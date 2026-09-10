"""
services/price_tracker.py — Ethical price comparison using OFFICIAL APIs only.

IMPORTANT: This service uses only legally compliant methods:
  - Amazon: Product Advertising API v5.0 (PA-API) — official affiliate API
  - Flipkart: Official Affiliate API
  - Myntra / Ajio / Meesho: Direct search links ONLY (no scraping).
    These platforms do not offer public price APIs; we link users there directly.

To activate live prices:
  1. Join Amazon Associates: https://affiliate-program.amazon.in/
  2. Request PA-API access from your Associates dashboard.
  3. Set AMAZON_ACCESS_KEY, AMAZON_SECRET_KEY, AMAZON_PARTNER_TAG in .env

  4. Join Flipkart Affiliate: https://affiliate.flipkart.com/
  5. Set FLIPKART_AFFILIATE_TOKEN in .env

No scraping. No ToS violations.
"""

import logging
import hashlib
import hmac
import json
import re
from datetime import datetime, timezone
from typing import Optional

import httpx
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings

log = logging.getLogger(__name__)


# ---------------------------------------------------------------------------
# Amazon PA-API v5
# Docs: https://webservices.amazon.com/paapi5/documentation/
# ---------------------------------------------------------------------------

async def search_amazon_paapi(query: str) -> dict:
    """
    Search Amazon India using the official Product Advertising API v5.0.
    Returns the cheapest result with a live price and affiliate URL.

    Requires: AMAZON_ACCESS_KEY, AMAZON_SECRET_KEY, AMAZON_PARTNER_TAG in .env
    """
    access_key = getattr(settings, "amazon_access_key", None)
    secret_key = getattr(settings, "amazon_secret_key", None)
    partner_tag = getattr(settings, "amazon_partner_tag", None)

    if not all([access_key, secret_key, partner_tag]):
        log.info("Amazon PA-API credentials not set — skipping live lookup.")
        return {
            "platform": "amazon",
            "price": None,
            "url": f"https://www.amazon.in/s?k={query.replace(' ', '+')}",
            "source": "link_only",
        }

    endpoint   = "https://webservices.amazon.in/paapi5/searchitems"
    host       = "webservices.amazon.in"
    region     = "eu-west-1"
    service    = "ProductAdvertisingAPI"

    payload = {
        "Keywords":      query,
        "Resources":     ["Offers.Summaries.LowestPrice", "ItemInfo.Title", "DetailPageURL"],
        "SearchIndex":   "Fashion",
        "PartnerTag":    partner_tag,
        "PartnerType":   "Associates",
        "Marketplace":   "www.amazon.in",
    }

    try:
        # AWS SigV4 signing
        amz_date    = datetime.now(timezone.utc).strftime("%Y%m%dT%H%M%SZ")
        date_stamp  = amz_date[:8]
        body        = json.dumps(payload)
        content_type = "application/json; charset=UTF-8"
        amz_target  = "com.amazon.paapi5.v1.ProductAdvertisingAPIv1.SearchItems"

        canonical_headers = (
            f"content-type:{content_type}\n"
            f"host:{host}\n"
            f"x-amz-date:{amz_date}\n"
            f"x-amz-target:{amz_target}\n"
        )
        signed_headers = "content-type;host;x-amz-date;x-amz-target"
        payload_hash   = hashlib.sha256(body.encode("utf-8")).hexdigest()
        canonical_req  = "\n".join([
            "POST", "/paapi5/searchitems", "",
            canonical_headers, signed_headers, payload_hash
        ])

        credential_scope = f"{date_stamp}/{region}/{service}/aws4_request"
        string_to_sign   = "\n".join([
            "AWS4-HMAC-SHA256", amz_date, credential_scope,
            hashlib.sha256(canonical_req.encode("utf-8")).hexdigest()
        ])

        def _sign(key, msg):
            return hmac.new(key, msg.encode("utf-8"), hashlib.sha256).digest()

        signing_key = _sign(
            _sign(_sign(_sign(
                f"AWS4{secret_key}".encode("utf-8"), date_stamp),
                region), service),
            "aws4_request")
        signature = hmac.new(signing_key, string_to_sign.encode("utf-8"), hashlib.sha256).hexdigest()

        auth_header = (
            f"AWS4-HMAC-SHA256 Credential={access_key}/{credential_scope}, "
            f"SignedHeaders={signed_headers}, Signature={signature}"
        )

        headers = {
            "Content-Type":  content_type,
            "X-Amz-Date":    amz_date,
            "X-Amz-Target":  amz_target,
            "Authorization": auth_header,
        }

        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.post(endpoint, content=body, headers=headers)
            if resp.status_code != 200:
                log.warning(f"Amazon PA-API error {resp.status_code}: {resp.text[:200]}")
                return {"platform": "amazon", "price": None, "url": None}

            data  = resp.json()
            items = data.get("SearchResult", {}).get("Items", [])
            if not items:
                return {"platform": "amazon", "price": None, "url": None}

            item  = items[0]
            offer = item.get("Offers", {}).get("Summaries", [{}])[0]
            price = offer.get("LowestPrice", {}).get("Amount")
            url   = item.get("DetailPageURL")

            return {
                "platform": "amazon",
                "price":    float(price) if price else None,
                "url":      url,
                "source":   "pa_api",
            }

    except Exception as e:
        log.warning(f"Amazon PA-API call failed: {e}")
        return {"platform": "amazon", "price": None, "url": None}


# ---------------------------------------------------------------------------
# Flipkart Affiliate API
# Docs: https://affiliate.flipkart.com/introduction
# ---------------------------------------------------------------------------

async def search_flipkart_api(query: str) -> dict:
    """
    Search Flipkart using the official Affiliate API.
    Requires: FLIPKART_AFFILIATE_TOKEN in .env
    """
    token = getattr(settings, "flipkart_affiliate_token", None)
    search_link = f"https://www.flipkart.com/search?q={query.replace(' ', '+')}"

    if not token:
        log.info("Flipkart affiliate token not set — returning search link only.")
        return {
            "platform": "flipkart",
            "price":    None,
            "url":      search_link,
            "source":   "link_only",
        }

    try:
        # Flipkart affiliate search endpoint
        url = f"https://affiliate-api.flipkart.io/affiliate/search?query={query.replace(' ', '%20')}&resultCount=1"
        headers = {
            "Fk-Affiliate-Id":    token,
            "Fk-Affiliate-Token": token,
        }
        async with httpx.AsyncClient(timeout=15) as client:
            resp = await client.get(url, headers=headers)
            if resp.status_code != 200:
                log.warning(f"Flipkart API error: {resp.status_code}")
                return {"platform": "flipkart", "price": None, "url": search_link}

            data     = resp.json()
            products = data.get("productInfoList", [])
            if not products:
                return {"platform": "flipkart", "price": None, "url": search_link}

            product  = products[0]
            price    = (product.get("productBaseInfo") or {}).get("productAttributes", {}).get("sellingPrice", {}).get("amount")
            prod_url = (product.get("productBaseInfo") or {}).get("productAttributes", {}).get("productUrl")

            return {
                "platform": "flipkart",
                "price":    float(price) / 100 if price else None,  # API returns paise
                "url":      prod_url or search_link,
                "source":   "affiliate_api",
            }

    except Exception as e:
        log.warning(f"Flipkart API call failed: {e}")
        return {"platform": "flipkart", "price": None, "url": search_link}


# ---------------------------------------------------------------------------
# Platforms without public APIs
# We provide direct search links — transparent to the user.
# ---------------------------------------------------------------------------

def _link_only(platform: str, query: str, region: str = "IN") -> dict:
    """Return a direct search link for platforms based on region."""
    q_plus = query.replace(" ", "+")
    q_dash = query.replace(" ", "-")
    q_enc  = query.replace(" ", "%20")

    # Global Store URLs
    urls = {
        # India
        "myntra": f"https://www.myntra.com/{q_dash}",
        "ajio":   f"https://www.ajio.com/search/?text={q_enc}",
        "meesho": f"https://www.meesho.com/search?q={q_plus}",
        "flipkart": f"https://www.flipkart.com/search?q={q_plus}",
        
        # USA
        "nordstrom": f"https://www.nordstrom.com/sr?keyword={q_plus}",
        "macys": f"https://www.macys.com/shop/featured/{q_plus}",
        "bloomingdales": f"https://www.bloomingdales.com/shop/search?keyword={q_plus}",
        "zara": f"https://www.zara.com/us/en/search?searchTerm={q_enc}",
        
        # UK / EU
        "asos": f"https://www.asos.com/search/?q={q_plus}",
        "zalando": f"https://www.zalando.co.uk/catalog/?q={q_plus}",
        "farfetch": f"https://www.farfetch.com/shopping/women/search/items.aspx?q={q_plus}",
        "next": f"https://www.next.co.uk/search?w={q_plus}",
        
        # Global
        "shein": f"https://www.shein.com/explore?search_keyword={q_plus}",
        "h&m": f"https://www2.hm.com/en_in/search-results.html?q={q_plus}",
        "ssense": f"https://www.ssense.com/en-ca/men?q={q_enc}",
        "lululemon": f"https://shop.lululemon.com/search?Ntt={q_plus}",
        "davidjones": f"https://www.davidjones.com/search?q={q_plus}",
        "theiconic": f"https://www.theiconic.com.au/catalog/?q={q_plus}"
    }
    
    # Platform Overrides based on region for generic names
    if region == "US":
        if platform == "amazon": return {"platform": "Amazon US", "price": None, "url": f"https://www.amazon.com/s?k={q_plus}", "source": "link_only"}
    elif region == "UK":
        if platform == "amazon": return {"platform": "Amazon UK", "price": None, "url": f"https://www.amazon.co.uk/s?k={q_plus}", "source": "link_only"}
    elif region == "CA":
        if platform == "amazon": return {"platform": "Amazon CA", "price": None, "url": f"https://www.amazon.ca/s?k={q_plus}", "source": "link_only"}
    
    return {
        "platform": platform.capitalize(),
        "price":    None,
        "url":      urls.get(platform, "#"),
        "source":   "link_only",
    }


# ---------------------------------------------------------------------------
# Main entry point
# ---------------------------------------------------------------------------

async def search_cuelinks_db(query: str) -> list:
    """
    Check the local database for products ingested via Cuelinks feeds.
    Returns results for Myntra, Ajio, Meesho if available in DB.
    """
    from database.models import Product
    from database.postgres import AsyncSessionLocal
    from sqlalchemy import select, or_

    results = []
    platforms = ["myntra", "ajio", "meesho"]
    
    async with AsyncSessionLocal() as db:
        for platform in platforms:
            # Simple keyword match in DB for that platform
            stmt = select(Product).where(
                Product.store == platform,
                Product.name.ilike(f"%{query}%")
            ).limit(1)
            res = await db.execute(stmt)
            product = res.scalar_one_or_none()
            
            if product:
                results.append({
                    "platform": platform,
                    "price":    product.current_price,
                    "url":      product.affiliate_url,
                    "source":   "cuelinks_feed"
                })
            else:
                # Fallback to link only if not in DB
                results.append(_link_only(platform, query))
    return results

async def compare_prices(query: str, region: str = "IN") -> dict:
    """
    Return price comparison using official APIs only.
    Regional stores are prioritized based on user location.
    """
    import asyncio

    results = []
    
    if region == "IN":
        # India Stack
        amazon_result, flipkart_result, cuelinks_results = await asyncio.gather(
            search_amazon_paapi(query),
            search_flipkart_api(query),
            search_cuelinks_db(query)
        )
        results = [amazon_result, flipkart_result] + cuelinks_results
    elif region == "US":
        # USA Stack
        results = [
            {"platform": "Amazon US", "price": None, "url": f"https://www.amazon.com/s?k={query.replace(' ', '+')}", "source": "link_only"},
            _link_only("nordstrom", query, "US"),
            _link_only("asos", query, "US"),
            _link_only("zara", query, "US"),
            _link_only("macys", query, "US"),
            _link_only("bloomingdales", query, "US"),
        ]
    elif region == "CA":
        # Canada Stack
        results = [
            {"platform": "Amazon CA", "price": None, "url": f"https://www.amazon.ca/s?k={query.replace(' ', '+')}", "source": "link_only"},
            _link_only("ssense", query, "CA"),
            _link_only("h&m", query, "CA"),
            _link_only("zara", query, "CA"),
            _link_only("farfetch", query, "CA"),
        ]
    elif region == "AU":
        # Australia Stack
        results = [
            {"platform": "Amazon AU", "price": None, "url": f"https://www.amazon.com.au/s?k={query.replace(' ', '+')}", "source": "link_only"},
            {"platform": "THE ICONIC", "price": None, "url": f"https://www.theiconic.com.au/catalog/?q={query.replace(' ', '+')}", "source": "link_only"},
            _link_only("davidjones", query, "AU"),
            _link_only("zara", query, "AU"),
            _link_only("h&m", query, "AU"),
        ]
    elif region in ["GB", "UK"]:
        # UK Stack
        results = [
            {"platform": "Amazon UK", "price": None, "url": f"https://www.amazon.co.uk/s?k={query.replace(' ', '+')}", "source": "link_only"},
            _link_only("asos", query, "GB"),
            _link_only("next", query, "GB"),
            _link_only("zalando", query, "GB"),
            _link_only("zara", query, "GB"),
        ]
    elif region in ["DE", "FR", "IT", "ES", "EU"]:
        # Europe Stack
        results = [
            {"platform": "Amazon EU", "price": None, "url": f"https://www.amazon.de/s?k={query.replace(' ', '+')}", "source": "link_only"},
            _link_only("zalando", query, region),
            _link_only("zara", query, region),
            _link_only("h&m", query, region),
            _link_only("farfetch", query, region),
        ]
    else:
        # Global Default
        results = [
            {"platform": "Amazon Global", "price": None, "url": f"https://www.amazon.com/s?k={query.replace(' ', '+')}", "source": "link_only"},
            _link_only("asos", query, "GLOBAL"),
            _link_only("shein", query, "GLOBAL"),
            _link_only("zara", query, "GLOBAL"),
            _link_only("farfetch", query, "GLOBAL"),
        ]

    # Best price only from platforms with live API data
    priced = [(r["price"], r["platform"]) for r in results if r.get("price")]
    priced.sort(key=lambda x: x[0])
    best = priced[0] if priced else (None, None)

    return {
        "query":         query,
        "region":        region,
        "results":       results,
        "best_price":    best[0],
        "best_platform": best[1],
    }


# ---------------------------------------------------------------------------
# Price alert checker (used by background task)
# ---------------------------------------------------------------------------

async def check_and_notify_alerts(db: AsyncSession):
    from database.models import PriceAlert, Product, User
    from sqlalchemy import select

    stmt   = select(PriceAlert).where(PriceAlert.is_active == True)
    res    = await db.execute(stmt)
    alerts = res.scalars().all()

    for alert in alerts:
        prod_res = await db.execute(select(Product).where(Product.id == alert.product_id))
        product  = prod_res.scalar_one_or_none()
        if not product:
            continue
        comparison   = await compare_prices(product.name)
        current_best = comparison["best_price"]
        if current_best and current_best <= alert.target_price:
            user_res = await db.execute(select(User).where(User.id == alert.user_id))
            user     = user_res.scalar_one_or_none()
            
            log.info(
                f"🔔 ALERT: {product.name} is now ₹{current_best}! "
                f"(Target: ₹{alert.target_price}) for user {user.id if user else 'unknown'}"
            )
            
            if user and user.pref_price_drop:
                from services.notification_service import notify_user
                await notify_user(
                    user_id=user.id,
                    title="Price Drop Alert! 💰",
                    body=f"Good news! {product.name} has dropped to {current_best}. Your target was {alert.target_price}.",
                    data={
                        "type": "price_drop",
                        "product_id": str(product.id),
                        "price": str(current_best)
                    },
                    image_url=product.image_url,
                    db=db
                )
                # Mark alert as inactive so it doesn't spam
                alert.is_active = False
                await db.commit()
