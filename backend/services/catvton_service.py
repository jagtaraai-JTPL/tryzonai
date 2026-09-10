"""
services/catvton_service.py — CatVTON virtual try-on generation (GPU 0)

Wraps the existing ComfyUI bridge at port 8000 for actual inference.
When CatVTON weights are downloaded locally, this switches to direct inference.

NOTE: See scripts/download_models.sh for weight download commands.
"""

import logging
import os
import uuid

import httpx
from PIL import Image

from config import settings
from utils.image_utils import compress_to_bytes, resize_image

log = logging.getLogger(__name__)


async def run_tryon(
    person_image_path: str,
    garment_image_path: str,
    mask_image_path: str | None = None,
) -> str:
    """
    Run virtual try-on and return path to the result image.

    Strategy:
    1. Try local CatVTON model (when weights available)
    2. Fall back to ComfyUI bridge (existing infrastructure)
    """
    # Check if local model available
    local_model_dir = os.getenv(
        "CATVTON_MODEL_DIR",
        "/home/hemendramehra/tryzonai_server/models/catvton",
    )
    if os.path.exists(local_model_dir) and _local_model_ready(local_model_dir):
        return await _run_local(person_image_path, garment_image_path, mask_image_path, local_model_dir)

    # Fall back to existing ComfyUI bridge
    return await _run_via_comfyui_bridge(person_image_path, garment_image_path)


def _local_model_ready(model_dir: str) -> bool:
    required = ["config.json", "pytorch_model.bin"]
    return all(os.path.exists(os.path.join(model_dir, f)) for f in required)


async def _run_local(person_path, garment_path, mask_path, model_dir) -> str:
    """Direct CatVTON inference — when model weights are present."""
    log.info("Running CatVTON locally")
    try:
        import torch
        from diffusers import AutoencoderKL
        from utils.gpu_utils import get_device

        device = get_device(settings.gpu_catvton)
        # Full CatVTON pipeline integration goes here
        # (Placeholder for when weights are downloaded)
        raise NotImplementedError("CatVTON local inference — weights not configured yet")
    except (ImportError, NotImplementedError) as e:
        log.warning(f"Local CatVTON failed ({e}), falling back to ComfyUI bridge")
        return await _run_via_comfyui_bridge(person_path, garment_path)


async def _run_via_comfyui_bridge(person_path: str, garment_path: str) -> str:
    """
    Use the existing ComfyUI bridge (port 8000) for try-on inference.
    Sends multipart/form-data with person + garment images.
    """
    log.info(f"Routing try-on to ComfyUI bridge at {settings.comfyui_bridge_url}")

    output_path = os.path.join(
        settings.output_dir, f"tryon_{uuid.uuid4().hex}.png"
    )

    async with httpx.AsyncClient(timeout=120.0) as client:
        with open(person_path, "rb") as pf, open(garment_path, "rb") as gf:
            response = await client.post(
                f"{settings.comfyui_bridge_url}/try-on",
                files={
                    "person_image": ("person.jpg", pf, "image/jpeg"),
                    "garment_image": ("garment.jpg", gf, "image/jpeg"),
                },
                headers={"X-API-Key": settings.jagtara_api_key},
            )

    if response.status_code != 200:
        raise RuntimeError(
            f"ComfyUI bridge returned {response.status_code}: {response.text}"
        )

    with open(output_path, "wb") as f:
        f.write(response.content)

    log.info(f"Try-on result saved: {output_path}")
    return output_path
