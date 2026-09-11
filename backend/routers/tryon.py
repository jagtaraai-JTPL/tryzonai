"""
routers/tryon.py — Virtual Try-On endpoint
POST /api/v1/tryon
"""

import logging
import time
import os
import json
from typing import Optional

from fastapi import APIRouter, Depends, File, HTTPException, Request, UploadFile, BackgroundTasks, Query
from sqlalchemy import select, text, update
from sqlalchemy.ext.asyncio import AsyncSession
import hashlib

from database.models import TryOnSession, User, Product
from database.postgres import get_db, AsyncSessionLocal
from routers.auth import get_current_user, get_optional_user
from services.comfy_service import run_flux_tryon
from utils.image_utils import save_upload, validate_image_size
from config import settings
from datetime import datetime, timezone

def log_security_violation(action: str, ip: str, user_id: str = "guest", session_id: str = "none"):
    """Permanent legal audit log for NSFW and policy violations."""
    try:
        log_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "nsfw_violations.log")
        timestamp = datetime.now(timezone.utc).isoformat()
        with open(log_file, "a") as f:
            f.write(f"[{timestamp}] ACTION={action} IP={ip} USER_ID={user_id} SESSION={session_id}\n")
    except Exception as e:
        log.error(f"Failed to write security log: {e}")

log = logging.getLogger(__name__)
router = APIRouter()


async def process_tryon_task(
    session_id_db: int,
    p_path: str,
    g_path: str,
    start_time: float,
    is_premium: bool = False
):
    try:
        result_path = await run_flux_tryon(
            person_image_path=p_path,
            garment_image_path=g_path
        )
        
        # ── Post-Generation NSFW Content Moderation ────────
        from services.clip_service import check_nsfw
        if await check_nsfw(result_path):
            if os.path.exists(result_path):
                os.remove(result_path)
            highres_path = result_path.replace("_result.webp", "_highres.png")
            if os.path.exists(highres_path):
                os.remove(highres_path)
            
            # Purge any raw ComfyUI node outputs from disk
            import glob
            for legacy_file in glob.glob(os.path.join(settings.output_dir, "Flux2_Klein_*")):
                try:
                    os.remove(legacy_file)
                    log.warning(f"Purged ComfyUI raw output file: {legacy_file}")
                except Exception:
                    pass
            
            async with AsyncSessionLocal() as db:
                session = await db.get(TryOnSession, session_id_db)
                if session:
                    session.status = "failed"
                    session.result_image_path = None
                    await db.commit()
                    log.warning(f"Post-generation NSFW blocked and hard-deleted for session {session.id}")
                    
                    # Write to legal audit log
                    log_security_violation(
                        action="BLOCKED_POST_GENERATION_NSFW",
                        ip=session.session_id or "unknown",
                        user_id=str(session.user_id) if session.user_id else "guest",
                        session_id=str(session.id)
                    )
                    
                    if session.user_id:
                        from services.notification_service import notify_user
                        await notify_user(
                            user_id=session.user_id,
                            title="Generation Blocked ⚠️",
                            body="Your Try-On was blocked because the AI generated inappropriate or NSFW content.",
                            data={},
                            image_url=None,
                            db=db
                        )
            return

        if not is_premium:
            from utils.image_utils import apply_watermark
            import asyncio
            # Run sync watermark function in threadpool to avoid blocking event loop
            await asyncio.to_thread(apply_watermark, result_path)
            
        elapsed_ms = int((time.time() - start_time) * 1000)
        
        async with AsyncSessionLocal() as db:
            session = await db.get(TryOnSession, session_id_db)
            if session:
                session.result_image_path = result_path
                session.status = "done"
                session.processing_time_ms = elapsed_ms
                await db.commit()
                log.info(f"FLUX Try-on background task complete for {session.session_id} in {elapsed_ms}ms")
                
                # Notify User
                if session.user_id:
                    from services.notification_service import notify_user
                    res_url = f"/outputs/{os.path.basename(result_path)}"
                    await notify_user(
                        user_id=session.user_id,
                        title="Your Try-On is Ready! ✨",
                        body="Tap to see your AI-generated fashion look.",
                        data={"type": "tryon_complete", "session_id": str(session.id)},
                        image_url=res_url, # Note: This needs to be absolute for FCM image
                        db=db
                    )
    except Exception as e:
        log.error(f"FLUX Try-on background task failed: {e}")
        async with AsyncSessionLocal() as db:
            session = await db.get(TryOnSession, session_id_db)
            if session:
                session.status = "failed"
                await db.commit()


@router.post("/tryon")
@router.post("/tryon/", include_in_schema=False)
async def virtual_tryon(
    background_tasks: BackgroundTasks,
    request: Request,
    person_image: UploadFile = File(..., description="Full-body photo of the person"),
    garment_image: UploadFile = File(..., description="Flat-lay or product photo of garment"),
    product_id: Optional[int] = Query(None, description="Product ID from catalog if applicable"),
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    # ── Limit Check (3 tries per day for guests and free users) ────────
    from datetime import datetime, timezone, timedelta
    today_start = datetime.now(timezone.utc).replace(hour=0, minute=0, second=0, microsecond=0)
    
    # 1. Detect real IP behind proxies/tunnels & Device ID
    cf_connecting_ip = request.headers.get("CF-Connecting-IP")
    forwarded_for = request.headers.get("X-Forwarded-For")
    
    if cf_connecting_ip:
        real_ip = cf_connecting_ip.strip()
    elif forwarded_for:
        real_ip = forwarded_for.split(",")[0].strip()
    else:
        real_ip = request.headers.get("X-Real-IP", request.client.host if request.client else "unknown_ip")

    # Normalize IPv6 to /64 prefix to prevent Airplane Mode IP rotation bypass
    if ":" in real_ip:
        parts = real_ip.split(":")
        if len(parts) >= 4:
            real_ip = ":".join(parts[:4]) + "::/64"

    # Hardware Device ID locking
    device_id_hdr = request.headers.get("X-Device-ID")
    device_key = device_id_hdr.strip() if (device_id_hdr and len(device_id_hdr.strip()) > 3) else real_ip

    # ── Concurrency Lock (Prevent Race Conditions) ──────────
    lock_id_str = str(current_user.id) if current_user else device_key
    lock_id_int = int(hashlib.md5(lock_id_str.encode()).hexdigest()[:8], 16) - 2147483648
    await db.execute(text(f"SELECT pg_advisory_xact_lock({lock_id_int})"))

    # ── Single Active Job Concurrency Guard ───────────────────
    if current_user:
        active_stmt = select(TryOnSession).where(
            TryOnSession.user_id == current_user.id,
            TryOnSession.status == "processing"
        )
    else:
        active_stmt = select(TryOnSession).where(
            TryOnSession.session_id == device_key,
            TryOnSession.status == "processing"
        )
    active_res = await db.execute(active_stmt)
    if active_res.scalar_one_or_none():
        raise HTTPException(
            status_code=409,
            detail="You already have a Try-On currently processing! Please wait a few seconds for it to finish."
        )

    # ── Global IP / Device Abuse Prevention (Max 25 Try-Ons per IP/Device per day) ────────
    global_ip_stmt = select(TryOnSession).where(
        TryOnSession.session_id == real_ip,
        TryOnSession.created_at >= today_start
    )
    global_ip_res = await db.execute(global_ip_stmt)
    if len(global_ip_res.scalars().all()) >= 25:
        raise HTTPException(
            status_code=429,
            detail="Too many Try-On requests from this network today. Please try again tomorrow."
        )

    # ── Device & Guest Usage Helper Functions ──────────────────
    now_utc = datetime.now(timezone.utc)
    today_str = now_utc.strftime("%Y-%m-%d")
    device_tracker_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "device_usage_tracker.json")

    def get_device_data() -> dict:
        if os.path.exists(device_tracker_file):
            try:
                with open(device_tracker_file, "r") as f:
                    data = json.load(f)
                    if data.get("date") == today_str:
                        return data
            except Exception as e:
                log.error(f"Error loading device tracker: {e}")
        return {"date": today_str, "devices": {}}

    def save_device_data(data: dict):
        try:
            with open(device_tracker_file, "w") as f:
                json.dump(data, f)
        except Exception as e:
            log.error(f"Error saving device tracker: {e}")

    device_data = get_device_data()
    dev_info = device_data.get("devices", {}).get(device_key, {"count": 0, "reward_ad_count": 0})
    dev_count = dev_info.get("count", 0)
    dev_ad_count = dev_info.get("reward_ad_count", 0)

    # ── Unauthenticated Limit Check (3 tries LIFETIME) ────────
    if not current_user:
        guest_tracker_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "guest_usage_tracker.json")
        guest_data = {}
        if os.path.exists(guest_tracker_file):
            try:
                with open(guest_tracker_file, "r") as f:
                    guest_data = json.load(f)
            except Exception:
                pass
                
        ip_tries_total = max(guest_data.get(device_key, 0), guest_data.get(real_ip, 0))
        is_reward_bonus = (request.headers.get("X-Daily-Style") == "true") or (request.headers.get("X-Reward-Ad-Bonus") == "true")

        if ip_tries_total >= 3 and not is_reward_bonus:
            raise HTTPException(
                status_code=403,
                detail="Guest free limit reached (3 tries). Please register to get 3 daily free tries!"
            )
        else:
            guest_data[device_key] = ip_tries_total + 1
            guest_data[real_ip] = ip_tries_total + 1
            with open(guest_tracker_file, "w") as f:
                json.dump(guest_data, f)
            log.info(f"Guest Device {device_key} / IP {real_ip} used a try. Used total: {ip_tries_total + 1}")
    
    # ── Credit & Subscription Enforcement (Authenticated) ──────────
    if current_user:
        tier = (current_user.subscription_tier or "free").lower()
        unlimited_tiers = ["pro", "premium", "vip", "monthly pro", "yearly legend", "elite"]
        
        is_unlimited = current_user.is_premium or any(t == tier for t in unlimited_tiers)
        
        log.info(f"[CREDIT_CHECK] User {current_user.id} | Device={device_key} | tier={tier} | is_premium={current_user.is_premium} | credits={current_user.credits} | paid_credits={getattr(current_user, 'paid_credits', 0)} | is_unlimited={is_unlimited}")
        
        if not is_unlimited:
            same_day = False
            if current_user.last_tryon_timestamp:
                last_time = current_user.last_tryon_timestamp
                if getattr(last_time, "tzinfo", None) is None:
                    last_time = last_time.replace(tzinfo=timezone.utc)
                if last_time.date() == now_utc.date():
                    same_day = True
            
            user_count = current_user.current_slot_tryon_count if same_day else 0
            # Lock count to device: effective count is max of user count & device count today
            current_count = max(user_count, dev_count)
            
            x_daily = request.headers.get("X-Daily-Style")
            x_reward = request.headers.get("X-Reward-Ad-Bonus")
            log.info(f"[CREDIT_CHECK] same_day={same_day} | user_count={user_count} | dev_count={dev_count} | effective_count={current_count}")
            
            if current_count >= 1:
                is_reward_bonus = (x_daily == "true") or (x_reward == "true")
                user_paid_credits = getattr(current_user, 'paid_credits', 0) or 0
                log.info(f"[CREDIT_CHECK] count>=1 | is_reward_bonus={is_reward_bonus} | credits={current_user.credits} | paid_credits={user_paid_credits}")
                
                if is_reward_bonus:
                    daily_ads = getattr(current_user, 'daily_reward_ad_count', 0) or 0
                    effective_ad_count = max(daily_ads, dev_ad_count)
                    if effective_ad_count >= 2:
                        log.warning(f"User {current_user.id} / Device {device_key} hit daily rewarded ad limit ({effective_ad_count}/2).")
                        if current_user.credits <= 0 and user_paid_credits <= 0:
                            raise HTTPException(
                                status_code=429,
                                detail="Daily free try-ons & bonus ad limit reached for this device (Max 3 tries/day). Top up credits or upgrade to Pro!"
                            )
                        else:
                            is_reward_bonus = False
                    else:
                        new_ad_count = effective_ad_count + 1
                        await db.execute(
                            update(User)
                            .where(User.id == current_user.id)
                            .values(daily_reward_ad_count=new_ad_count)
                        )
                        await db.commit()
                        current_user.daily_reward_ad_count = new_ad_count

                        # Update device tracker ad count
                        if "devices" not in device_data:
                            device_data["devices"] = {}
                        if device_key not in device_data["devices"]:
                            device_data["devices"][device_key] = {"count": 0, "reward_ad_count": 0}
                        device_data["devices"][device_key]["reward_ad_count"] = new_ad_count
                        save_device_data(device_data)

                        log.info(f"User {current_user.id} / Device {device_key} used Rewarded Ad bonus {new_ad_count}/2 for extra try-on!")
                
                if not is_reward_bonus:
                    if current_user.credits <= 0 and user_paid_credits <= 0:
                        raise HTTPException(
                            status_code=429, 
                            detail="Daily 1 free try-on limit reached for this device! Top up credits or upgrade to Pro for unlimited try-ons."
                        )
                    
                    if user_paid_credits > 0:
                        new_paid = user_paid_credits - 1
                        new_free = current_user.credits
                    else:
                        new_paid = 0
                        new_free = max(0, current_user.credits - 1)
                    
                    await db.execute(
                        update(User)
                        .where(User.id == current_user.id)
                        .values(
                            credits=new_free,
                            paid_credits=new_paid
                        )
                    )
                    await db.commit()
                    current_user.credits = new_free
                    current_user.paid_credits = new_paid
                    log.info(f"[CREDIT_CHECK] ✅ Deducted 1 credit from User {current_user.id}. Remaining credits: {current_user.credits}, paid_credits: {current_user.paid_credits}")
            else:
                new_count = current_count + 1
                await db.execute(
                    update(User)
                    .where(User.id == current_user.id)
                    .values(
                        current_slot_tryon_count=new_count,
                        last_tryon_timestamp=now_utc
                    )
                )
                await db.commit()
                current_user.current_slot_tryon_count = new_count
                current_user.last_tryon_timestamp = now_utc

                # Update device count in device_usage_tracker.json
                if "devices" not in device_data:
                    device_data["devices"] = {}
                if device_key not in device_data["devices"]:
                    device_data["devices"][device_key] = {"count": 0, "reward_ad_count": 0}
                device_data["devices"][device_key]["count"] = max(dev_count + 1, new_count)
                save_device_data(device_data)

                log.info(f"[CREDIT_CHECK] User {current_user.id} (Device {device_key}) used daily free try {new_count}/3")
        else:
            log.info(f"[CREDIT_CHECK] User {current_user.id} has unlimited access (premium={current_user.is_premium}, tier={tier})")

    # ── Read & validate uploads ───────────────────────────
    person_bytes = await person_image.read()
    garment_bytes = await garment_image.read()

    log.info(f"Upload received - Person: {len(person_bytes)} bytes, Garment: {len(garment_bytes)} bytes")
    if len(person_bytes) > 50:
        log.debug(f"Person bytes start: {person_bytes[:50].hex()}")
    if len(garment_bytes) > 50:
        log.debug(f"Garment bytes start: {garment_bytes[:50].hex()}")

    try:
        validate_image_size(person_bytes)
        validate_image_size(garment_bytes)
    except ValueError as e:
        raise HTTPException(status_code=413, detail=str(e))

    # ── Save to disk ──────────────────────────────────────
    try:
        p_path = save_upload(person_bytes, prefix="person")
        g_path = save_upload(garment_bytes, prefix="garment")
        
        # ── NSFW & Garment Content Moderation ───────────────────────
        from services.clip_service import check_nsfw, check_garment_safety
        if await check_nsfw(p_path) or await check_garment_safety(g_path):
            # Delete files immediately
            if os.path.exists(p_path): os.remove(p_path)
            if os.path.exists(g_path): os.remove(g_path)
            
            # Write to legal audit log
            log_security_violation(
                action="BLOCKED_PRE_GENERATION_UPLOAD_NSFW",
                ip=real_ip,
                user_id=str(current_user.id) if current_user else "guest"
            )
            
            log.warning(f"NSFW content blocked from IP: {real_ip}")
            raise HTTPException(
                status_code=400,
                detail="Inappropriate or NSFW content detected. Please upload an appropriate image."
            )
            
    except HTTPException as he:
        raise he
    except Exception as e:
        log.error(f"Image processing failed at save_upload: {str(e)}")
        raise HTTPException(
            status_code=400, 
            detail=f"Invalid image format or corrupt file. Error: {str(e)}"
        )

    # ── Create Session Record ────────────────────────────────
    try:
        session_record = TryOnSession(
            user_id=current_user.id if current_user else None,
            session_id=real_ip,  # Save IP to track Global Limits and Guest Limits accurately
            person_image_path=p_path,
            garment_image_path=g_path,
            product_id=product_id,
            status="processing"
        )
        db.add(session_record)
        
        # Tier-based daily limits are already checked above. 
        # Credits are reserved for special 'pay-per-use' features in the future.
        pass

        await db.commit()
        await db.refresh(session_record)
        
        # Dispatch background task
        background_tasks.add_task(
            process_tryon_task,
            session_record.id,
            p_path,
            g_path,
            time.time(),
            is_unlimited if current_user else False
        )

        # Calculate display credits
        if current_user:
            final_credits = current_user.credits
        else:
            final_credits = 99 # Limits are disabled
        
        return {
            "session_id": session_record.id,
            "status": "processing",
            "credits_remaining": max(0, final_credits),
        }

    except Exception as e:
        log.error(f"Failed to initiate try-on session: {e}")
        raise HTTPException(status_code=500, detail="Internal server error while starting try-on.")


@router.get("/tryon/status/{session_id}")
async def get_tryon_status(
    session_id: int,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    stmt = select(TryOnSession).where(TryOnSession.id == session_id)
    result = await db.execute(stmt)
    session = result.scalar_one_or_none()
    
    if not session:
        raise HTTPException(404, "Session not found")
        
    highres_url = None
    if session.status == "done" and session.result_image_path:
        base_name = os.path.basename(session.result_image_path)
        if "_result.webp" in base_name:
            highres_name = base_name.replace("_result.webp", "_highres.png")
        else:
            highres_name = base_name.replace(".webp", "_highres.png")
        highres_url = f"/outputs/{highres_name}"

    response = {
        "session_id": session.id,
        "status": session.status,
        "original_url": f"/inputs/{os.path.basename(session.person_image_path)}" if session.person_image_path else None,
        "result_url": f"/outputs/{os.path.basename(session.result_image_path)}" if session.status == "done" and session.result_image_path else None,
        "highres_url": highres_url,
        "processing_time_ms": session.processing_time_ms,
    }

    if session.status == "done":
        # 1. Fetch Recommendations (Complements)
        from routers.feed import get_similar_products
        recs = await get_similar_products(product_id=session.product_id, q=None, gender=None, limit=4, db=db)
        response["complements"] = [
            {
                "id": str(p["id"]),
                "name": p["name"],
                "category": p["category"],
                "price": int(p["price"] or 0),
                "imageUrl": p["image"],
                "url": p.get("url"),
                "matchScore": 95 + (i % 5) # Varied match score
            } for i, p in enumerate(recs.get("products", []))
        ]

        # 2. Fetch Best Deals (Price Comparison)
        from services.price_tracker import compare_prices
        from utils.geo_utils import get_country_from_request
        
        # Try to get product name for search
        query = "Fashionable Garment"
        if session.product_id:
            prod_res = await db.execute(select(Product).where(Product.id == session.product_id))
            product = prod_res.scalar_one_or_none()
            if product:
                query = product.name
        
        # Disable fake price options generation per affiliate compliance rules
        response["price_options"] = []

    return response


@router.get("/tryon/result/{session_id}")
async def get_tryon_result(
    session_id: int,
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db),
):
    """Get result image for a completed try-on session."""
    from fastapi.responses import FileResponse

    stmt = select(TryOnSession).where(TryOnSession.id == session_id)
    result = await db.execute(stmt)
    session = result.scalar_one_or_none()
    
    if not session:
        raise HTTPException(404, "Session not found")
    
    # Simple permission check
    if session.user_id and (not current_user or session.user_id != current_user.id):
        raise HTTPException(403, "Access denied")

    if session.status != "done" or not session.result_image_path:
        raise HTTPException(202, f"Session status: {session.status}")

    return FileResponse(session.result_image_path)


@router.get("/tryon/history")
async def tryon_history(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
    limit: int = 50,
):
    """Return recent try-on sessions for the current user."""
    from sqlalchemy import desc

    result = await db.execute(
        select(TryOnSession)
        .where(
            TryOnSession.user_id == current_user.id,
            TryOnSession.status == "done",
            TryOnSession.result_image_path.isnot(None)
        )
        .order_by(desc(TryOnSession.created_at))
        .limit(limit)
    )
    sessions = result.scalars().all()
    out = []
    for s in sessions:
        if not s.result_image_path:
            continue
        rel = f"/outputs/{os.path.basename(s.result_image_path)}" if not s.result_image_path.startswith("http") else s.result_image_path
        created_iso = s.created_at.isoformat() if s.created_at else ""
        out.append({
            "session_id": str(s.session_id if s.session_id else s.id),
            "id": str(s.session_id if s.session_id else s.id),
            "status": s.status,
            "result_url": rel,
            "timestamp": created_iso,
            "created_at": created_iso,
            "product_id": str(s.product_id) if s.product_id else None,
            "garment_name": None
        })
    return out


@router.get("/tryon/download/{filename}")
async def download_result(filename: str):
    """Force download of a result image with correct headers. Serves high-res if available."""
    from fastapi.responses import FileResponse
    
    # Check if high-res PNG version exists
    highres_filename = filename.replace("_result.webp", "_highres.png").replace(".webp", "_highres.png")
    highres_path = os.path.join(settings.output_dir, highres_filename)
    
    if os.path.exists(highres_path):
        log.info(f"Serving high-res download for {filename} -> {highres_filename}")
        return FileResponse(
            path=highres_path,
            filename=f"tryzon-{highres_filename}",
            media_type="image/png"
        )
        
    # Fallback to serving the original compressed WebP
    file_path = os.path.join(settings.output_dir, filename)
    if not os.path.exists(file_path):
        raise HTTPException(404, "Result image not found")
        
    return FileResponse(
        path=file_path,
        filename=f"tryzon-{filename}",
        media_type="image/webp"
    )

@router.delete("/tryon/history/{session_id}")
async def delete_tryon_session(
    session_id: str,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Delete a specific try-on session from history."""
    from sqlalchemy import delete, or_
    cond = (TryOnSession.session_id == session_id)
    try:
        int_id = int(session_id)
        cond = or_(TryOnSession.id == int_id, TryOnSession.session_id == session_id)
    except ValueError:
        pass

    stmt = delete(TryOnSession).where(
        cond,
        TryOnSession.user_id == current_user.id
    )
    res = await db.execute(stmt)
    await db.commit()
    return {"status": "success", "message": "Session deleted"}

@router.post("/tryon/claim-reward-credit")
async def claim_reward_credit(
    request: Request,
    current_user: Optional[User] = Depends(get_optional_user),
    db: AsyncSession = Depends(get_db)
):
    """Grant +1 Try-On Credit in PostgreSQL DB upon watching a Rewarded Ad (Max 2 per day)."""
    MAX_DAILY_BONUS_ADS = 2
    if current_user:
        now_utc = datetime.now(timezone.utc)
        same_day = False
        if getattr(current_user, "last_reward_ad_timestamp", None):
            last_time = current_user.last_reward_ad_timestamp
            if getattr(last_time, "tzinfo", None) is None:
                last_time = last_time.replace(tzinfo=timezone.utc)
            if last_time.date() == now_utc.date():
                same_day = True
        
        current_ad_count = getattr(current_user, "daily_reward_ad_count", 0) if same_day else 0
        
        if current_ad_count >= MAX_DAILY_BONUS_ADS:
            log.warning(f"User {current_user.id} reached daily 2 bonus ad credits limit!")
            raise HTTPException(
                status_code=429,
                detail=f"Daily ad bonus limit reached ({MAX_DAILY_BONUS_ADS}/{MAX_DAILY_BONUS_ADS})! Upgrade to Pro or buy top-up credits for unlimited try-ons 👑"
            )

        new_count = current_ad_count + 1
        await db.execute(
            update(User)
            .where(User.id == current_user.id)
            .values(
                credits=User.credits + 1,
                daily_reward_ad_count=new_count,
                last_reward_ad_timestamp=now_utc
            )
        )
        await db.commit()
        await db.refresh(current_user)
        log.info(f"🎉 Granted +1 Rewarded Ad Credit ({new_count}/{MAX_DAILY_BONUS_ADS} today) to User {current_user.id}. Total credits: {current_user.credits}")
        return {
            "status": "success",
            "credits": current_user.credits,
            "bonus_ads_claimed_today": new_count,
            "max_daily_bonus_ads": MAX_DAILY_BONUS_ADS,
            "message": f"🎉 Bonus +1 Try-On Credit Added ({new_count}/{MAX_DAILY_BONUS_ADS} today)!"
        }
    else:
        cf_connecting_ip = request.headers.get("CF-Connecting-IP")
        forwarded_for = request.headers.get("X-Forwarded-For")
        if cf_connecting_ip:
            real_ip = cf_connecting_ip.strip()
        elif forwarded_for:
            real_ip = forwarded_for.split(",")[0].strip()
        else:
            real_ip = request.headers.get("X-Real-IP", request.client.host if request.client else "unknown_ip")

        if ":" in real_ip:
            parts = real_ip.split(":")
            if len(parts) >= 4:
                real_ip = ":".join(parts[:4]) + "::/64"

        guest_tracker_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "guest_usage_tracker.json")
        guest_data = {}
        if os.path.exists(guest_tracker_file):
            try:
                with open(guest_tracker_file, "r") as f:
                    guest_data = json.load(f)
            except Exception:
                pass
        
        current_tries = guest_data.get(real_ip, 0)
        new_tries = max(0, current_tries - 1)
        guest_data[real_ip] = new_tries
        try:
            with open(guest_tracker_file, "w") as f:
                json.dump(guest_data, f)
        except Exception as e:
            log.error(f"Failed to update guest_usage_tracker: {e}")

        log.info(f"🎉 Guest Rewarded Ad Bonus registered for IP {real_ip}. Decremented used count to {new_tries}.")
        return {
            "status": "success",
            "credits": 1,
            "message": "🎉 Guest Bonus +1 Free Try-On Unlocked!"
        }
