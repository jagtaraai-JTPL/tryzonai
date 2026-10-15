import os
import json
import uuid
import logging
import aiohttp
import asyncio
import time
from typing import Optional, List
from config import settings
from utils.image_utils import compress_result_image

log = logging.getLogger(__name__)

async def get_queue_remaining(session: aiohttp.ClientSession, url: str) -> int:
    """Fetch the number of remaining items in the ComfyUI queue."""
    try:
        async with session.get(f"{url}/queue", timeout=2) as resp:
            if resp.status == 200:
                data = await resp.json()
                # queue_running is items currently being processed
                # queue_pending is items waiting in queue
                running = len(data.get("queue_running", []))
                pending = len(data.get("queue_pending", []))
                return running + pending
    except Exception as e:
        log.warning(f"Could not fetch queue for {url}: {e}")
    return 999  # Treat as full if unreachable

async def get_sorted_backend_urls() -> List[str]:
    """
    Returns all ComfyUI URLs sorted by their current queue size (smallest first).
    Enables automatic failover if the primary worker is down or OOMs.
    """
    # master-server runs with network_mode:host, so Docker container names are NOT resolvable.
    # ComfyUI workers are mapped to localhost:8188 and localhost:8189.
    urls_str = os.getenv("COMFYUI_URLS", os.getenv("COMFYUI_URL", "http://localhost:8188,http://localhost:8189"))
    urls = [u.strip() for u in urls_str.split(",") if u.strip()]
    
    if not urls:
        return ["http://localhost:8188"]
    
    if len(urls) == 1:
        return [urls[0]]

    async with aiohttp.ClientSession() as session:
        tasks = [get_queue_remaining(session, url) for url in urls]
        queue_sizes = await asyncio.gather(*tasks)
    
    # Pair URLs with their sizes and sort
    worker_stats = sorted(zip(urls, queue_sizes), key=lambda x: x[1])
    return [w[0] for w in worker_stats]


def prepare_person_canvas(person_image_path: str, target_mp: float = 2.0) -> None:
    """
    Rescales person input photo to target megapixels (2.0 MP for Paid/VIP users, 1.5 MP for Free users)
    and ensures dimensions are multiples of 8.
    This guarantees VAEEncode (Node 150) receives a full 2.0 MP canvas and generates native 2.0 MP 2K Ultra HD output.
    """
    try:
        from PIL import Image
        import math
        img = Image.open(person_image_path)
        w, h = img.size
        current_mp = (w * h) / 1_000_000.0
        
        target_pixels = int(target_mp * 1_000_000)
        aspect_ratio = w / float(h) if h > 0 else 0.5625
        
        target_h = int(math.sqrt(target_pixels / aspect_ratio))
        target_w = int(target_h * aspect_ratio)
        
        target_w = max(64, (target_w // 8) * 8)
        target_h = max(64, (target_h // 8) * 8)
        
        if abs(current_mp - target_mp) > 0.05 or (w % 8 != 0) or (h % 8 != 0):
            resampled = img.resize((target_w, target_h), Image.LANCZOS)
            resampled.save(person_image_path, "JPEG", quality=95)
            log.info(f"📐 Prepared person input canvas from {w}x{h} ({current_mp:.2f} MP) to {target_w}x{target_h} ({(target_w*target_h)/1e6:.2f} MP) for native 2K VAE output.")
    except Exception as e:
        log.error(f"Error preparing person canvas resolution: {e}")


def strip_model_head_from_garment(garment_path: str, category: str = "clothing", sub_type: str = "full_body_outfit", exact_item: str = "outfit", framing: str = "full_body") -> str:
    """
    Rule 14: Strips catalog model's head/face and hair above the chin/neckline.
    Prevents model hair, narrow neck, and head from leaking into ReferenceLatents.
    Preserves 100% of the garment neckline, collar, shoulders, and sleeves.
    Only strips lower trousers/pants for upper-body items when an actual catalog model is wearing pants.
    Full body outfits (saree, lehenga, gown, dress, etc.) and standalone flat-lays are 100% preserved.
    """
    try:
        from PIL import Image
        import numpy as np
        import cv2

        if not os.path.exists(garment_path):
            return garment_path

        img = Image.open(garment_path).convert("RGB")
        w, h = img.size

        # 1. Detect model face/chin using OpenCV Face Detector & YCrCb Skin Analysis (no fake fallback)
        cv_img = cv2.imread(garment_path)
        chin_y = 0
        has_real_face = False
        if cv_img is not None:
            try:
                from utils.garment_analyzer import get_face_bbox
                bbox = get_face_bbox(cv_img, allow_fallback=False)
                if bbox is not None:
                    bx, by, bw, bh = bbox
                    if by < int(h * 0.40) and bh >= max(25, int(h * 0.05)):
                        has_real_face = True
                        max_chin_pct = 0.38
                        chin_y = min(int(h * max_chin_pct), by + bh + int(bh * 0.05))

                # Rule 14: Only strip head when a genuine face/head is detected by YuNet or Haar Cascade.
                # Never guess or crop on headless catalog images or flat-lays.
            except Exception as fe:
                log.warning(f"Face bbox detection error: {fe}")

        is_footwear = (category == "footwear" or exact_item in ["footwear", "shoes", "sneakers", "heels", "boots", "sandals", "slippers", "loafers"])

        # If a model/person is detected in a footwear photo, crop strictly to bottom feet/shoes (y >= 0.65 * h) to prevent model face/outfit leakage
        if is_footwear and has_real_face:
            crop_top_y = int(h * 0.65)
            clean_img = img.crop((0, crop_top_y, w, h))
            clean_filename = f"{os.path.splitext(os.path.basename(garment_path))[0]}_clean.jpg"
            clean_path = os.path.join(settings.upload_dir, clean_filename)
            clean_img.save(clean_path, "JPEG", quality=95)
            log.info(f"✂️ Footwear Model Guard: Detected model in footwear reference. Cropped to bottom feet/shoes (y={crop_top_y}px) -> {clean_path}")
            return clean_path

        # If no real face is detected and it is an isolated accessory, preserve 100% as is (Rule 12 compliant)
        if not has_real_face and (category in ["handbag", "footwear", "watch", "eyewear", "jewelry", "hat"] or exact_item in ["handbag", "purse", "bag", "shoes", "sneakers", "heels", "watch", "sunglasses", "hat"]):
            if not garment_path.startswith(settings.upload_dir):
                import shutil
                dest = os.path.join(settings.upload_dir, os.path.basename(garment_path))
                shutil.copyfile(garment_path, dest)
                return dest
            return garment_path

        is_full_outfit = (exact_item in ["saree", "lehenga", "gown", "dress", "anarkali", "jumpsuit", "kurta", "outfit", "set", "frock", "co-ord", "onepiece", "one-piece", "suit", "tuxedo", "skirt suit", "sherwani", "outfit set"] or sub_type == "full_body_outfit")
        is_upper_wear = (exact_item in ["jacket", "shirt", "t-shirt", "top", "blouse", "crop top", "sweater", "hoodie", "cardigan", "coat"] or sub_type == "upper_body")

        # 2. Check if lower trousers/skirts/bags should be cropped for upper-body wear (Rule 14)
        crop_bottom_y = h
        if not is_full_outfit and is_upper_wear and has_real_face and h >= int(w * 1.25):
            crop_bottom_y = int(h * 0.78)
        elif framing in ["headshot_portrait", "selfie_portrait"] and not is_full_outfit and is_upper_wear and h >= int(w * 1.15):
            crop_bottom_y = int(h * 0.65)
            log.info(f"✂️ Framing Guard: Headshot selfie detected. Cropping garment to upper-body (y={crop_bottom_y}px) to match portrait framing.")

        if chin_y > 15 or crop_bottom_y < h:
            crop_top_y = chin_y if chin_y > 15 else 0
            clean_img = img.crop((0, crop_top_y, w, crop_bottom_y))
            clean_filename = f"{os.path.splitext(os.path.basename(garment_path))[0]}_clean.jpg"
            clean_path = os.path.join(settings.upload_dir, clean_filename)
            clean_img.save(clean_path, "JPEG", quality=95)
            log.info(f"✂️ Rule 14: Stripped model head y={crop_top_y}px ({crop_top_y/h*100:.1f}%) and bottom y={crop_bottom_y}px ({crop_bottom_y/h*100:.1f}%) -> {clean_path}")
            return clean_path
    except Exception as e:
        log.warning(f"Error in strip_model_head_from_garment: {e}")
    if not garment_path.startswith(settings.upload_dir):
        import shutil
        dest = os.path.join(settings.upload_dir, os.path.basename(garment_path))
        shutil.copyfile(garment_path, dest)
        return dest
    return garment_path



async def run_flux_tryon(person_image_path: str, garment_image_path: str, is_premium: bool = False) -> str:
    """
    Submits a try-on job with AUTOMATIC FAILOVER & AI Safety Guardrails.
    If input images are unsafe or nude, blocks submission before calling ComfyUI GPU workers.
    Sets 2.0 MP Ultra HD resolution for Paid/Pro Users (is_premium=True) vs 1.5 MP for Free Users.
    """
    # 0. Pre-Flight Mandatory AI Safety Guardrail Check
    try:
        from services.clip_service import check_nsfw, check_garment_safety
        if await check_nsfw(person_image_path) or await check_garment_safety(garment_image_path):
            log.warning(f"ComfyUI Job Rejected by Pre-Flight Safety Guardrail! Person: {person_image_path}, Garment: {garment_image_path}")
            raise Exception("Inappropriate, nude, or NSFW content detected in input images. Request blocked by AI Safety Guardrail.")
    except Exception as se:
        if "Inappropriate" in str(se) or "NSFW" in str(se):
            raise se
        log.error(f"Pre-flight safety check error: {se}")

    backend_urls = await get_sorted_backend_urls()
    
    # 1. Load workflow
    workflow_path = os.getenv("WORKFLOW_PATH", "/app/comfyui/workflow/TryZonAI JagTara Pvt. Ltd api.json")
    if not os.path.exists(workflow_path):
        # Fallback to local path if running on host without env var
        local_fallback = os.path.join(os.path.dirname(__file__), "../../comfyui/workflow/TryZonAI JagTara Pvt. Ltd api.json")
        if os.path.exists(local_fallback):
            workflow_path = local_fallback
        else:
            log.error(f"Workflow file not found at {workflow_path}")
            raise Exception(f"Workflow file missing: {workflow_path}")

    with open(workflow_path) as f:
        workflow = json.load(f)

    # 2. Inject inputs — Pure Raw Person Image (No masking/slicing) & Garment Image
    person_filename = os.path.basename(person_image_path)
    garment_filename = os.path.basename(garment_image_path)

    # Detect category, framing, clothing sub-type, and color descriptor via Neural Vision to instruct Flux via text prompting
    try:
        from utils.garment_analyzer import (
            detect_photo_framing,
            analyze_person_photo_advanced,
            analyze_garment_vision_descriptor,
            preprocess_and_center_garment
        )
        person_vision = analyze_person_photo_advanced(person_image_path)
        framing = person_vision.get("framing", "full_body")
        vision_info = analyze_garment_vision_descriptor(garment_image_path)

        category = vision_info.get("category", "clothing")
        sub_type = vision_info.get("sub_type", "full_body_outfit")
        sleeve_type = vision_info.get("sleeve", "unknown")
        exact_item = vision_info.get("exact_item", "outfit")

        person_filename = os.path.basename(person_image_path)
        # Direct clean garment delivery compliant with Rule 12 & Rule 14 chin stripping
        garment_clean_path = strip_model_head_from_garment(garment_image_path, category=category, sub_type=sub_type, exact_item=exact_item, framing=framing)
        garment_filename = os.path.basename(garment_clean_path)

        log.info(f"🎨 Flux TryOn Auto-Tuner -> Item: '{exact_item}', Category: '{category}', Sleeve: '{sleeve_type}', Framing: '{framing}', Garment: '{garment_filename}'")
    except Exception as e:
        log.error(f"Error detecting vision features: {e}")
        framing = "full_body"
        category = "clothing"
        sub_type = "full_body_outfit"
        sleeve_type = "unknown"
        exact_item = "outfit"
        vision_info = {"detected": False}
        person_vision = {"is_wide_shot": False, "arms_crossed": False}
        person_filename = os.path.basename(person_image_path)
        garment_filename = os.path.basename(garment_image_path)

    if "209:208" in workflow:
        workflow["209:208"]["inputs"]["image"] = person_filename
    if "121" in workflow:
        workflow["121"]["inputs"]["image"] = garment_filename

    # Disable white box mask (Node 162 switch = True disables inpainting mask preview and white rectangular cut-out)
    if "162" in workflow and "inputs" in workflow["162"]:
        workflow["162"]["inputs"]["switch"] = True
        log.info("🚫 ComfyUI Node 162 switch set to True: White Box Mask preview DISABLED. 100% Pure Flux 2 Prompting & Reference Latent active.")

    # 🎲 Randomize KSampler Seed to prevent static latent trapping and identical image hallucinations
    if "156" in workflow and "inputs" in workflow["156"]:
        import random
        random_seed = random.randint(1, 999999999999999)
        workflow["156"]["inputs"]["seed"] = random_seed
        log.info(f"🎲 Randomizing LanPaint KSampler Node 156 Seed: {random_seed}")

    # Construct clean, highly-effective, affirmative Flux 2 prompt instructions (Item Name ONLY — colors handled visually by ReferenceLatent)
    # Fallback prompt if vision cannot identify outfit details: "transfer the fashion"
    # ── DECOUPLED MODULAR PROMPT ARCHITECTURE ──────────────────────────────────────
    # ── HARMONIZED INPUT FRAMING & GARMENT DEMAND MATRIX ──────────────────────────────
    user_framing = person_vision.get("framing", "full_body")
    person_gender = person_vision.get("gender", "person")
    gender_target = "woman" if person_gender == "woman" else ("man" if person_gender == "man" else "person")
    is_headshot = (user_framing == "headshot_portrait")
    is_head_accessory = (category in ["eyewear", "hat"] or exact_item in ["sunglasses", "goggles", "glasses", "eyewear", "hat", "cap"])
    is_accessory = (category in ["handbag", "footwear", "watch", "jewelry", "eyewear", "hat"] or exact_item in ["handbag", "purse", "bag", "crossbody", "tote", "clutch", "footwear", "shoes", "sneakers", "heels", "boots", "sandals", "watch", "necklace", "earrings", "ring", "bracelet", "sunglasses", "goggles", "glasses", "hat", "cap"])
    is_full_outfit = (exact_item in ["saree", "lehenga", "gown", "dress", "onepiece", "one-piece", "frock", "co-ord", "kurta"] or sub_type == "full_body_outfit")
    person_outfit = person_vision.get("person_outfit", "outfit")

    # ── ULTRA-CLEAN DIRECT TRANSFER PROMPT ARCHITECTURE (RULE 14 COMPLIANT) ─────────
    item_lower = str(exact_item).lower() if exact_item else category.lower()
    target_item = item_lower if item_lower and item_lower not in ["unknown", "none"] else "outfit"

    if is_head_accessory or item_lower in ["sunglasses", "goggles", "glasses", "eyewear", "frames"]:
        COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the face in Image 1"
    elif item_lower in ["hat", "cap", "beanie"]:
        COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the head in Image 1"
    elif item_lower in ["backpack"]:
        COLOR_PROMPT = f"transfer the single backpack from Image 2 onto Image 1, carried on back"
    elif item_lower in ["clutch"]:
        COLOR_PROMPT = f"transfer the single clutch from Image 2 onto Image 1, held in hand"
    elif item_lower in ["crossbody"]:
        COLOR_PROMPT = f"transfer the single crossbody bag from Image 2 onto Image 1, carried across shoulder"
    elif category == "handbag" or item_lower in ["handbag", "purse", "tote", "bag", "shoulder bag", "satchel"]:
        COLOR_PROMPT = f"transfer only the single main {target_item} from Image 2 held in one hand by the {gender_target} in Image 1"
    elif category == "watch" or item_lower in ["watch", "timepiece"]:
        if is_headshot:
            COLOR_PROMPT = f"transfer the single {target_item} from Image 2 into Image 1, portrait view"
        else:
            COLOR_PROMPT = f"transfer the single {target_item} from Image 2 onto the wrist in Image 1"
    elif category == "jewelry" or item_lower in ["necklace", "chain", "pendant", "earrings", "ring", "bracelet", "bangle", "jewelry"]:
        COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the {gender_target} in Image 1"
    elif category == "footwear" or item_lower in ["footwear", "shoes", "sneakers", "heels", "boots", "sandals", "slippers", "loafers"]:
        if is_headshot or user_framing in ["headshot_portrait", "half_body", "selfie_portrait"]:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the feet of the {gender_target} in Image 1, full length standing view"
        else:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the feet of the {gender_target} in Image 1"
    elif is_full_outfit or item_lower in ["dress", "gown", "saree", "lehenga", "anarkali", "jumpsuit", "onepiece", "one-piece", "frock", "co-ord", "kurta", "outfit set", "outfit", "set", "suit", "tuxedo", "skirt suit", "sherwani"]:
        if is_headshot:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the {gender_target} in Image 1, fully replacing the outfit, full length standing view"
        elif person_outfit in ["saree", "lehenga"] and item_lower not in ["saree", "lehenga"]:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto Image 1, fully replacing the {person_outfit}"
        else:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the {gender_target} in Image 1, fully replacing the outfit"
    elif item_lower in ["top", "shirt", "t-shirt", "sweatshirt", "blouse", "crop top", "sweater", "hoodie", "cardigan", "jacket", "coat"] or sub_type == "upper_body":
        if is_headshot:
            COLOR_PROMPT = f"transfer only the {target_item} from Image 2 onto the upper body of the {gender_target} in Image 1, fully replacing the upper clothing, close-up portrait"
        elif person_outfit in ["saree", "dress", "lehenga"]:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto Image 1, replacing the {person_outfit} with the {target_item}"
        else:
            COLOR_PROMPT = f"transfer only the {target_item} from Image 2 onto the upper body of the {gender_target} in Image 1, fully replacing the upper clothing"
    elif item_lower in ["pants", "jeans", "trousers", "joggers", "leggings", "skirt", "shorts", "boxers", "bermudas"] or sub_type == "bottom_body":
        if is_headshot:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the lower body of the {gender_target} in Image 1, fully replacing the lower clothing, full length standing view"
        elif person_outfit in ["saree", "dress", "lehenga"]:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto Image 1, replacing the {person_outfit} with the {target_item}"
        else:
            COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the lower body of the {gender_target} in Image 1, fully replacing the lower clothing"
    elif is_headshot:
        COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the {gender_target} in Image 1, close-up portrait"
    elif item_lower in ["swimsuit", "monokini", "bikini", "bodysuit", "swimwear", "lingerie set", "lingerie"]:
        COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto Image 1, fully replacing the outfit"
    else:
        COLOR_PROMPT = f"transfer the {target_item} from Image 2 onto the {gender_target} in Image 1"


    if "107" in workflow and "inputs" in workflow["107"] and "text" in workflow["107"]["inputs"]:
        workflow["107"]["inputs"]["text"] = COLOR_PROMPT
        log.info(f"📢 INJECTING FLUX 2 PROMPT (Node 107): '{COLOR_PROMPT}'")

    # High Resolution Output: 2.0 MP (Ultra HD 2K) for Paid/Pro Users vs 1.5 MP for Free Users
    target_mp = 2.0 if is_premium else 1.5
    prepare_person_canvas(person_image_path, target_mp=target_mp)

    if "135" in workflow and "inputs" in workflow["135"]:
        workflow["135"]["inputs"]["value"] = target_mp
        log.info(f"📐 Target Resolution set to {target_mp} MP (Paid/Pro VIP: {is_premium}).")

    # 3. Execute with Retries on different workers
    last_error = None
    for url in backend_urls:
        client_id = str(uuid.uuid4())
        try:
            log.info(f"Submitting Job to ComfyUI: {url} (Client: {client_id})")
            image_data = await get_images_robust(url, workflow, client_id)
            
            if not image_data:
                log.warning(f"Worker {url} returned no image, trying next...")
                continue

            # 4. Save result with WebP compression
            unique_id = uuid.uuid4().hex[:8]
            result_filename = f"{person_filename.rsplit('.', 1)[0]}_{unique_id}_result.webp"
            result_path = os.path.join(settings.output_dir, result_filename)
            
            compress_result_image(image_data, result_path)
            log.info(f"Result saved to {result_path} via {url}")

            # Post-Generation AI Safety Guardrail Check
            from services.clip_service import check_nsfw
            if await check_nsfw(result_path):
                log.warning(f"🚫 Generated result image blocked by AI Safety Guardrail! Image: {result_path}")
                if os.path.exists(result_path):
                    try: os.remove(result_path)
                    except Exception: pass
                raise Exception("Inappropriate or revealing attire detected in generated image. Request blocked by AI Safety Guardrail.")

            # Deliver raw, pristine, photorealistic ComfyUI output directly (all post-processing disabled as per user instruction)
            log.info(f"Delivering raw photorealistic ComfyUI output directly for {result_path}")

            # Save original high-res image (lossless PNG)
            highres_filename = result_filename.replace("_result.webp", "_highres.png")
            highres_path = os.path.join(settings.output_dir, highres_filename)
            with open(highres_path, "wb") as f:
                f.write(image_data)
            log.info(f"High-res original saved to {highres_path}")

            return result_path

        except Exception as e:
            last_error = e
            log.error(f"Worker {url} failed: {e}. Trying failover...")
            continue
    
    raise last_error or Exception("All ComfyUI workers failed.")

async def run_advanced_wardrobe_tryon(
    person_image_path: str, 
    collection_id: str, 
    accessory_ids: list[str],
    garment_image_path: Optional[str] = None
) -> str:
    """
    Advanced orchestrator with AUTOMATIC FAILOVER.
    """
    backend_urls = await get_sorted_backend_urls()
    
    # Define collection styles
    STYLES = {
        "streetwear": "Oversized graphic hoodie and baggy cargo pants",
        "chic": "Tailored blazer and elegant silk slip dress",
        "workwear": "Structured button-down shirt and high-waisted trousers",
        "evening": "Sophisticated floor-length gown with metallic accents"
    }
    
    selected_style = STYLES.get(collection_id, "Modern fashion ensemble")
    acc_desc = " and ".join(accessory_ids) if accessory_ids else "no extra accessories"
    
    # Load primary JagTara API workflow strictly
    workflow_path = os.getenv("WORKFLOW_PATH", "/app/comfyui/workflow/TryZonAI JagTara Pvt. Ltd api.json")
    if not os.path.exists(workflow_path):
        local_fallback = os.path.join(os.path.dirname(__file__), "../../comfyui/workflow/TryZonAI JagTara Pvt. Ltd api.json")
        if os.path.exists(local_fallback):
            workflow_path = local_fallback

    with open(workflow_path) as f:
        workflow = json.load(f)

    # Inject Inputs
    person_filename = os.path.basename(person_image_path)
    if "209:208" in workflow:
        workflow["209:208"]["inputs"]["image"] = person_filename
    
    if garment_image_path and "121" in workflow:
        workflow["121"]["inputs"]["image"] = os.path.basename(garment_image_path)

    if "107" in workflow:
        if garment_image_path:
            prompt = f"Realistically dress the person in the provided garment, adding {acc_desc}."
        else:
            prompt = f"Transform the person into a {selected_style} look, accessorized with {acc_desc}."
        workflow["107"]["inputs"]["text"] = prompt

    # Execute with Failover
    last_error = None
    for url in backend_urls:
        client_id = str(uuid.uuid4())
        try:
            log.info(f"Submitting Advanced Job to: {url}")
            image_data = await get_images_robust(url, workflow, client_id)
            
            if not image_data:
                continue

            result_filename = f"wardrobe_{collection_id}_{uuid.uuid4().hex[:8]}.webp"
            result_path = os.path.join(settings.output_dir, result_filename)
            
            compress_result_image(image_data, result_path)

            # Save original high-res image (lossless PNG)
            highres_filename = result_filename.replace(".webp", "_highres.png")
            highres_path = os.path.join(settings.output_dir, highres_filename)
            with open(highres_path, "wb") as f:
                f.write(image_data)
            log.info(f"High-res wardrobe original saved to {highres_path}")

            return result_path
        except Exception as e:
            last_error = e
            log.warning(f"Advanced failover from {url}: {e}")
            continue

    raise last_error or Exception("Advanced generation failed on all workers")

async def get_images_robust(backend_url: str, workflow: dict, client_id: str):
    payload = json.dumps({"prompt": workflow, "client_id": client_id}).encode()
    ws_url = backend_url.replace("http://", "ws://") + f"/ws?client_id={client_id}"

    async with aiohttp.ClientSession() as session:
        # 1. Queue the prompt
        try:
            async with session.post(f"{backend_url}/prompt", data=payload,
                                    headers={"Content-Type": "application/json"}) as resp:
                if resp.status != 200:
                    text = await resp.text()
                    raise Exception(f"ComfyUI Error {resp.status}: {text}")
                resp_json = await resp.json()
                if "prompt_id" not in resp_json:
                    raise Exception(f"ComfyUI rejected prompt: {resp_json}")
                prompt_id = resp_json["prompt_id"]
                log.info(f"Queued prompt_id: {prompt_id} on {backend_url}")
        except Exception as e:
            log.error(f"Failed to queue prompt on {backend_url}: {e}")
            raise e

        # 2. Hybrid Wait: WebSocket with Polling Fallback
        async def wait_via_websocket():
            try:
                async with session.ws_connect(ws_url, timeout=300) as ws:
                    async for msg in ws:
                        if msg.type == aiohttp.WSMsgType.TEXT:
                            data = json.loads(msg.data)
                            if data.get("type") == "executing":
                                d = data.get("data", {})
                                if d.get("node") is None and d.get("prompt_id") == prompt_id:
                                    log.info(f"WebSocket: Execution complete for {prompt_id}")
                                    return True
                        elif msg.type in (aiohttp.WSMsgType.CLOSED, aiohttp.WSMsgType.ERROR):
                            break
            except Exception as e:
                log.warning(f"WebSocket listener encountered error on {backend_url}: {e}")
            return False

        async def wait_via_polling():
            # Max wait 5 minutes
            for i in range(60):
                await asyncio.sleep(5)
                try:
                    async with session.get(f"{backend_url}/history/{prompt_id}") as resp:
                        if resp.status == 200:
                            history = await resp.json()
                            if prompt_id in history:
                                log.info(f"Polling: Detected completion for {prompt_id} on {backend_url}")
                                return True
                except:
                    continue
            return False

        # Run both, stop at first success (don't cancel polling if websocket returns False)
        ws_task = asyncio.create_task(wait_via_websocket())
        poll_task = asyncio.create_task(wait_via_polling())

        completed_successfully = False
        while not completed_successfully:
            done, pending = await asyncio.wait(
                [ws_task, poll_task],
                return_when=asyncio.FIRST_COMPLETED
            )
            for t in done:
                try:
                    if t.result() is True:
                        completed_successfully = True
                        break
                except Exception:
                    pass

            if completed_successfully:
                for t in pending:
                    t.cancel()
                break

            if not pending:
                break

        # 3. Fetch result from history (Focus on SaveImage nodes)
        async with session.get(f"{backend_url}/history/{prompt_id}") as resp:
            history = await resp.json()

        job = history.get(prompt_id, {})
        outputs = job.get("outputs", {})
        
        # Priority 1: Node 9 (SaveImage)
        if "9" in outputs:
            node_output = outputs["9"]
            images = node_output.get("images", [])
            if images:
                return await download_image(session, backend_url, images[0])
        
        # Priority 2: Filter all output images (ignore temp subfolders and small preview images)
        candidate_images = []
        for node_id, node_output in outputs.items():
            if node_id == "9": continue
            images = node_output.get("images", [])
            for img_info in images:
                sub = img_info.get("subfolder", "").lower()
                img_type = img_info.get("type", "").lower()
                filename = img_info.get("filename", "").lower()
                # Exclude temp masks and preview thumbnails
                if "temp" in sub or "temp" in filename or img_type == "temp":
                    continue
                candidate_images.append((node_id, img_info))
        
        for node_id, img_info in candidate_images:
            log.info(f"Falling back to node {node_id} ({img_info.get('filename')}) for main output image")
            return await download_image(session, backend_url, img_info)

        # Priority 3: Fallback to any image if no non-temp output found
        for node_id, node_output in outputs.items():
            images = node_output.get("images", [])
            if images:
                log.warning(f"Final fallback to node {node_id} ({images[0].get('filename')})")
                return await download_image(session, backend_url, images[0])
        
    return None

async def download_image(session, backend_url, image_info):
    params = {
        "filename": image_info["filename"], 
        "subfolder": image_info.get("subfolder",""), 
        "type": image_info.get("type","output")
    }
    async with session.get(f"{backend_url}/view", params=params) as resp:
        return await resp.read()
