"""
routers/video.py — LTX Video Try-On [PHASE 2 STUB]
POST /api/v1/video
"""

from fastapi import APIRouter, Depends
from database.models import User
from routers.auth import get_current_user

router = APIRouter()


@router.post("/video")
async def generate_video(
    current_user: User = Depends(get_current_user),
):
    """
    [PHASE 2] Generate a virtual try-on video using LTX Video (GPU 2+3).
    Coming soon — purchase triggers automatic video generation queue.
    """
    return {
        "status": "coming_soon",
        "message": "Video try-on is coming in Phase 2! 🎬",
        "estimated_release": "Q3 2025",
        "notify_email": current_user.email,
    }
