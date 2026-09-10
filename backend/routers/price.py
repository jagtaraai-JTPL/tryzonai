"""
routers/price.py — Price Comparison endpoint
GET /api/v1/price-compare?q=<query>
"""

import logging

from fastapi import APIRouter, Depends, Query, Request
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database.models import Product, User
from database.postgres import get_db
from routers.auth import get_current_user, get_optional_user
from services.price_tracker import compare_prices
from typing import Optional

log = logging.getLogger(__name__)
router = APIRouter()


@router.get("/price-compare")
async def price_compare(
    request: Request,
    q: str | None = Query(None, description="Product name or search query"),
    product_id: int | None = Query(None, description="Product ID from catalog"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Compare prices across global platforms (Amazon, Myntra, Nordstrom, ASOS, etc.)
    tailored to the user's detected region.
    """
    from utils.geo_utils import get_country_from_request
    region = await get_country_from_request(request)

    # ── Resolve Product ───────────────────────────────────
    cached = None
    if product_id:
        result = await db.execute(select(Product).where(Product.id == product_id))
        cached = result.scalar_one_or_none()
        if cached:
            q = cached.name
    elif q:
        result = await db.execute(
            select(Product).where(Product.name.ilike(f"%{q}%")).limit(1)
        )
        cached = result.scalar_one_or_none()
    else:
        return {"detail": "Query q or product_id required", "results": []}

    # Only use cache if it's the SAME region (simple logic: India vs others)
    # If the user is global but the cache only has IN prices, we re-scrape
    if cached and region == "IN" and (cached.amazon_price or cached.myntra_price or cached.flipkart_price):
        log.debug(f"Price cache hit (IN) for: {q}")
        return {
            "query": q,
            "region": region,
            "source": "cache",
            "results": [
                {"platform": "Amazon", "price": cached.amazon_price, "url": cached.amazon_url},
                {"platform": "Myntra", "price": cached.myntra_price, "url": cached.myntra_url},
                {"platform": "Flipkart", "price": cached.flipkart_price, "url": cached.flipkart_url},
            ],
            "best_price": min(
                p for p in [cached.amazon_price, cached.myntra_price, cached.flipkart_price]
                if p is not None
            ) if any([cached.amazon_price, cached.myntra_price, cached.flipkart_price]) else None,
            "last_updated": cached.last_price_update.isoformat() if cached.last_price_update else None,
        }

    # ── Live scrape / Link Generation ────────────────────
    log.info(f"Live regional price comparison [{region}] for: {q}")
    data = await compare_prices(q, region=region)

    # Cache only for India for now (as global stores are link-only)
    if region == "IN":
        prices = {r["platform"].lower(): r for r in data["results"]}
        if not cached:
            product = Product(
                name=q,
                category="unknown",
                amazon_price=prices.get("amazon", {}).get("price"),
                myntra_price=prices.get("myntra", {}).get("price"),
                flipkart_price=prices.get("flipkart", {}).get("price"),
                amazon_url=prices.get("amazon", {}).get("url"),
                myntra_url=prices.get("myntra", {}).get("url"),
                flipkart_url=prices.get("flipkart", {}).get("url"),
            )
            from datetime import datetime, timezone
            product.last_price_update = datetime.now(timezone.utc)
            db.add(product)
        else:
            cached.amazon_price = prices.get("amazon", {}).get("price")
            cached.myntra_price = prices.get("myntra", {}).get("price")
            cached.flipkart_price = prices.get("flipkart", {}).get("price")
            from datetime import datetime, timezone
            cached.last_price_update = datetime.now(timezone.utc)
        
        await db.commit()

    return {**data, "source": "live"}
