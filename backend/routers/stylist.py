"""
routers/stylist.py — Hinglish AI Stylist endpoint
POST /api/v1/stylist
POST /api/v1/stylist/stream
"""

import json
import logging

from fastapi import APIRouter, Depends, HTTPException
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database.models import User, WardrobeItem
from database.postgres import get_db
from routers.auth import get_current_user, get_optional_user
from services.ollama_service import stream_stylist_response, get_stylist_response

log = logging.getLogger(__name__)
router = APIRouter()


class StylistRequest(BaseModel):
    message: str
    include_wardrobe: bool = False
    history: list[dict] | None = None


@router.post("/stylist")
async def stylist_chat(
    req: StylistRequest,
    current_user: User | None = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Get a Hinglish fashion styling response from Qwen 9B via Ollama.
    Now includes product recommendations based on the conversation context.
    """
    wardrobe_context = None
    if req.include_wardrobe and current_user:
        wardrobe_context = await _build_wardrobe_context(current_user.id, db)

    # 1. Get AI Text Response
    ai_response = await get_stylist_response(req.message, wardrobe_context, req.history)
    
    # 2. Extract keywords for product search (Simple heuristic for now)
    from routers.feed import get_similar_products
    
    # Try to find products that match the query
    recs_data = await get_similar_products(q=req.message, limit=3, db=db)
    recommendations = recs_data.get("products", [])

    return {
        "response": ai_response, 
        "recommendations": recommendations,
        "model": "titi-global-stylist-v2"
    }


@router.post("/stylist/stream")
async def stylist_stream(
    req: StylistRequest,
    current_user: User | None = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    """
    Streaming version of stylist — SSE-style text/event-stream.
    Frontend can consume with fetch() + ReadableStream.
    """
    wardrobe_context = None
    if req.include_wardrobe:
        wardrobe_context = await _build_wardrobe_context(current_user.id, db)

    async def event_generator():
        async for chunk in stream_stylist_response(req.message, wardrobe_context, req.history):
            yield f"data: {json.dumps({'chunk': chunk})}\n\n"
        yield "data: [DONE]\n\n"

    return StreamingResponse(event_generator(), media_type="text/event-stream")


async def _build_wardrobe_context(user_id: int, db: AsyncSession) -> str:
    """Build a text description of user's wardrobe for stylist context."""
    result = await db.execute(
        select(WardrobeItem)
        .where(WardrobeItem.user_id == user_id)
        .limit(20)
    )
    items = result.scalars().all()
    if not items:
        return None

    descriptions = []
    for item in items:
        tags = []
        if item.color_tags:
            try:
                tags.extend(json.loads(item.color_tags))
            except Exception:
                pass
        if item.style_tags:
            try:
                tags.extend(json.loads(item.style_tags))
            except Exception:
                pass
        descriptions.append(f"- {item.category}: {', '.join(tags)}")

    return "\n".join(descriptions)
