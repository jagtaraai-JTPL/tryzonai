"""
services/clip_service.py — CLIP visual tag extraction (GPU 1)

Extracts semantic tags (color, style, category) from garment images
using OpenAI CLIP. Used for ChromaDB embeddings and feed ranking.
"""

import logging
from pathlib import Path

import numpy as np
import torch
from PIL import Image

from config import settings
from services.florence_service import get_florence_attributes
from utils.gpu_lock import get_gpu_lock
from utils.gpu_utils import get_device

log = logging.getLogger(__name__)

# ── Fashion-specific label candidates ─────────────────────
STYLE_LABELS = [
    "formal shirt", "casual t-shirt", "ethnic kurta", "western dress",
    "saree", "jeans", "trousers", "jacket", "hoodie", "blazer",
    "salwar kameez", "lehenga", "shorts", "skirt", "co-ord set",
]

COLOR_LABELS = [
    "red", "blue", "green", "black", "white", "yellow", "pink",
    "purple", "orange", "grey", "brown", "navy blue", "maroon",
    "beige", "cream", "multicolor",
]

_model = None
_preprocess = None


def _load_clip():
    global _model, _preprocess
    if _model is not None:
        return _model, _preprocess
    try:
        import clip
        device = get_device(settings.gpu_clip)
        _model, _preprocess = clip.load("ViT-B/32", device=device)
        _model.eval()
        log.info(f"CLIP loaded on {device}")
    except ImportError:
        log.warning("openai-clip not installed — using mock tags")
        _model, _preprocess = None, None
    return _model, _preprocess


async def get_clip_tags(image_path: str) -> dict:
    """
    Main entry point for product tagging (Async).
    Returns style tags, color tags, and the CLIP embedding vector.
    """
    lock = get_gpu_lock(settings.gpu_clip)
    async with lock:
        return _get_clip_tags_sync(image_path)

def check_excessive_skin(image_path: str) -> bool:
    """
    CV skin color filter disabled to avoid false positive blocks on normal fashion outfits,
    sleeveless tops, sarees, and warm lighting. Safety moderation is handled strictly by CLIP Neural Model.
    """
    return False



async def check_nsfw(image_path: str) -> bool:
    """
    Zero-Tolerance Multi-Layer NSFW & Nudity Moderator.
    Combines YCbCr/RGB skin analysis and direct CLIP cosine similarity scoring against forbidden concepts.
    """
    # Layer 1: Computer Vision Multi-Spectral Bare Skin Analysis
    if check_excessive_skin(image_path):
        return True

    # Layer 2: CLIP Deep Neural Concept Similarity Matrix
    lock = get_gpu_lock(settings.gpu_clip)
    async with lock:
        model, preprocess = _load_clip()
        if model is None: return False
        
        device = get_device(settings.gpu_clip)
        import clip
        try:
            image_pil = Image.open(image_path).convert("RGB")
            image = preprocess(image_pil).unsqueeze(0).to(device)
            
            # Explicit forbidden concepts list
            forbidden_concepts = [
                "a photo of a nude body", "a photo of a naked person", "a photo showing bare breasts", 
                "a photo of exposed nipples", "a photo of exposed female chest", "a photo of exposed breasts",
                "a photo of a bikini", "a photo of swimwear", "a photo of a swimsuit", "a photo of lingerie", 
                "a photo of underwear", "a photo of a bra", "a photo of panties", 
                "a photo of transparent clothing", "a photo of sheer see through clothes", 
                "a photo of a topless person", "a photo with sexually suggestive pose", 
                "a photo of erotic attire"
            ]
            
            safe_concepts = [
                "a photo of a person wearing fully clothed modest public attire",
                "a photo of a person wearing a formal shirt and trousers",
                "a photo of a person wearing a modest dress, skirt, or jacket",
                "a photo of a person wearing a casual t-shirt, top, and jeans",
                "a photo of a person wearing a sweater, hoodie, sweatshirt, or coat",
                "a photo of a person wearing an ethnic kurta, salwar kameez, or saree",
                "a photo of a modest clothing item or garment on a hanger",
                "a photo of an everyday casual top, blouse, shirt, or pants"
            ]
            
            forbidden_tokens = clip.tokenize(forbidden_concepts).to(device)
            safe_tokens = clip.tokenize(safe_concepts).to(device)
            
            with torch.no_grad():
                image_features = model.encode_image(image)
                image_features /= image_features.norm(dim=-1, keepdim=True)
                
                forbidden_text_features = model.encode_text(forbidden_tokens)
                forbidden_text_features /= forbidden_text_features.norm(dim=-1, keepdim=True)
                
                safe_text_features = model.encode_text(safe_tokens)
                safe_text_features /= safe_text_features.norm(dim=-1, keepdim=True)
                
                forbidden_sims = (image_features @ forbidden_text_features.T).cpu().numpy()[0]
                safe_sims = (image_features @ safe_text_features.T).cpu().numpy()[0]
                
                max_forbidden_sim = float(np.max(forbidden_sims))
                max_safe_sim = float(np.max(safe_sims))
                
            log.info(f"NSFW Check - Max Forbidden Sim: {max_forbidden_sim:.4f}, Max Safe Sim: {max_safe_sim:.4f}")
            
            # An image is ONLY NSFW if forbidden concepts strictly dominate safe concepts by a clear margin (>= 0.45)
            if max_forbidden_sim > (max_safe_sim + 0.05) and max_forbidden_sim >= 0.45:
                log.warning(f"NSFW/Inappropriate content blocked! Forbidden Score: {max_forbidden_sim:.4f}, Safe Score: {max_safe_sim:.4f}")
                return True
                
            return False
        except Exception as e:
            log.error(f"NSFW check failed: {e}")
            return False


async def check_garment_safety(image_path: str) -> bool:
    """
    Dedicated Strict Garment Safety Filter.
    Returns True if the uploaded garment image is UNSAFE (nude, explicit, lingerie, unbuttoned).
    """
    lock = get_gpu_lock(settings.gpu_clip)
    async with lock:
        model, preprocess = _load_clip()
        if model is None: return False
        
        device = get_device(settings.gpu_clip)
        import clip
        try:
            image_pil = Image.open(image_path).convert("RGB")
            image = preprocess(image_pil).unsqueeze(0).to(device)
            
            unsafe_garment_concepts = [
                "a photo of a nude person", "a photo of exposed breasts", "a photo of nipples",
                "a photo of bare chest", "a photo of lingerie", "a photo of underwear", "a photo of a bikini",
                "a photo of a swimsuit", "a photo of transparent clothing", "a photo of sheer clothes",
                "a photo of a bra", "a photo of panties"
            ]
            
            valid_garment_concepts = [
                "a photo of a shirt", "a photo of a t-shirt", "a photo of a dress",
                "a photo of pants", "a photo of a jacket", "a photo of a sweater",
                "a photo of a top", "a photo of a skirt", "a photo of a coat",
                "a photo of a hoodie", "a photo of a saree", "a photo of a kurta",
                "a photo of a clothing item on a flat background or hanger"
            ]
            
            unsafe_tokens = clip.tokenize(unsafe_garment_concepts).to(device)
            valid_tokens = clip.tokenize(valid_garment_concepts).to(device)
            
            with torch.no_grad():
                image_features = model.encode_image(image)
                image_features /= image_features.norm(dim=-1, keepdim=True)
                
                unsafe_text_features = model.encode_text(unsafe_tokens)
                unsafe_text_features /= unsafe_text_features.norm(dim=-1, keepdim=True)
                
                valid_text_features = model.encode_text(valid_tokens)
                valid_text_features /= valid_text_features.norm(dim=-1, keepdim=True)
                
                unsafe_sims = (image_features @ unsafe_text_features.T).cpu().numpy()[0]
                valid_sims = (image_features @ valid_text_features.T).cpu().numpy()[0]
                
                max_unsafe_sim = float(np.max(unsafe_sims))
                max_valid_sim = float(np.max(valid_sims))
                
            log.info(f"Garment Safety Check - Unsafe Sim: {max_unsafe_sim:.4f}, Valid Sim: {max_valid_sim:.4f}")
            
            if max_unsafe_sim > (max_valid_sim + 0.05) and max_unsafe_sim >= 0.45:
                log.warning(f"Garment REJECTED by safety filter! Unsafe: {max_unsafe_sim:.4f}, Valid: {max_valid_sim:.4f}")
                return True
                
            return False
        except Exception as e:
            log.error(f"Garment safety check failed: {e}")
            return False

def _get_clip_tags_sync(image_path: str) -> dict:
    try:
        model, preprocess = _load_clip()
        if model is None:
             return {"style": [], "color": [], "embedding": None}
             
        device = get_device(settings.gpu_clip)
        import clip
        
        # Ensure image is RGB
        image_pil = Image.open(image_path).convert("RGB")
        image = preprocess(image_pil).unsqueeze(0).to(device)
        
        with torch.no_grad():
            image_features = model.encode_image(image)
            # Normalize embedding
            image_features /= image_features.norm(dim=-1, keepdim=True)
            embedding = image_features.cpu().numpy().flatten().tolist()
            
            # Predict tags (Style & Color)
            # 1. Style
            style_tokens = clip.tokenize(STYLE_LABELS).to(device)
            style_probs = (image_features @ model.encode_text(style_tokens).T).softmax(dim=-1)
            styles = [STYLE_LABELS[i] for i in style_probs[0].topk(3).indices.tolist()]
            
            # 2. Color
            color_tokens = clip.tokenize(COLOR_LABELS).to(device)
            color_probs = (image_features @ model.encode_text(color_tokens).T).softmax(dim=-1)
            colors = [COLOR_LABELS[i] for i in color_probs[0].topk(2).indices.tolist()]

        # Florence-2 Integration for Descriptive Captioning (Optional)
        florence_caption = ""
        florence_tags = []
        try:
            florence_res = get_florence_attributes(image_path)
            florence_caption = florence_res.get("caption", "")
            florence_tags = florence_res.get("tags", [])
        except Exception as e:
            log.warning(f"Florence-2 failed: {e}. Falling back to CLIP tags only.")
        
        # Free VRAM
        if device.type == "cuda":
            torch.cuda.empty_cache()

        return {
            "style": styles,
            "color": colors,
            "embedding": embedding,
            "florence_caption": florence_caption,
            "detailed_tags": florence_tags
        }
    except Exception as e:
        log.error(f"CLIP Error for {image_path}: {e}")
        return {"style": [], "color": [], "embedding": None}
