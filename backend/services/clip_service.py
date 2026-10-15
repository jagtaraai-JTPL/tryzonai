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
        _model, _preprocess = clip.load("ViT-B/16", device=device)
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
    CV skin color filter using combined YCrCb and HSV analysis.
    Masks out face and neck area so close-up headshots and selfies do NOT trigger false nudity blocks.
    Uses HSV saturation gating to avoid false-triggering on neutral/light beige/wood walls.
    Only flags if non-face bare skin covers more than 55% of the total frame.
    Note: Must NEVER be called on garments (only person images).
    """
    try:
        import cv2
        img = cv2.imread(image_path)
        if img is None:
            return False
        h, w, _ = img.shape
        
        # 1. YCrCb skin mask
        img_ycbcr = cv2.cvtColor(img, cv2.COLOR_BGR2YCrCb)
        lower_ycbcr = np.array([0, 133, 77], dtype=np.uint8)
        upper_ycbcr = np.array([255, 173, 127], dtype=np.uint8)
        mask_ycbcr = cv2.inRange(img_ycbcr, lower_ycbcr, upper_ycbcr)

        # 2. HSV gating (H in skin tones [0, 25] or [165, 180], S >= 35 to reject flat beige/white walls)
        img_hsv = cv2.cvtColor(img, cv2.COLOR_BGR2HSV)
        lower_hsv1 = np.array([0, 35, 50], dtype=np.uint8)
        upper_hsv1 = np.array([25, 255, 255], dtype=np.uint8)
        mask_hsv1 = cv2.inRange(img_hsv, lower_hsv1, upper_hsv1)
        
        lower_hsv2 = np.array([165, 35, 50], dtype=np.uint8)
        upper_hsv2 = np.array([180, 255, 255], dtype=np.uint8)
        mask_hsv2 = cv2.inRange(img_hsv, lower_hsv2, upper_hsv2)
        
        mask_hsv = cv2.bitwise_or(mask_hsv1, mask_hsv2)
        skin_mask = cv2.bitwise_and(mask_ycbcr, mask_hsv)

        # 3. Mask out upper face, head, and neck region
        try:
            from utils.garment_analyzer import get_face_bbox
            face_rect = get_face_bbox(img)
            if face_rect is not None:
                fx, fy, fw, fh = face_rect
                y1 = max(0, fy - int(fh * 0.3))
                y2 = min(h, fy + int(fh * 2.0))
                x1 = max(0, fx - int(fw * 0.5))
                x2 = min(w, fx + int(fw * 1.5))
                skin_mask[y1:y2, x1:x2] = 0
        except Exception as fe:
            log.warning(f"Face mask subtraction warning in skin check: {fe}")

        skin_ratio = np.sum(skin_mask > 0) / float(h * w)
        if skin_ratio > 0.55:
            log.warning(f"Excessive body bare skin ratio detected: {skin_ratio * 100:.1f}%")
            return True
        return False
    except Exception as e:
        log.error(f"Skin check error: {e}")
        return False



async def check_minor_in_image(image_path: str) -> bool:
    """
    🔒 CHILD SAFETY PROTOCOL — Critical Hard-Block Guardian.
    Detects if the uploaded person photo contains a child, toddler, baby,
    or minor (estimated age < 18) using dual-layer detection:

    Layer 1: CLIP neural vision-language scoring
      — Compares image against child/minor/toddler vs adult concept embeddings.
      — Blocks if child concepts dominate adult concepts with a safety margin.

    Layer 2: Face proportion heuristic (failsafe)
      — Children have proportionally larger heads relative to body height.
      — Face height > 30% of total image height → strong minor indicator.

    Returns True if a minor is detected → HARD BLOCK.
    Never runs on garment images.
    """
    lock = get_gpu_lock(settings.gpu_clip)
    async with lock:
        model, preprocess = _load_clip()
        if model is not None:
            device = get_device(settings.gpu_clip)
            import clip
            try:
                image_pil = Image.open(image_path).convert("RGB")
                image = preprocess(image_pil).unsqueeze(0).to(device)

                # Concepts indicating a minor / child
                minor_concepts = [
                    "a photo of a baby or infant",
                    "a photo of a toddler or young child",
                    "a photo of a child under 10 years old",
                    "a photo of a young girl or young boy child",
                    "a photo of a little kid or small child",
                    "a photo of a child wearing clothes",
                    "a photo of a school-age child",
                    "a photo of a minor or underage person",
                    "a photo of a kid playing or posing",
                    "a photo of a small child standing",
                ]

                # Concepts indicating an adult person
                adult_concepts = [
                    "a photo of an adult man or woman",
                    "a photo of a grown-up person",
                    "a photo of an adult model posing",
                    "a photo of a mature adult wearing fashion clothes",
                    "a photo of an adult person standing",
                    "a photo of a young adult or college student",
                    "a photo of a working professional adult",
                    "a selfie photo of an adult person",
                    "a mobile portrait photo of an adult man or woman",
                    "a photo of an Indian adult man or woman",
                ]

                minor_tokens = clip.tokenize(minor_concepts).to(device)
                adult_tokens = clip.tokenize(adult_concepts).to(device)

                with torch.no_grad():
                    image_features = model.encode_image(image)
                    image_features /= image_features.norm(dim=-1, keepdim=True)

                    minor_text_features = model.encode_text(minor_tokens)
                    minor_text_features /= minor_text_features.norm(dim=-1, keepdim=True)

                    adult_text_features = model.encode_text(adult_tokens)
                    adult_text_features /= adult_text_features.norm(dim=-1, keepdim=True)

                    minor_sims = (image_features @ minor_text_features.T).cpu().numpy()[0]
                    adult_sims = (image_features @ adult_text_features.T).cpu().numpy()[0]

                    max_minor_sim = float(np.max(minor_sims))
                    max_adult_sim = float(np.max(adult_sims))

                log.info(f"👶 Child Safety Check — Minor Sim: {max_minor_sim:.4f}, Adult Sim: {max_adult_sim:.4f}")

                # Tier 1: Very high minor score (absolute hard-block threshold)
                if max_minor_sim >= 0.295:
                    log.warning(f"🚨 CHILD SAFETY BLOCK! Minor score: {max_minor_sim:.4f} (absolute threshold >= 0.295)")
                    return True

                # Tier 2: Minor concept dominates adult concept with safety margin
                if max_minor_sim >= 0.280 and max_minor_sim > (max_adult_sim + 0.030):
                    log.warning(f"🚨 CHILD SAFETY BLOCK! Minor ({max_minor_sim:.4f}) dominates Adult ({max_adult_sim:.4f})")
                    return True

            except Exception as e:
                log.error(f"Child safety CLIP check failed: {e}")

    # Layer 2: Face proportion failsafe — children have large face-to-body ratios
    try:
        import cv2
        img_cv = cv2.imread(image_path)
        if img_cv is not None:
            h, w, _ = img_cv.shape
            from utils.garment_analyzer import get_face_bbox
            face_rect = get_face_bbox(img_cv)
            if face_rect is not None:
                fx, fy, fw, fh = face_rect
                face_height_ratio = fh / float(h)
                face_width_ratio = fw / float(w)
                # Only evaluate head-to-body ratio if full standing body is visible (at least 3.5x head heights below chin)
                # In close-up selfies / portraits, face ratio is naturally 30-70% for adults
                is_full_body_framing = (fy + int(fh * 3.5)) <= h
                if is_full_body_framing and face_height_ratio > 0.28 and face_width_ratio > 0.22:
                    log.warning(f"🚨 CHILD SAFETY BLOCK (face heuristic)! Standing child detected: Face H ratio: {face_height_ratio:.3f}, W ratio: {face_width_ratio:.3f}")
                    return True
    except Exception as e:
        log.error(f"Child safety face heuristic failed: {e}")

    return False


async def check_nsfw(image_path: str) -> bool:
    """
    Intelligent Multi-Layer NSFW & Nudity Moderator.
    Uses CLIP neural vision-language model with calibrated margins and fast-pass safe clothing recognition.
    Strictly blocks genuine adult nudity, exposed genitals, breasts, and lingerie,
    while reliably allowing evening gowns, bridal wear, sleeveless tops, and ethnic attire.
    """
    lock = get_gpu_lock(settings.gpu_clip)
    async with lock:
        model, preprocess = _load_clip()
        if model is not None:
            device = get_device(settings.gpu_clip)
            import clip
            try:
                image_pil = Image.open(image_path).convert("RGB")
                image = preprocess(image_pil).unsqueeze(0).to(device)
                
                # Strict explicit forbidden concepts (Pornography, full nudity, exposed genitalia, breasts, underwear)
                forbidden_concepts = [
                    "a photo of a fully nude body",
                    "a photo of an uncovered naked person",
                    "a photo showing bare breasts or nipples",
                    "a photo of exposed female breasts",
                    "a photo of exposed male genitals or female genitalia",
                    "a photo of exposed buttocks or bare ass without clothing",
                    "a photo of a person wearing only underwear or panties",
                    "a photo of a person wearing only a bra",
                    "a photo of explicit pornography or sexual acts",
                    "a photo of an unclothed person with no clothes on",
                    "explicit adult nudity"
                ]
                
                # Broad, realistic safe concepts covering western, ethnic, everyday, and summer attire
                safe_concepts = [
                    "a photo of a person wearing fully clothed public attire",
                    "a photo of a person wearing a formal shirt, blouse, and trousers",
                    "a photo of a person wearing a modest dress, gown, skirt, or jacket",
                    "a photo of a person wearing an evening gown, party dress, or glamorous gown",
                    "a photo of a person wearing a cocktail dress, maxi dress, or wedding gown",
                    "a photo of a person wearing a traditional saree, lehenga, choli, or anarkali",
                    "a photo of a person wearing an ethnic kurta, kurti, or salwar kameez",
                    "a photo of a person wearing a casual t-shirt, top, polo, crop top, and jeans",
                    "a photo of a person wearing a vest, banyan, tank top, or undershirt",
                    "a photo of a person wearing a sweater, hoodie, sweatshirt, or coat",
                    "a photo of a person wearing shorts, skirt, pants, or trousers",
                    "a photo of a person wearing fashion sunglasses, goggles, eyeglasses, or frames",
                    "a photo of a close-up selfie or portrait photo of a person face",
                    "a photo of a person posing in fashionable clothing",
                    "a photo of an ordinary person standing indoors or outdoors",
                    "a photo of an Indian man or woman wearing everyday home clothing",
                    "a photo of a clothing item, accessory, or gown on a mannequin or hanger",
                    "a photo of fashion glasses, sunglasses, goggles, watch, handbag, or shoes"
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
                
                # Tier 1: Severe high absolute forbidden score (>= 0.32) -> Zero Tolerance Hard Block
                if max_forbidden_sim >= 0.320:
                    log.warning(f"🚫 NSFW content hard blocked! Forbidden Score: {max_forbidden_sim:.4f}, Safe Score: {max_safe_sim:.4f}")
                    return True
                
                # Tier 2: Forbidden concept strongly dominates safe concept with significant delta margin (+0.035)
                if max_forbidden_sim >= 0.285 and max_forbidden_sim > (max_safe_sim + 0.035):
                    log.warning(f"🚫 NSFW content blocked! Forbidden dominates Safe (Forbidden: {max_forbidden_sim:.4f} > Safe: {max_safe_sim:.4f})")
                    return True

                # Tier 3: Affirmative Safe Clothing Fast-Pass (Safe concept matches strongly or exceeds forbidden)
                if max_safe_sim >= 0.24 and max_safe_sim >= max_forbidden_sim:
                    log.info(f"✅ Safe clothing affirmed (Safe: {max_safe_sim:.4f} >= Forbidden: {max_forbidden_sim:.4f}). Allowed.")
                    return False


            except Exception as e:
                log.error(f"NSFW check failed with exception: {e}")

    # Tier 4: Computer Vision bare skin fallback ONLY when CLIP is uncertain or unavailable
    return check_excessive_skin(image_path)


async def check_garment_safety(image_path: str) -> bool:
    """
    Dedicated Strict Garment Safety Filter.
    Returns True if the uploaded garment image is UNSAFE (nude model, explicit, underwear).
    Guarantees that legitimate garments (gowns, shoes, sarees, dresses) are NEVER falsely flagged.
    NOTE: Never runs skin-color heuristics on garments.
    """
    lock = get_gpu_lock(settings.gpu_clip)
    async with lock:
        model, preprocess = _load_clip()
        if model is not None:
            device = get_device(settings.gpu_clip)
            import clip
            try:
                image_pil = Image.open(image_path).convert("RGB")
                image = preprocess(image_pil).unsqueeze(0).to(device)
                
                unsafe_garment_concepts = [
                    "a photo of a nude person",
                    "a photo of exposed breasts or nipples",
                    "a photo of bare chest without clothing",
                    "a photo of lingerie, underwear, bra, or panties",
                    "a photo of an unclothed naked person",
                    "bare skin nudity"
                ]
                
                valid_garment_concepts = [
                    "a photo of a shirt", "a photo of a t-shirt", "a photo of a dress", "a photo of a gown",
                    "a photo of an evening gown, party dress, or wedding gown",
                    "a photo of pants", "a photo of a jacket", "a photo of a sweater",
                    "a photo of a top", "a photo of a skirt", "a photo of a coat",
                    "a photo of a hoodie", "a photo of a saree", "a photo of a kurta", "a photo of a lehenga",
                    "a photo of a clothing item on a flat background or hanger",
                    "a photo of fashion sunglasses, goggles, eyeglasses, spectacles, or eyewear",
                    "a photo of a wristwatch, watch, handbag, purse, shoes, footwear, sneakers, hat, cap, or jewelry accessory"
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
                
                # Tier 1: Severe high absolute unsafe score (>= 0.295)
                if max_unsafe_sim >= 0.295:
                    log.warning(f"🚫 Garment REJECTED by safety filter! Unsafe: {max_unsafe_sim:.4f}")
                    return True

                # Tier 2: Unsafe concept strongly dominates valid concept with delta buffer (+0.02)
                if max_unsafe_sim >= 0.275 and max_unsafe_sim > (max_valid_sim + 0.02):
                    log.warning(f"🚫 Garment REJECTED! Unsafe dominates Valid with delta > 0.02 (Unsafe: {max_unsafe_sim:.4f} > Valid: {max_valid_sim:.4f})")
                    return True

                log.info(f"✅ Garment approved (Valid: {max_valid_sim:.4f} vs Unsafe: {max_unsafe_sim:.4f})")
                return False

            except Exception as e:
                log.error(f"Garment safety check failed with exception: {e}")
                return False

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
