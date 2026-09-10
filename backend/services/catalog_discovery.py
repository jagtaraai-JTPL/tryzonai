import logging
import asyncio
import random
from typing import List, Dict, Any
import httpx
from bs4 import BeautifulSoup
from urllib.parse import quote_plus

from services.admin_scraper import fetch_product_metadata
from database.models import Product
from database.postgres import AsyncSessionLocal
from sqlalchemy import select

log = logging.getLogger(__name__)

USER_AGENTS = [
    "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36",
    "Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/121.0.0.0 Safari/537.36",
]

DEFAULT_KEYWORDS = [
    "Mens Streetwear", "Luxury Evening Dresses", "Denim Jackets", "Designer Handbags",
    "Sneakers Lifestyle", "Formal Business Suits", "Summer Floral Dresses",
    "Casual T-Shirts Men", "Winter Overcoats", "Cocktail Dresses"
]

class CatalogDiscoveryService:
    def __init__(self):
        self.client = httpx.AsyncClient(timeout=20, follow_redirects=True)

    async def find_product_urls(self, keyword: str, store: str = "amazon") -> List[str]:
        """Finds product URLs from a store's search results page."""
        log.info(f"Searching {store} for keyword: {keyword}")
        headers = {"User-Agent": random.choice(USER_AGENTS)}
        
        url = ""
        if store == "amazon":
            url = f"https://www.amazon.com/s?k={quote_plus(keyword)}"
        elif store == "asos":
            url = f"https://www.asos.com/search/?q={quote_plus(keyword)}"
        elif store == "zara":
            url = f"https://www.zara.com/us/en/search?searchTerm={quote_plus(keyword)}"

        try:
            resp = await self.client.get(url, headers=headers)
            if resp.status_code != 200:
                log.warning(f"Search failed for {store} (Status {resp.status_code})")
                return []

            soup = BeautifulSoup(resp.text, 'html.parser')
            links = []

            if store == "amazon":
                # Find product links (excluding ads)
                for a in soup.select("a.a-link-normal.s-underline-text.s-underline-link-text.s-link-style.a-text-normal"):
                    link = a.get("href")
                    if link and "/dp/" in link:
                        if not link.startswith("http"):
                            link = "https://www.amazon.com" + link
                        links.append(link.split("?")[0]) # Strip query params

            elif store == "asos":
                for a in soup.select("a[href*='/prd/']"):
                    link = a.get("href")
                    if link:
                        if not link.startswith("http"):
                            link = "https://www.asos.com" + link
                        links.append(link.split("?")[0])

            return list(set(links))[:10] # Return top 10 unique links
        except Exception as e:
            log.error(f"Error during {store} discovery: {e}")
            return []

    async def discover_and_ingest(self, keywords: List[str] = None):
        """Main entry point to find and add products."""
        keywords = keywords or DEFAULT_KEYWORDS
        stores = ["amazon", "asos"]
        
        new_count = 0
        for keyword in keywords:
            for store in stores:
                urls = await self.find_product_urls(keyword, store)
                for url in urls:
                    try:
                        # 1. Check if already exists
                        async with AsyncSessionLocal() as db:
                            stmt = select(Product).where(Product.original_url == url)
                            res = await db.execute(stmt)
                            if res.scalar_one_or_none():
                                continue

                        # 2. Scrape metadata
                        metadata = await fetch_product_metadata(url)
                        if "error" in metadata or not metadata.get("image_url") or not metadata.get("name"):
                            continue

                        # 3. Save to DB
                        async with AsyncSessionLocal() as db:
                            # Determine region based on store
                            regions = ["IN"]
                            if store == "amazon" and "amazon.com" in url:
                                regions = ["US", "GLOBAL"]
                            elif store == "amazon" and "amazon.in" in url:
                                regions = ["IN"]
                            
                            product = Product(
                                name=metadata["name"],
                                category=metadata["category"],
                                image_url=metadata["image_url"],
                                brand=metadata["brand"] or store.capitalize(),
                                store=metadata["store"] or store,
                                original_url=url,
                                current_price=metadata["price"] or 0,
                                supported_regions=str(regions),
                                added_by="auto_discovery"
                            )
                            db.add(product)
                            await db.commit()
                        
                        new_count += 1
                        log.info(f"Ingested new product: {metadata['name']} from {store}")
                        await asyncio.sleep(2) # Prevent rate limiting
                    except Exception as e:
                        log.error(f"Failed to ingest {url}: {e}")

        log.info(f"Discovery run complete. Added {new_count} new products.")
        return new_count

async def run_discovery_service():
    service = CatalogDiscoveryService()
    await service.discover_and_ingest()

if __name__ == "__main__":
    asyncio.run(run_discovery_service())
