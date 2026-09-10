"""
services/florence_service.py — Microsoft Florence-2 (MIT License)
Granular attribute detection (fabric, pattern, specific style)
"""

import logging
import torch
from PIL import Image
from transformers import AutoProcessor, AutoModelForCausalLM

from config import settings
from utils.gpu_utils import get_device

log = logging.getLogger(__name__)

_model = None
_processor = None

def _load_florence():
    global _model, _processor
    if _model is not None:
        return _model, _processor
    
    device = get_device(settings.gpu_florence)
    try:
        from transformers import AutoConfig
        model_id = 'microsoft/Florence-2-base'
        config = AutoConfig.from_pretrained(model_id, trust_remote_code=True)
        if not hasattr(config, "forced_bos_token_id"):
            config.forced_bos_token_id = None
            
        log.info(f"Loading Florence-2 from {model_id} on {device}...")
        _model = AutoModelForCausalLM.from_pretrained(
            model_id, 
            config=config,
            trust_remote_code=True,
            device_map={"": device} if device.type == "cuda" else None
        ).eval()
        _processor = AutoProcessor.from_pretrained(model_id, trust_remote_code=True)
        log.info("Florence-2 loaded successfully.")
    except Exception as e:
        log.error(f"Failed to load Florence-2: {e}")
        _model, _processor = None, None
        
    return _model, _processor

def run_florence_task(image_path: str, task_prompt: str = "<DETAILED_CAPTION>") -> str:
    """
    Run a specific Florence-2 task on the image.
    Popular prompts: <CAPTION>, <DETAILED_CAPTION>, <MORE_DETAILED_CAPTION>, <OD>
    """
    model, processor = _load_florence()
    if not model or not processor:
        return ""

    try:
        image = Image.open(image_path).convert("RGB")
        device = get_device(settings.gpu_florence)
        
        inputs = processor(text=task_prompt, images=image, return_tensors="pt").to(device)
        
        generated_ids = model.generate(
            input_ids=inputs["input_ids"],
            pixel_values=inputs["pixel_values"],
            max_new_tokens=1024,
            do_sample=False,
            num_beams=3,
        )
        
        generated_text = processor.batch_decode(generated_ids, skip_special_tokens=False)[0]
        parsed_answer = processor.post_process_generation(
            generated_text, 
            task=task_prompt, 
            image_size=(image.width, image.height)
        )
        if device.type == "cuda":
            torch.cuda.empty_cache()
            
        return parsed_answer.get(task_prompt, "")
    except Exception as e:
        log.error(f"Florence-2 task failed: {e}")
        return ""

def get_florence_attributes(image_path: str) -> dict:
    """
    Extract granular attributes from the image.
    Focuses on 'detailed caption' which usually describes fabric and style.
    """
    detailed = run_florence_task(image_path, "<DETAILED_CAPTION>")
    # Simple attribute extraction from caption (naive for now)
    # Florence-2 often mentions 'silk', 'cotton', 'floral', 'striped' in detailed captions.
    return {
        "caption": detailed,
        "tags": [tag.strip() for tag in detailed.split(',') if len(tag.strip()) > 3]
    }
