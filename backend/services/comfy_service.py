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

async def run_flux_tryon(person_image_path: str, garment_image_path: str) -> str:
    """
    Submits a try-on job with AUTOMATIC FAILOVER.
    If the first GPU worker fails, it tries the second one before giving up.
    """
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

    # 2. Inject inputs & AI Safety Guardrails
    person_filename = os.path.basename(person_image_path)
    garment_filename = os.path.basename(garment_image_path)
    
    if "209:208" in workflow:
        workflow["209:208"]["inputs"]["image"] = person_filename
    if "121" in workflow:
        workflow["121"]["inputs"]["image"] = garment_filename

    # Mandatory AI Safety Prompt Guardrail for Google Play & Server Security Compliance
    SAFETY_GUARDRAIL = ", person is fully clothed in modest public attire, wearing complete opaque top and bottom clothing, strictly zero nudity, no bare chest, no nipples, no breasts, no bikini, no lingerie, no underwear, no see through transparent clothes, no suggestive pose"
    if "107" in workflow and "inputs" in workflow["107"] and "text" in workflow["107"]["inputs"]:
        workflow["107"]["inputs"]["text"] = str(workflow["107"]["inputs"]["text"]) + SAFETY_GUARDRAIL

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
    
    # Load advanced workflow
    workflow_path = os.getenv("WORKFLOW_PATH", "/app/comfyui/workflow/TryZonAI JagTara Pvt. Ltd api.json")
    if not os.path.exists(workflow_path):
        workflow_path = "/app/comfyui/workflow/Flux2_Clothes_Swap_API.json"
    
    if not os.path.exists(workflow_path):
        # Local fallback
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

        # Run both, stop at first success
        done, pending = await asyncio.wait(
            [asyncio.create_task(wait_via_websocket()), asyncio.create_task(wait_via_polling())],
            return_when=asyncio.FIRST_COMPLETED
        )
        for task in pending:
            task.cancel()

        # 3. Fetch result from history (Focus on Node 9)
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
        
        # Priority 2: Any other node with images
        for node_id, node_output in outputs.items():
            if node_id == "9": continue
            images = node_output.get("images", [])
            if images:
                log.info(f"Falling back to node {node_id} for output image")
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
