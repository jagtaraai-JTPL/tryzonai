"""
routers/feed.py — Personalised fashion feed
GET /api/v1/feed
POST /api/v1/feed/interaction  (record like/view for interest tracking)
"""

import logging

from fastapi import APIRouter, Depends, Query, Header, Request
from pydantic import BaseModel
from sqlalchemy.ext.asyncio import AsyncSession

from sqlalchemy import select, or_, desc, func
from database.models import User, Product
from database.postgres import get_db
from routers.auth import get_current_user, get_optional_user
from services.chroma_service import get_personalised_feed, upsert_user_interest, get_product_embedding, search_products
from utils.geo_utils import get_country_from_request
from services.affiliate_engine import generate_affiliate_url

log = logging.getLogger(__name__)
router = APIRouter()


class InteractionRequest(BaseModel):
    product_id: str
    interaction_type: str  # "like" | "view" | "try_on" | "purchase"
    embedding: list[float] | None = None
    tags: list[str] | None = None


@router.get("/feed")
async def get_feed(
    current_user: User | None = Depends(get_optional_user),
    limit: int = Query(20, ge=1, le=50),
    db: AsyncSession = Depends(get_db),
):
    # Cold start or Guest: return featured products
    if not current_user:
        stmt = select(Product).where(Product.is_featured == True, Product.is_active == True).order_by(func.random()).limit(limit)
        res = await db.execute(stmt)
        featured = res.scalars().all()
        return {
            "user_id": None,
            "count": len(featured),
            "items": [
                {
                    "id": str(p.id),
                    "name": p.name,
                    "brand": p.brand,
                    "price": int(p.current_price or 0),
                    "image": p.image_url,
                    "category": p.category,
                    "badge": "TRENDING"
                } for p in featured
            ],
            "is_personalised": False
        }

    items = get_personalised_feed(user_id=current_user.id, top_k=limit)
    
    if not items:
        # User Onboarding Preference-based smart fallback ranking engine
        stmt = select(Product).where(Product.is_active == True)
        res = await db.execute(stmt)
        all_products = res.scalars().all()
        
        pref_gender = (current_user.pref_gender or "Women").lower()
        pref_vibe = (current_user.pref_style_vibe or "").lower()
        pref_goal = (current_user.pref_fashion_goal or "").lower()

        scored_products = []
        for p in all_products:
            score = 50.0  # Base score
            name_lower = (p.name or "").lower()
            cat_lower = (p.category or "").lower()

            # Gender Match Scoring
            if pref_gender == "women" and any(k in name_lower or k in cat_lower for k in ["women", "dress", "gown", "abaya", "jumpsuit", "sundress", "silk"]):
                score += 30
            elif pref_gender == "men" and any(k in name_lower or k in cat_lower for k in ["men", "suit", "blazer", "tuxedo", "kurta", "linen"]):
                score += 30

            # Style Vibe Match Scoring
            if not pref_vibe or "all" in pref_vibe or "mix" in pref_vibe:
                score += 25  # All Styles & Mix gets universal boost
            elif "old money" in pref_vibe or "luxury" in pref_vibe:
                if any(k in name_lower or k in cat_lower for k in ["luxury", "gowns", "couture", "elite", "monaco", "linen", "quiet"]):
                    score += 25
            elif "streetwear" in pref_vibe or "oversized" in pref_vibe:
                if any(k in name_lower or k in cat_lower for k in ["streetwear", "cyber", "techwear", "oversized", "baggy", "jacket"]):
                    score += 25
            elif "party" in pref_vibe or "glam" in pref_vibe or "date" in pref_vibe:
                if any(k in name_lower or k in cat_lower for k in ["formal", "party", "gown", "dress", "glam", "cocktail"]):
                    score += 25
            elif "ethnic" in pref_vibe or "festive" in pref_vibe or "royal" in pref_vibe:
                if any(k in name_lower or k in cat_lower for k in ["ethnic", "kurta", "traditional", "festival", "lehenga", "wedding"]):
                    score += 25
            elif "minimal" in pref_vibe or "airport" in pref_vibe:
                if any(k in name_lower or k in cat_lower for k in ["casual", "minimal", "airport", "chic", "blazer", "basic"]):
                    score += 25

            # Fashion Goal Match Scoring
            if "online" in pref_goal and p.is_featured:
                score += 15
            elif "social" in pref_goal and any(k in name_lower for k in ["viral", "cyber", "royal", "gala"]):
                score += 15
            elif "event" in pref_goal and any(k in cat_lower for k in ["formal", "luxury"]):
                score += 15

            match_badge = "✨ RECOMMENDED" if score >= 90 else ("🎯 VIBE MATCH" if score >= 75 else "FOR YOU")
            scored_products.append((score, p, match_badge))

        # Sort by highest recommendation score
        scored_products.sort(key=lambda x: x[0], reverse=True)
        top_recs = scored_products[:limit]

        return {
            "user_id": current_user.id,
            "count": len(top_recs),
            "items": [
                {
                    "id": p.id,
                    "name": p.name,
                    "brand": p.brand,
                    "price": p.current_price,
                    "image": p.image_url,
                    "category": p.category,
                    "badge": badge
                } for score, p, badge in top_recs
            ],
            "is_personalised": True,
            "user_preferences": {
                "gender": current_user.pref_gender,
                "fashion_goal": current_user.pref_fashion_goal,
                "style_vibe": current_user.pref_style_vibe
            }
        }

    # Enrich Chroma items with SQL data
    p_ids = [int(it["product_id"]) for it in items if it["product_id"].isdigit()]
    stmt = select(Product).where(Product.id.in_(p_ids))
    res = await db.execute(stmt)
    products_map = {p.id: p for p in res.scalars().all()}

    enriched = []
    for it in items:
        p_id = int(it["product_id"]) if it["product_id"].isdigit() else None
        if p_id and p_id in products_map:
            p = products_map[p_id]
            enriched.append({
                "id": p.id,
                "name": p.name,
                "brand": p.brand,
                "price": p.current_price,
                "image": p.image_url,
                "category": p.category,
                "badge": f"{int(it['score']*100)}% Match" if "score" in it else "90% Match"
            })

    return {
        "user_id": current_user.id,
        "count": len(enriched),
        "items": enriched,
        "is_personalised": True,
    }


@router.post("/feed/interaction")
async def record_interaction(
    req: InteractionRequest,
    current_user: User | None = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Record a user interaction (like/view/try_on) to update interest vector.
    If embedding is provided (from CLIP), updates ChromaDB interest profile.
    """
    if not current_user:
        return {
            "status": "ignored_guest",
            "product_id": req.product_id,
            "interaction": req.interaction_type
        }

    if req.embedding and req.tags:
        upsert_user_interest(
            user_id=current_user.id,
            interest_embedding=req.embedding,
            tags=req.tags,
        )
        log.info(f"Interest updated for user {current_user.id}: {req.tags}")

    # Weight by interaction type
    weight_map = {"view": 1, "like": 3, "try_on": 5, "purchase": 10}
    weight = weight_map.get(req.interaction_type, 1)

    return {
        "status": "recorded",
        "product_id": req.product_id,
        "interaction": req.interaction_type,
        "interest_weight": weight,
    }


@router.get("/catalog")
async def get_catalog(
    request: Request,
    category: str | None = Query(None),
    brand: str | None = Query(None),
    color: str | None = Query(None),
    occasion: str | None = Query(None),
    gender: str | None = Query(None),
    search: str | None = Query(None),
    limit: int = Query(50, ge=1, le=100),
    offset: int = Query(0, ge=0),
    region: str | None = Query(None),
    x_tryzon_region: str | None = Header(None),
    db: AsyncSession = Depends(get_db),
):
    """
    Public catalog listing from PostgreSQL.
    Supports filtering by category, search by name/brand, and region-based discovery.
    """
    # Use header if query param missing, then fallback to auto-detection
    active_region = region or x_tryzon_region
    if not active_region:
        active_region = await get_country_from_request(request)
    
    stmt = select(Product).where(
        Product.is_active == True,
        Product.image_url != None,
        Product.image_url != ""
    )

    # Regional Filter
    if active_region and active_region != "IN": # Assuming IN gets everything or we filter for IN
        # Match if active_region is in supported_regions (JSON-ish string) OR supported_regions is NULL/GLOBAL
        region_filter = or_(
            Product.supported_regions.ilike(f"%{active_region}%"),
            Product.supported_regions.ilike("%GLOBAL%"),
            Product.supported_regions.is_(None)
        )
        stmt = stmt.where(region_filter)
    
    # Enforce Clothing only policy for discovery if no category specified
    if category and category != "All":
        stmt = stmt.where(Product.category == category)
    
    if brand:
        stmt = stmt.where(Product.brand.ilike(f"%{brand}%"))
    
    if color:
        stmt = stmt.where(Product.clip_tags.ilike(f"%{color}%"))
    
    if occasion:
        stmt = stmt.where(Product.clip_tags.ilike(f"%{occasion}%"))
    if gender and gender != "All":
        # Database gender column is currently unpopulated, fallback to keyword matching with word boundaries
        # This prevents 'Men' from matching 'Women'
        regex_pattern = rf"\b{gender}\b"
        gender_filter = or_(
            Product.gender == gender,
            Product.category.op("~*")(regex_pattern),
            Product.name.op("~*")(regex_pattern),
            Product.clip_tags.op("~*")(regex_pattern)
        )
        stmt = stmt.where(gender_filter)
    
    if search:
        search_filter = or_(
            Product.name.ilike(f"%{search}%"),
            Product.brand.ilike(f"%{search}%"),
            Product.category.ilike(f"%{search}%"),
            Product.clip_tags.ilike(f"%{search}%")
        )
        stmt = stmt.where(search_filter)
    
    # Sorting Logic (Regional Sensitivity)
    # If region is Global/US, prioritize Western themes and add randomness for variety
    sort_featured = desc(Product.is_featured)
    
    if active_region and active_region != "IN":
        # Bias towards Streetwear/Formal for global users
        category_bias = desc(or_(Product.category == "Streetwear", Product.category == "Formal"))
        # Prioritize newest, then random sampling to keep it fresh
        stmt = stmt.order_by(sort_featured, desc(Product.created_at), category_bias, func.random())
    else:
        # Default behavior (India): Featured first, then Newest, then bias towards Ethnic/Casual
        category_bias = desc(or_(Product.category == "Ethnic", Product.category == "Casual"))
        stmt = stmt.order_by(sort_featured, desc(Product.created_at), category_bias)
    
    # Count total (before limit/offset and without order_by)
    count_stmt = select(func.count()).select_from(stmt.order_by(None).subquery())
    total_res = await db.execute(count_stmt)
    total_count = total_res.scalar() or 0

    # Apply Pagination
    stmt = stmt.limit(limit).offset(offset)
    
    result = await db.execute(stmt)
    products = result.scalars().all()
    
    return {
        "total": total_count,
        "limit": limit,
        "offset": offset,
        "products": [
            {
                "id": str(p.id),
                "name": p.name,
                "brand": p.brand,
                "category": p.category,
                "store": p.store,
                "price": int(p.current_price or 0),
                "originalPrice": int((p.current_price / (1 - (p.discount_percent or 0) / 100)) if p.current_price and (p.discount_percent or 0) < 100 else (p.current_price or 0)),
                "image": p.image_url,
                "url": generate_affiliate_url(p.affiliate_url or p.original_url, p.store, active_region),
                "is_featured": p.is_featured,
                "badge": "NEW" if (p.is_featured) else None
            }
            for p in products
        ]
    }


@router.get("/catalog/categories", tags=["Catalog"])
async def get_unique_categories(db: AsyncSession = Depends(get_db)):
    """
    Returns a unique list of all categories currently present in the active catalog.
    """
    stmt = select(Product.category).where(Product.is_active == True).distinct().order_by(Product.category)
    result = await db.execute(stmt)
    categories = [c for c in result.scalars().all() if c]
    
    # Ensure 'All' is NOT in the DB list but handled by frontend
    # Sort and prioritize certain categories if needed
    prio = ["Raw Denim", "Emerald", "Parisian", "Formal", "Summer Outfits"]
    sorted_cats = [c for c in prio if c in categories] + sorted([c for c in categories if c not in prio])
    
    return {"categories": ["All"] + sorted_cats}


@router.get("/recommendations/similar")
async def get_similar_products(
    product_id: int | None = Query(None),
    q: str | None = Query(None),
    gender: str | None = Query(None),
    limit: int = Query(6, ge=1, le=20),
    db: AsyncSession = Depends(get_db),
):
    """
    Return visually similar products with multiple fallback layers.
    """
    log.info(f"Similar recommendations request: product_id={product_id}, q={q}")
    items = []
    
    # Layer 1: Vector Search
    if product_id:
        embedding = get_product_embedding(product_id)
        if embedding:
            meta_filter = {"gender": gender} if gender and gender != "Unisex" else None
            items = search_products(embedding, top_k=limit + 1, metadata_filter=meta_filter)
            # Filter out the same product
            items = [it for it in items if it["product_id"] != str(product_id)][:limit]
            log.info(f"Vector search returned {len(items)} items for product_id {product_id}")

    # Layer 2: Text Search (if vector failed or no product_id)
    if not items and q:
        stmt = select(Product).where(
            Product.is_active == True,
            Product.image_url != None,
            Product.image_url != ""
        )
        stmt = stmt.where(Product.name.ilike(f"%{q}%"))
        if gender and gender != "Unisex":
            stmt = stmt.where(Product.gender == gender)
        stmt = stmt.limit(limit)
        result = await db.execute(stmt)
        products = result.scalars().all()
        if products:
            log.info(f"Text search returned {len(products)} items for q='{q}'")
            return {
                "source": "text_search",
                "products": [
                   {
                       "id": p.id,
                       "name": p.name,
                       "brand": p.brand,
                       "price": int(p.current_price or 0),
                       "originalPrice": int((p.current_price / (1 - (p.discount_percent or 0) / 100)) if p.current_price and (p.discount_percent or 0) < 100 else (p.current_price or 0)),
                       "image": p.image_url,
                       "url": generate_affiliate_url(p.affiliate_url or p.original_url, p.store, None),
                       "category": p.category
                   } for p in products
                ]
            }

    # Layer 3: Process Vector Results (if found)
    if items:
        p_ids = [int(it["product_id"]) for it in items]
        stmt = select(Product).where(Product.id.in_(p_ids))
        result = await db.execute(stmt)
        products_map = {p.id: p for p in result.scalars().all()}
        
        if products_map:
            return {
                "source": "vector_search",
                "products": [
                    {
                        "id": p_id,
                        "name": products_map[p_id].name,
                        "brand": products_map[p_id].brand,
                        "price": int(products_map[p_id].current_price or 0),
                        "originalPrice": int((products_map[p_id].current_price / (1 - (products_map[p_id].discount_percent or 0) / 100)) if products_map[p_id].current_price and (products_map[p_id].discount_percent or 0) < 100 else (products_map[p_id].current_price or 0)),
                        "image": products_map[p_id].image_url,
                        "url": generate_affiliate_url(products_map[p_id].affiliate_url or products_map[p_id].original_url, products_map[p_id].store, None),
                        "category": products_map[p_id].category
                    }
                    for p_id in p_ids if p_id in products_map
                ]
            }

    # Layer 4: Final Global Fallback (Featured or Random)
    log.warning("Falling back to global catalog for recommendations")
    # Try featured first
    stmt = select(Product).where(
        Product.is_active == True,
        Product.is_featured == True,
        Product.image_url != None
    ).limit(limit)
    result = await db.execute(stmt)
    products = result.scalars().all()

    if not products:
        # If no featured, just get random active products
        stmt = select(Product).where(
            Product.is_active == True,
            Product.image_url != None
        )
        if gender and gender != "Unisex":
            stmt = stmt.where(Product.gender == gender)
        
        stmt = stmt.order_by(func.random()).limit(limit)
        result = await db.execute(stmt)
        products = result.scalars().all()

    return {
        "source": "fallback_catalog",
        "products": [
            {
                "id": p.id,
                "name": p.name,
                "brand": p.brand,
                "price": int(p.current_price or 0),
                "originalPrice": int((p.current_price / (1 - (p.discount_percent or 0) / 100)) if p.current_price and (p.discount_percent or 0) < 100 else (p.current_price or 0)),
                "image": p.image_url,
                "url": generate_affiliate_url(p.affiliate_url or p.original_url, p.store, None),
                "category": p.category
            } for p in products
        ]
    }


# ─────────────────────────────────────────────────────────────────────────────
# Image Proxy — lets the browser fetch external garment images without CORS
# GET /api/v1/catalog/image-proxy?url=https://...
# ─────────────────────────────────────────────────────────────────────────────
@router.get("/catalog/image-proxy")
async def image_proxy(url: str = Query(..., description="External image URL to proxy")):
    """
    Fetches an external image server-side and streams it back to the browser.
    This is needed so TryOnWidget can load product images as binary blobs for
    multipart upload without hitting CORS restrictions in the browser.
    """
    import httpx
    from fastapi.responses import StreamingResponse
    import urllib.parse

    # Basic sanity check — only allow http/https
    parsed = urllib.parse.urlparse(url)
    if parsed.scheme not in ("http", "https"):
        from fastapi import HTTPException
        raise HTTPException(400, "Only http/https URLs are allowed")

    try:
        async with httpx.AsyncClient(follow_redirects=True, timeout=15.0) as client:
            resp = await client.get(url, headers={"User-Agent": "TryZonAI-Bot/1.0"})
            resp.raise_for_status()
            content_type = resp.headers.get("content-type", "image/jpeg")
            return StreamingResponse(
                iter([resp.content]),
                media_type=content_type,
                headers={"Cache-Control": "public, max-age=86400"}
            )
    except Exception as e:
        log.warning(f"Image proxy failed for {url}: {e}")
        from fastapi import HTTPException
        raise HTTPException(502, f"Could not fetch image: {e}")


@router.get("/trending-outfit")
async def get_trending_outfit(
    gender: str | None = Query(None, description="Gender preference: Men | Women | Unisex"),
    db: AsyncSession = Depends(get_db)
):
    """Returns a random featured trending product outfit for Daily AI Style Engine filtered by gender."""
    stmt = select(Product).where(Product.is_active == True)
    if gender:
        stmt = stmt.where(or_(Product.gender.ilike(f"%{gender}%"), Product.gender.ilike("%Unisex%"), Product.gender == None))
    stmt = stmt.order_by(func.random()).limit(1)
    
    res = await db.execute(stmt)
    prod = res.scalar_one_or_none()
    if prod:
        return {
            "id": str(prod.id),
            "name": prod.name,
            "category": prod.category,
            "gender": prod.gender or "Unisex",
            "image_url": prod.image_url,
            "price": int(prod.current_price or 0)
        }
    return {
        "id": "default_trend",
        "name": "Trending AI Look",
        "category": "Outfit",
        "gender": gender or "Unisex",
        "image_url": "https://api.tryzonai.com/static/sample_outfit.jpg",
        "price": 0
    }

