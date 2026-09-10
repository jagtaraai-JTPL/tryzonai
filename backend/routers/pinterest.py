from fastapi import APIRouter, HTTPException, Depends
from pydantic import BaseModel
import os
import logging
from automation.pinterest_bot import post_to_pinterest, KeywordRandomizer
from config import settings

router = APIRouter(prefix="/api/v1/pinterest", tags=["Pinterest"])
logger = logging.getLogger("PinterestRouter")

class PinterestPostRequest(BaseModel):
    image_filename: str
    title: str = None
    description: str = None

@router.post("/post")
async def post_image_to_pinterest(req: PinterestPostRequest):
    """
    Manually post a generated image to Pinterest.
    """
    if not settings.pinterest_access_token or not settings.pinterest_board_id:
        raise HTTPException(status_code=400, detail="Pinterest integration not configured.")

    # Construct the public URL for Pinterest to scrape
    public_url = f"{settings.website_url}/outputs/{req.image_filename}"
    
    # Use randomizer if title/description not provided
    randomizer = KeywordRandomizer()
    title = req.title or randomizer.get_random_title()
    description = req.description or randomizer.get_random_description()

    logger.info(f"Posting image to Pinterest: {req.image_filename}")
    
    success = post_to_pinterest(public_url, title, description)
    
    if success:
        return {"status": "success", "message": "Image posted to Pinterest!"}
    else:
        raise HTTPException(status_code=500, detail="Failed to post to Pinterest. Check server logs for details.")
