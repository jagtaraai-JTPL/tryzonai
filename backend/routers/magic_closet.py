import os
import logging
from typing import List, Optional
from fastapi import APIRouter, Depends, Request
from pydantic import BaseModel
from utils.geo_utils import get_country_from_request

log = logging.getLogger(__name__)
router = APIRouter()

class MagicClosetItem(BaseModel):
    id: str
    image_url: str
    category: str
    region: str

# Continent Mapping
CONTINENTS = {
    # Asia
    "IN": "ASIA", "PK": "ASIA", "BD": "ASIA", "LK": "ASIA", "NP": "ASIA",
    # North America
    "US": "NA", "CA": "NA", "MX": "NA",
    # Europe
    "GB": "EU", "FR": "EU", "DE": "EU", "IT": "EU", "ES": "EU", "UK": "EU",
    # Middle East
    "AE": "UAE", "SA": "UAE", "QA": "UAE", "KW": "UAE",
}

@router.get("/magic-closet", response_model=List[MagicClosetItem])
async def get_magic_closet(request: Request):
    """
    Returns AI-curated garments based on the user's detected region.
    Logic: Specific Country Folder -> Continent Folder -> GLOBAL.
    """
    country = await get_country_from_request(request)
    continent = CONTINENTS.get(country, "GLOBAL")
    
    # Priority List for scanning
    search_folders = []
    if country: search_folders.append(country)
    if continent: search_folders.append(continent)
    search_folders.append("GLOBAL")
    
    items = []
    seen_files = set()
    
    for folder in search_folders:
        base_path = f"web/public/magic-closet/{folder}"
        public_url_base = f"/magic-closet/{folder}"
        
        if os.path.exists(base_path):
            files = os.listdir(base_path)
            for filename in files:
                if filename.lower().endswith(('.png', '.jpg', '.jpeg')) and filename not in seen_files:
                    items.append(MagicClosetItem(
                        id=f"magic_{folder}_{filename}",
                        image_url=f"{public_url_base}/{filename}",
                        category="AI Curated",
                        region=folder
                    ))
                    seen_files.add(filename)
        
        # If we found enough items for a good display, stop
        if len(items) >= 20:
            break
            
    return items
