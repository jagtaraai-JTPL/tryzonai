import logging
import re
import json
import random
import asyncio
from typing import Optional, Dict, Any, List
import httpx
from bs4 import BeautifulSoup
from urllib.parse import urlparse

log = logging.getLogger(__name__)

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
    "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (iPhone; CPU iPhone OS 17_2_1 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
    "Mozilla/5.0 (iPad; CPU OS 17_2 like Mac OS X) AppleWebKit/605.1.15 (KHTML, like Gecko) Version/17.2 Mobile/15E148 Safari/604.1",
]

async def fetch_product_metadata(url: str, retries: int = 2) -> Dict[str, Any]:
    """
    Scrapes product metadata (Title, Price, Image, Brand) from a given URL.
    Best-effort extraction using OpenGraph, Schema.org, and meta tags.
    Now with enhanced bot-detection bypass logic.
    """
    domain = urlparse(url).netloc.lower().replace("www.", "")
    base_headers = {
        "Accept": "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "Accept-Language": "en-US,en;q=0.9",
        "Accept-Encoding": "gzip, deflate, br",
        "Connection": "keep-alive",
        "Upgrade-Insecure-Requests": "1",
        "Sec-Fetch-Dest": "document",
        "Sec-Fetch-Mode": "navigate",
        "Sec-Fetch-Site": "none",
        "Sec-Fetch-User": "?1",
        "Cache-Control": "max-age=0",
        "Referer": f"https://{domain}/",
    }

    last_error = None
    for attempt in range(retries + 1):
        headers = base_headers.copy()
        headers["User-Agent"] = random.choice(USER_AGENTS)
        
        try:
            # We use http2 only if the store is known to support it, but for simplicity we try it
            async with httpx.AsyncClient(timeout=15, follow_redirects=True, http2=True) as client:
                resp = await client.get(url, headers=headers)
                
                # If we get blocked (403/503), try again with delay
                if resp.status_code in [403, 503, 429]:
                    log.warning(f"Blocked by {domain} (Status {resp.status_code}) on attempt {attempt+1}")
                    last_error = f"Store blocked access (Status {resp.status_code})"
                    await asyncio.sleep(1.5 * (attempt + 1))
                    continue
                
                if resp.status_code != 200:
                    log.error(f"Failed to fetch {url}: {resp.status_code}")
                    return {"error": f"Failed to fetch page: {resp.status_code}"}
                
                html = resp.text
                soup = BeautifulSoup(html, 'html.parser')
                
                # Detect Store
                store = domain.split('.')[0].capitalize()

                # 1. Extract Title
                title = None
                og_title = soup.find("meta", property="og:title")
                if og_title: title = og_title.get("content")
                if not title:
                    title_tag = soup.find("title")
                    if title_tag: title = title_tag.get_text().strip()

                # 2. Extract Image
                image_url = None
                
                # Priority 1: OpenGraph Image
                og_image = soup.find("meta", property="og:image")
                if og_image: image_url = og_image.get("content")
                
                # Priority 2: Twitter Image
                if not image_url:
                    twitter_image = soup.find("meta", attrs={"name": "twitter:image"})
                    if twitter_image: image_url = twitter_image.get("content")
                    
                # Priority 3: Specific Store Extractors
                if not image_url:
                    if "myntra" in domain:
                        img_tag = soup.find("img", class_="pdp-main-img")
                        if img_tag: image_url = img_tag.get("src")
                    elif "amazon" in domain:
                        img_tag = soup.find("img", id="landingImage")
                        if img_tag: image_url = img_tag.get("src") or img_tag.get("data-old-hires")
                    elif "flipkart" in domain:
                        # Flipkart often uses specific class names
                        img_tag = soup.select_one("img._396cs4, img._2amPTt, img.q6DClP")
                        if img_tag: image_url = img_tag.get("src")

                # Priority 4: First large image (Hacky fallback)
                if not image_url:
                    for img in soup.find_all("img"):
                        w = img.get("width")
                        h = img.get("height")
                        if w and h and w.isdigit() and h.isdigit() and int(w) > 300:
                            image_url = img.get("src")
                            break

                # ── Clean & Absolute Image URL ───────────────────────
                if image_url:
                    if image_url.startswith("//"):
                        image_url = "https:" + image_url
                    elif image_url.startswith("/"):
                        base_url = f"{urlparse(url).scheme}://{urlparse(url).netloc}"
                        image_url = base_url + image_url

                # 3. Extract Price
                price = None
                og_price = soup.find("meta", property="product:price:amount")
                if og_price: price = og_price.get("content")
                
                if not price:
                    scripts = soup.find_all("script", type="application/ld+json")
                    for script in scripts:
                        try:
                            data = json.loads(script.string)
                            items = data if isinstance(data, list) else [data]
                            for item in items:
                                if item.get("@type") == "Product":
                                    offers = item.get("offers")
                                    if isinstance(offers, dict):
                                        price = offers.get("price")
                                    elif isinstance(offers, list) and offers:
                                        price = offers[0].get("price")
                                    if price: break
                            if price: break
                        except: continue

                # Fallback Price Extraction
                if not price:
                    for selector in [".pdp-price", "#priceblock_ourprice", "._30jeq3", ".a-price-whole"]:
                        tag = soup.select_one(selector)
                        if tag:
                            price_text = tag.get_text()
                            match = re.search(r'[\d,.]+', price_text)
                            if match:
                                price = match.group(0).replace(',', '')
                                break

                # 4. Extract Brand
                brand = None
                og_brand = soup.find("meta", property="product:brand")
                if og_brand: brand = og_brand.get("content")
                if not brand:
                    og_site = soup.find("meta", property="og:site_name")
                    if og_site: brand = og_site.get("content")

                # 5. Extract Category
                category = "Clothing"
                lower_title = title.lower() if title else ""
                lower_url = url.lower()
                
                # Broadened filters for non-clothing items
                footwear_keys = ["shoes", "footwear", "sneakers", "sandals", "heels", "boots", "slippers", "clogs"]
                beauty_keys = ["beauty", "makeup", "skin", "cosmetics", "cream", "fragrance", "perfume", "serum"]
                accessory_keys = ["watch", "jewelry", "earrings", "necklace", "handbag", "wallet", "belt", "sunglasses"]

                if any(x in lower_url or x in lower_title for x in footwear_keys):
                    category = "Footwear"
                elif any(x in lower_url or x in lower_title for x in beauty_keys):
                    category = "Beauty"
                elif any(x in lower_url or x in lower_title for x in accessory_keys):
                    category = "Accessory"
                
                return {
                    "name": title,
                    "price": float(price) if price and str(price).replace('.', '', 1).isdigit() else None,
                    "image_url": image_url,
                    "brand": brand,
                    "store": store,
                    "category": category,
                    "url": url
                }

        except httpx.HTTPError as e:
            log.error(f"HTTP error on attempt {attempt+1} for {url}: {e}")
            last_error = str(e)
            await asyncio.sleep(1)

    return {"error": last_error or "Failed to auto-fetch details. The store might be blocking access."}
