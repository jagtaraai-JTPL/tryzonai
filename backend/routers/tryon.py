"""
routers/tryon.py — Virtual Try-On endpoint
POST /api/v1/tryon
"""

import logging
import time
import os
import json
from typing import Optional

from fastapi import APIRouter, Depends, File, Form, HTTPException, Request, UploadFile, BackgroundTasks, Query
from sqlalchemy import select, text, update
from sqlalchemy.ext.asyncio import AsyncSession
import hashlib

from database.models import TryOnSession, User, Product
from database.enhanced_models import AppAnalyticsEvent
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


async def refund_tryon_credit(
    user_id: Optional[int],
    deduction_action: Optional[str],
    device_key: Optional[str],
    real_ip: Optional[str],
    dev_id_clean: Optional[str]
):
    """
    Refunds/reverts credit or guest trial slot if try-on generation failed or was blocked by safety filters.
    """
    if not deduction_action or deduction_action in ("none", "unlimited", "daily_style_push"):
        return

    log.info(f"[CREDIT_REFUND] 🔄 Initiating credit refund for deduction_action='{deduction_action}', user_id={user_id}, device_key={device_key}")
    try:
        if deduction_action == "guest":
            guest_tracker_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "guest_usage_tracker.json")
            if os.path.exists(guest_tracker_file):
                try:
                    with open(guest_tracker_file, "r") as f:
                        guest_data = json.load(f)
                    key_to_refund = dev_id_clean if (dev_id_clean and ":" not in dev_id_clean and "." not in dev_id_clean) else None
                    if key_to_refund and key_to_refund in guest_data:
                        guest_data[key_to_refund] = max(0, guest_data[key_to_refund] - 1)
                        if guest_data[key_to_refund] == 0:
                            del guest_data[key_to_refund]
                        with open(guest_tracker_file, "w") as f:
                            json.dump(guest_data, f)
                        log.info(f"[CREDIT_REFUND] ✅ Successfully restored guest 1st lifetime try for device key: '{key_to_refund}'")
                except Exception as ge:
                    log.error(f"[CREDIT_REFUND] Failed to update guest_usage_tracker.json: {ge}")

        elif deduction_action == "daily_free" and user_id:
            async with AsyncSessionLocal() as db:
                stmt = select(User).where(User.id == user_id)
                res = await db.execute(stmt)
                usr = res.scalar_one_or_none()
                if usr:
                    new_cnt = max(0, (usr.current_slot_tryon_count or 0) - 1)
                    usr.current_slot_tryon_count = new_cnt
                    await db.commit()
                    log.info(f"[CREDIT_REFUND] ✅ Restored daily free try (1/1) for User {user_id}. New count: {new_cnt}")

            device_tracker_file = os.path.join(os.path.dirname(os.path.dirname(__file__)), "device_usage_tracker.json")
            if os.path.exists(device_tracker_file) and device_key:
                try:
                    with open(device_tracker_file, "r") as f:
                        device_data = json.load(f)
                    if "devices" in device_data and device_key in device_data["devices"]:
                        device_data["devices"][device_key]["count"] = max(0, device_data["devices"][device_key].get("count", 1) - 1)
                        with open(device_tracker_file, "w") as f:
                            json.dump(device_data, f)
                except Exception as de:
                    log.error(f"[CREDIT_REFUND] Failed to update device_usage_tracker.json: {de}")

        elif deduction_action == "paid_credit" and user_id:
            async with AsyncSessionLocal() as db:
                stmt = select(User).where(User.id == user_id)
                res = await db.execute(stmt)
                usr = res.scalar_one_or_none()
                if usr:
                    usr.paid_credits = (usr.paid_credits or 0) + 1
                    await db.commit()
                    log.info(f"[CREDIT_REFUND] ✅ Refunded +1 Paid Credit to User {user_id}. New paid_credits: {usr.paid_credits}")

        elif deduction_action == "bonus_credit" and user_id:
            async with AsyncSessionLocal() as db:
                stmt = select(User).where(User.id == user_id)
                res = await db.execute(stmt)
                usr = res.scalar_one_or_none()
                if usr:
                    usr.bonus_credits = (usr.bonus_credits or 0) + 1
                    await db.commit()
                    log.info(f"[CREDIT_REFUND] ✅ Refunded +1 Bonus Credit to User {user_id}. New bonus_credits: {usr.bonus_credits}")

    except Exception as refund_err:
        log.error(f"[CREDIT_REFUND] ❌ Exception during credit refund execution: {refund_err}")


async def process_tryon_task(
    session_id_db: int,
    p_path: str,
    g_path: str,
    start_time: float,
    is_premium: bool = False,
    deduction_action: Optional[str] = None,
    device_key: Optional[str] = None,
    real_ip: Optional[str] = None,
    dev_id_clean: Optional[str] = None
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
                    db.add(AppAnalyticsEvent(
                        user_id=session.user_id,
                        event_name="tryon_failed",
                        metadata_json=json.dumps({"session_id": session.id, "reason": "nsfw_blocked"})
                    ))
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

                    # Revert deducted credit or guest try slot
                    await refund_tryon_credit(
                        user_id=session.user_id,
                        deduction_action=deduction_action,
                        device_key=device_key,
                        real_ip=real_ip,
                        dev_id_clean=dev_id_clean
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
                db.add(AppAnalyticsEvent(
                    user_id=session.user_id,
                    event_name="tryon_completed",
                    metadata_json=json.dumps({"session_id": session.id, "processing_time_ms": elapsed_ms})
                ))
                await db.commit()
                log.info(f"FLUX Try-on background task complete for {session.session_id} in {elapsed_ms}ms")
                
                # Notify User
                if session.user_id:
                    from services.notification_service import notify_user
                    res_url = f"/outputs/{os.path.basename(result_path)}"
                    await notify_user(
                        user_id=session.user_id,
                        title="Your Try-On is Ready! ✨",
                        body="Tap to see your AI-generated fashion look now! 👗",
                        data={"type": "tryon_complete", "session_id": session.session_id or str(session.id)},
                        image_url=res_url, # Note: This needs to be absolute for FCM image
                        db=db
                    )
    except Exception as e:
        log.error(f"FLUX Try-on background task failed: {e}")
        async with AsyncSessionLocal() as db:
            session = await db.get(TryOnSession, session_id_db)
            if session:
                session.status = "failed"
                db.add(AppAnalyticsEvent(
                    user_id=session.user_id,
                    event_name="tryon_failed",
                    metadata_json=json.dumps({"session_id": session.id, "reason": str(e)[:200]})
                ))
                await db.commit()
                await refund_tryon_credit(
                    user_id=session.user_id,
                    deduction_action=deduction_action,
                    device_key=device_key,
                    real_ip=real_ip,
                    dev_id_clean=dev_id_clean
                )


@router.post("/tryon")
@router.post("/tryon/", include_in_schema=False)
async def virtual_tryon(
    background_tasks: BackgroundTasks,
    request: Request,
    person_image: UploadFile = File(..., description="Full-body photo of the person"),
    garment_image: Optional[UploadFile] = File(None, description="Flat-lay or product photo of garment"),
    product_id: Optional[int] = Form(None, description="Product ID from catalog if applicable"),
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

    device_id_hdr = request.headers.get("X-Device-ID") or request.headers.get("x-device-id")
    device_key = device_id_hdr.strip() if (device_id_hdr and len(device_id_hdr.strip()) > 3) else real_ip
    log.info(f"[GUEST_DEBUG] X-Device-ID={device_id_hdr} | device_key={device_key} | real_ip={real_ip} | user={current_user.id if current_user else 'GUEST'}")

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

    # ── Global Device Abuse Prevention (Max 25 Try-Ons per Device per day) ────────
    global_ip_stmt = select(TryOnSession).where(
        TryOnSession.session_id == device_key,
        TryOnSession.created_at >= today_start
    )
    global_ip_res = await db.execute(global_ip_stmt)
    if len(global_ip_res.scalars().all()) >= 25:
        raise HTTPException(
            status_code=429,
            detail="Too many Try-On requests from this device today. Please try again tomorrow."
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

    # ── 1. Eligibility Check (NO credit deduction or tracker write yet!) ─────
    deduction_action = "none"

    if not current_user:
        deduction_action = "guest"
        log.info(f"[GUEST_TRY] Unauthenticated guest try-on request allowed (Device={device_key}, IP={real_ip}).")
    else:
        tier = (current_user.subscription_tier or "free").lower()
        unlimited_tiers = ["pro", "premium", "vip", "monthly pro", "yearly legend", "elite"]
        
        is_unlimited = current_user.is_premium or any(t == tier for t in unlimited_tiers)
        
        if is_unlimited:
            deduction_action = "unlimited"
        else:
            same_day = False
            if current_user.last_tryon_timestamp:
                last_time = current_user.last_tryon_timestamp
                if getattr(last_time, "tzinfo", None) is None:
                    last_time = last_time.replace(tzinfo=timezone.utc)
                if last_time.date() == now_utc.date():
                    same_day = True
            
            user_count = (current_user.current_slot_tryon_count or 0) if same_day else 0
            current_count = user_count
            
            x_daily = request.headers.get("X-Daily-Style")
            is_daily_style_push = (x_daily == "true")
            x_reward = request.headers.get("X-Reward-Ad-Bonus")
            is_reward_ad = (x_reward == "true")
            user_paid_credits = getattr(current_user, 'paid_credits', 0) or 0
            user_bonus = getattr(current_user, 'bonus_credits', 0) or 0
            user_legacy_credits = getattr(current_user, 'credits', 0) or 0
            bonus_expiry = getattr(current_user, 'bonus_credits_expiry', None)
            bonus_valid = False
            if user_bonus > 0:
                if bonus_expiry:
                    if getattr(bonus_expiry, "tzinfo", None) is None:
                        bonus_expiry = bonus_expiry.replace(tzinfo=timezone.utc)
                    if bonus_expiry >= now_utc:
                        bonus_valid = True
                    else:
                        # Option B: Midnight UTC has passed — expired bonus_credits reset to 0
                        bonus_valid = False
                        user_bonus = 0
                        current_user.bonus_credits = 0
                        await db.execute(update(User).where(User.id == current_user.id).values(bonus_credits=0))
                else:
                    bonus_valid = True
            elif user_legacy_credits > 0:
                bonus_valid = True

            if current_count < 1:
                deduction_action = "daily_free"
            elif is_daily_style_push:
                deduction_action = "daily_style_push"
            elif user_paid_credits > 0 and not is_reward_ad:
                # Rule 4: Option 1 (Use 1 Credit) — Deducts 1 paid credit
                deduction_action = "paid_credit"
            elif user_paid_credits > 0 and is_reward_ad:
                # Rule 4: Option 2 (Watch Video Ad) watched by Paid Credit holder — 0 paid credits deducted
                deduction_action = "reward_ad"
            elif bonus_valid:
                # Rule 5: Deducts 1 bonus credit (Welcome / Spin Wheel / Daily Ad)
                deduction_action = "bonus_credit"
            elif (getattr(current_user, 'daily_reward_ad_count', 0) or 0) % 2 == 1:
                # User has 0.5 ad credit balance — grant try-on!
                deduction_action = "reward_ad"
            else:
                raise HTTPException(
                    status_code=429,
                    detail="Daily 1 free try-on limit reached! Top up credits or upgrade to Pro for unlimited try-ons."
                )

    # ── 2. Read & Validate Uploads & NSFW (0 credits deducted if anything fails here) ──
    person_bytes = await person_image.read()
    garment_bytes = b""
    if garment_image:
        garment_bytes = await garment_image.read()

    log.info(f"Upload received - Person: {len(person_bytes)} bytes, Garment: {len(garment_bytes)} bytes, Product ID: {product_id}")

    try:
        validate_image_size(person_bytes)
        if garment_bytes:
            validate_image_size(garment_bytes)
    except ValueError as e:
        raise HTTPException(status_code=413, detail=str(e))

    try:
        p_path = save_upload(person_bytes, prefix="person")
        if garment_bytes:
            g_path = save_upload(garment_bytes, prefix="garment")
        elif product_id:
            prod_res = await db.execute(select(Product).where(Product.id == product_id))
            product_obj = prod_res.scalar_one_or_none()
            if not product_obj or not product_obj.image_url:
                raise HTTPException(status_code=400, detail=f"Catalog product ID {product_id} not found")
            
            prod_img_path = product_obj.image_url.strip()
            if prod_img_path.startswith("/"):
                prod_img_path = prod_img_path[1:]
            
            candidate_paths = [
                os.path.join(os.path.dirname(os.path.dirname(__file__)), prod_img_path),
                os.path.join("/app", prod_img_path),
                os.path.join("/app/web/public", prod_img_path),
                os.path.join("/app/catalog_inbox", os.path.basename(prod_img_path))
            ]
            
            resolved_g_path = None
            for cand in candidate_paths:
                if os.path.exists(cand) and os.path.getsize(cand) > 0:
                    resolved_g_path = cand
                    break
            
            if not resolved_g_path:
                import httpx
                target_url = product_obj.image_url.strip()
                if not target_url.startswith("http"):
                    target_url = f"http://localhost:8001/{prod_img_path}"
                headers = {"User-Agent": "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"}
                async with httpx.AsyncClient(timeout=10.0, follow_redirects=True, headers=headers) as client:
                    resp = await client.get(target_url)
                    if resp.status_code == 200:
                        resolved_g_path = save_upload(resp.content, prefix="garment")
            
            if not resolved_g_path or not os.path.exists(resolved_g_path):
                raise HTTPException(status_code=400, detail=f"Could not load garment image for product ID {product_id}")
            
            g_path = resolved_g_path
            log.info(f"Loaded catalog garment image for Product ID {product_id} from {g_path}")
        else:
            raise HTTPException(status_code=400, detail="Either garment_image file or product_id must be provided")
        
        # NSFW & Garment Content Moderation
        from services.clip_service import check_nsfw, check_garment_safety
        if await check_nsfw(p_path) or await check_garment_safety(g_path):
            if os.path.exists(p_path): os.remove(p_path)
            if os.path.exists(g_path): os.remove(g_path)
            
            log_security_violation(
                action="BLOCKED_PRE_GENERATION_UPLOAD_NSFW",
                ip=real_ip,
                user_id=str(current_user.id) if current_user else "guest"
            )
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

    # ── 3. ATOMIC SESSION CREATION & CREDIT DEDUCTION ─────────────────────
    # Reaching here guarantees that images are 100% valid and safe!
    try:
        valid_product_id = None
        if product_id:
            try:
                pid_int = int(str(product_id).strip())
                prod_chk = await db.execute(select(Product.id).where(Product.id == pid_int))
                if prod_chk.scalar_one_or_none():
                    valid_product_id = pid_int
            except (ValueError, TypeError):
                pass

        session_record = TryOnSession(
            user_id=current_user.id if current_user else None,
            session_id=device_key,
            person_image_path=p_path,
            garment_image_path=g_path,
            product_id=valid_product_id,
            status="processing"
        )
        db.add(session_record)

        # Apply credit deduction / guest tracking ONLY NOW upon confirmed session creation
        if deduction_action == "guest":
            log.info(f"Guest Device {device_key} / IP {real_ip} initiated try-on (Zero server-side blocking).")
        elif deduction_action == "daily_free":
            new_count = current_count + 1
            await db.execute(
                update(User)
                .where(User.id == current_user.id)
                .values(current_slot_tryon_count=new_count, last_tryon_timestamp=now_utc)
            )
            current_user.current_slot_tryon_count = new_count
            current_user.last_tryon_timestamp = now_utc

            log.info(f"[CREDIT_CHECK] User {current_user.id} (Device {device_key}) used daily free try 1/1")
        elif deduction_action == "reward_ad":
            dev_ad_count = dev_info.get("reward_ad_count", 0)
            dev_info["reward_ad_count"] = dev_ad_count + 1
            device_data["devices"][device_key] = dev_info
            save_device_data(device_data)
            if current_user and (getattr(current_user, 'daily_reward_ad_count', 0) or 0) % 2 == 1:
                new_ad_cnt = current_user.daily_reward_ad_count + 1
                await db.execute(update(User).where(User.id == current_user.id).values(daily_reward_ad_count=new_ad_cnt))
                current_user.daily_reward_ad_count = new_ad_cnt
            log.info(f"[CREDIT_CHECK] 📺 User {current_user.id} used Rewarded Video Ad try-on (0 paid credits deducted)")
        elif deduction_action == "paid_credit":
            new_paid = user_paid_credits - 1
            await db.execute(update(User).where(User.id == current_user.id).values(paid_credits=new_paid))
            current_user.paid_credits = new_paid
            log.info(f"[CREDIT_CHECK] ✅ Deducted 1 paid credit from User {current_user.id}. Remaining paid_credits: {new_paid}")
        elif deduction_action == "bonus_credit":
            new_bonus = max(0, user_bonus - 1)
            new_leg = max(0, user_legacy_credits - 1)
            await db.execute(update(User).where(User.id == current_user.id).values(bonus_credits=new_bonus, credits=new_leg))
            current_user.bonus_credits = new_bonus
            current_user.credits = new_leg
            log.info(f"[CREDIT_CHECK] ✅ Used 1 bonus credit for User {current_user.id}. Remaining bonus_credits: {new_bonus}, credits: {new_leg}")

        db.add(AppAnalyticsEvent(
            user_id=current_user.id if current_user else None,
            event_name="tryon_started",
            metadata_json=json.dumps({"session_id": session_record.id, "deduction_action": deduction_action})
        ))
        await db.commit()
        await db.refresh(session_record)

        # Dispatch background task with credit refund metadata
        background_tasks.add_task(
            process_tryon_task,
            session_record.id,
            p_path,
            g_path,
            time.time(),
            is_unlimited if current_user else False,
            deduction_action,
            device_key,
            real_ip,
            dev_id_clean if ('dev_id_clean' in locals() and has_hardware_device_id) else None
        )

        final_credits = current_user.credits if current_user else 99
        return {
            "session_id": session_record.id,
            "status": "processing",
            "credits_remaining": max(0, final_credits),
        }

    except Exception as e:
        log.error(f"Failed to initiate try-on session: {e}")
        await refund_tryon_credit(
            user_id=current_user.id if current_user else None,
            deduction_action=deduction_action if 'deduction_action' in locals() else None,
            device_key=device_key if 'device_key' in locals() else None,
            real_ip=real_ip if 'real_ip' in locals() else None,
            dev_id_clean=dev_id_clean if ('dev_id_clean' in locals() and 'has_hardware_device_id' in locals() and has_hardware_device_id) else None
        )
        raise HTTPException(status_code=500, detail="Internal server error while starting try-on.")


@router.get("/tryon/status/{session_id}")
async def get_tryon_status(
    session_id: int,
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    try:
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
            "original_url": f"/api/v1/inputs/{os.path.basename(session.person_image_path)}" if session.person_image_path else None,
            "result_url": f"/outputs/{os.path.basename(session.result_image_path)}" if session.status == "done" and session.result_image_path else None,
            "highres_url": highres_url,
            "processing_time_ms": session.processing_time_ms,
        }

        if session.status == "done":
            try:
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
                        "matchScore": 95 + (i % 5)
                    } for i, p in enumerate(recs.get("products", []))
                ]
            except Exception as rec_err:
                log.error(f"Error fetching complements for status: {rec_err}")
                response["complements"] = []

            response["price_options"] = []

        return response
    except HTTPException:
        raise
    except Exception as exc:
        log.error(f"Error in get_tryon_status for session {session_id}: {exc}")
        raise HTTPException(status_code=500, detail="Internal server error fetching try-on status.")


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
    """
    Grant +1 Bonus Try-On Credit (Spin Wheel / Rewarded reward).
    Credits are saved in bonus_credits and expire within 24h (midnight UTC).
    Rule 5: Free bonus credits NEVER grant paid/ad-free status.
    """
    if not current_user:
        raise HTTPException(
            status_code=401,
            detail="Mandatory Login Required. Please sign in to earn bonus try-on credits!"
        )

    now_utc = datetime.now(timezone.utc)
    expiry_utc = now_utc.replace(hour=23, minute=59, second=59, microsecond=999999)

    # Add +1 to bonus_credits, update expiry to end of today UTC
    current_bonus = getattr(current_user, 'bonus_credits', 0) or 0
    
    # If existing bonus_credits are already expired, reset to 0 before adding
    existing_expiry = getattr(current_user, 'bonus_credits_expiry', None)
    if existing_expiry:
        if getattr(existing_expiry, 'tzinfo', None) is None:
            existing_expiry = existing_expiry.replace(tzinfo=timezone.utc)
        if existing_expiry < now_utc:
            current_bonus = 0  # expired — start fresh

    new_bonus = current_bonus + 1
    await db.execute(
        update(User)
        .where(User.id == current_user.id)
        .values(
            bonus_credits=new_bonus,
            bonus_credits_expiry=expiry_utc,
            last_reward_ad_timestamp=now_utc
        )
    )
    await db.commit()
    await db.refresh(current_user)

    log.info(f"🎉 Granted +1 Spin/Reward Bonus Credit to User {current_user.id}. Total bonus_credits: {new_bonus} (expires: {expiry_utc})")
    return {
        "status": "success",
        "bonus_credits": new_bonus,
        "bonus_credits_expiry": expiry_utc.isoformat(),
        "message": f"🎉 +1 Bonus Try-On Credit Added! Valid until midnight UTC today."
    }
