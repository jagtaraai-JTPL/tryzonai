"""
routers/wardrobe.py — Virtual Wardrobe CRUD
Handles saving/removing clothes with CLIP-based auto-tagging.
"""

import logging
from typing import List, Optional

from fastapi import APIRouter, Depends, File, HTTPException, UploadFile
from pydantic import BaseModel
from sqlalchemy import select, func, delete
from sqlalchemy.ext.asyncio import AsyncSession

from database.models import User, WardrobeItem
from database.postgres import get_db
from routers.auth import get_current_user
from services.clip_service import get_clip_tags
from utils.image_utils import save_upload, validate_image_size
from config import settings

log = logging.getLogger(__name__)
router = APIRouter()

# ── Schemas ───────────────────────────────────────────────
class WardrobeItemResponse(BaseModel):
    id: int
    image_url: str
    created_at: str
    category: str
    style_tags: List[str]
    color_tags: List[str]

class DigitalWardrobeTryOnRequest(BaseModel):
    collection_id: str
    accessory_ids: List[str]

# ── Endpoints ─────────────────────────────────────────────

@router.post("/wardrobe", response_model=WardrobeItemResponse)
async def add_to_wardrobe(
    image: Optional[UploadFile] = File(None),
    image_url: Optional[str] = None,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Save a garment to your virtual wardrobe. 
    Enforces limits: Free (10 items) | Premium (Unlimited).
    """
    if not image and not image_url:
        raise HTTPException(400, "Either image or image_url is required")

    # ── Limit Check ───────────────────────────────────────
    if not current_user.is_premium:
        count_res = await db.execute(
            select(func.count()).select_from(WardrobeItem).where(WardrobeItem.user_id == current_user.id)
        )
        count = count_res.scalar_one()
        if count >= 10:
            raise HTTPException(403, "Free limit reached (10 items). Upgrade to Premium for unlimited wardrobe.")

    # ── Upload & Validate ─────────────────────────────────
    if image_url:
        if image_url.startswith("/outputs/") or image_url.startswith("/uploads/"):
            import os
            from config import settings
            base_dir = settings.output_dir if image_url.startswith("/outputs/") else settings.upload_dir
            filename = image_url.split("/")[-1]
            local_path = os.path.join(base_dir, filename)
            try:
                with open(local_path, "rb") as f:
                    img_bytes = f.read()
            except Exception as e:
                raise HTTPException(400, f"Failed to read local image: {e}")
        else:
            import httpx
            try:
                async with httpx.AsyncClient() as client:
                    res = await client.get(image_url, timeout=10.0)
                    res.raise_for_status()
                    img_bytes = res.content
            except Exception as e:
                raise HTTPException(400, f"Failed to download image from URL: {e}")
    else:
        img_bytes = await image.read()
        
    validate_image_size(img_bytes)
    path = save_upload(img_bytes, prefix="wardrobe")

    # ── Auto-Tagging via CLIP ─────────────────────────────
    tags = await get_clip_tags(path)
    # Convert list/dict to JSON string for storage (simplified)
    import json
    
    item = WardrobeItem(
        user_id=current_user.id,
        image_path=path,
        category=tags["style"][0] if tags["style"] else "unknown",
        style_tags=json.dumps(tags["style"]),
        color_tags=json.dumps(tags["color"]),
        clip_embedding_id=None, # To be updated below
    )
    
    db.add(item)
    await db.commit()
    await db.refresh(item)

    # ── Sync with ChromaDB for Personalisation ─────────────
    from services.chroma_service import add_wardrobe_embedding
    if tags.get("embedding"):
        doc_id = add_wardrobe_embedding(
            wardrobe_item_id=item.id,
            user_id=current_user.id,
            embedding=tags["embedding"],
            tags=tags["style"] + tags["color"]
        )
        item.clip_embedding_id = doc_id
        await db.commit()

    return WardrobeItemResponse(
        id=item.id,
        image_url=item.image_path.replace(settings.upload_dir + "/", "/uploads/"),
        created_at=item.created_at.isoformat() if hasattr(item, 'created_at') and item.created_at else "",
        category=item.category,
        style_tags=tags["style"],
        color_tags=tags["color"]
    )


@router.get("/wardrobe", response_model=List[WardrobeItemResponse])
async def list_wardrobe(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """List all items in the current user's wardrobe."""
    import json
    result = await db.execute(
        select(WardrobeItem).where(WardrobeItem.user_id == current_user.id)
    )
    items = result.scalars().all()
    
    return [
        WardrobeItemResponse(
            id=i.id,
            image_url=i.image_path.replace(settings.upload_dir + "/", "/uploads/"),
            created_at=i.created_at.isoformat() if hasattr(i, 'created_at') and i.created_at else "",
            category=i.category,
            style_tags=json.loads(i.style_tags or "[]"),
            color_tags=json.loads(i.color_tags or "[]"),
        )
        for i in items
    ]


@router.delete("/wardrobe/{item_id}", status_code=204)
async def remove_from_wardrobe(
    item_id: int,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Permanently remove an item from your wardrobe."""
    stmt = delete(WardrobeItem).where(
        WardrobeItem.id == item_id,
        WardrobeItem.user_id == current_user.id
    )
    res = await db.execute(stmt)
    if res.rowcount == 0:
        raise HTTPException(404, "Item not found in your wardrobe")
    
    await db.commit()
    return None

@router.post("/wardrobe/tryon")
async def digital_wardrobe_tryon(
    collection_id: str,
    accessory_ids: str, # Comma separated
    garment_url: Optional[str] = None,
    image: UploadFile = File(...),
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Handle the 'My Digital Wardrobe' AI Try-On flow.
    """
    # ── Upload & Validate ─────────────────────────────────
    img_bytes = await image.read()
    validate_image_size(img_bytes)
    p_path = save_upload(img_bytes, prefix="wardrobe_person")

    # ── AI Orchestration ──────────────────────────────────
    from services.comfy_service import run_advanced_wardrobe_tryon
    import os
    
    acc_list = [a.strip() for a in accessory_ids.split(",") if a.strip()]
    
    try:
        g_path = None
        if garment_url:
            # Resolve relative URL to absolute path
            # Assume /outputs/filename.jpg -> settings.output_dir / filename.jpg
            if "/outputs/" in garment_url:
                g_filename = garment_url.split("/outputs/")[1]
                g_path = os.path.join(settings.output_dir, g_filename)
            elif "/uploads/" in garment_url:
                g_filename = garment_url.split("/uploads/")[1]
                g_path = os.path.join(settings.upload_dir, g_filename)

        result_path = await run_advanced_wardrobe_tryon(
            person_image_path=p_path,
            collection_id=collection_id,
            accessory_ids=acc_list,
            garment_image_path=g_path
        )
        
        return {
            "status": "done",
            "result_url": f"/outputs/{os.path.basename(result_path)}",
            "look_description": f"{collection_id.capitalize()} ensemble with {', '.join(acc_list)}"
        }
    except Exception as e:
        log.error(f"Digital Wardrobe TryOn Failed: {e}")
        raise HTTPException(500, detail=str(e))
