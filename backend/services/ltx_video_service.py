"""
services/ltx_video_service.py — LTX Video generation [PHASE 2 STUB]

GPU 2 + 3 (multi-GPU). Not yet implemented.
"""

import logging

log = logging.getLogger(__name__)


async def generate_tryon_video(
    person_image_path: str,
    garment_image_path: str,
    result_image_path: str,
) -> str:
    """
    [PHASE 2] Generate a short try-on video using LTX Video model.
    GPU 2+3 (multi-GPU pipeline).

    Returns path to generated video file.
    Raises NotImplementedError until Phase 2 is activated.
    """
    raise NotImplementedError(
        "LTX Video generation is planned for Phase 2. "
        "Download weights with scripts/download_models.sh once ready."
    )
