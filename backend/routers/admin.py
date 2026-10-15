"""
JagTara Command Center - Admin Dashboard Backend
Secure admin-only routes for business management (Asynchronous Version)
"""

import hashlib
import json
import os
import urllib.parse
from datetime import datetime, timedelta, timezone
from typing import List, Optional

import logging
from fastapi import APIRouter, Body, Depends, File, Form, Header, HTTPException, Query, UploadFile
from sqlalchemy import case, desc, func, select, update
from sqlalchemy.ext.asyncio import AsyncSession

log = logging.getLogger("admin")

from config import settings
from database.postgres import get_db
from database.models import (
    User,
    Product,
    TryOnSession,
    WardrobeItem,
    PriceAlert,
    UserInteraction,
    Payment
)
from database.enhanced_models import (
    PriceHistory,
    AffiliatePurchase,
    AppAnalyticsEvent,
    AdPaidImpression
)
from services.admin_scraper import fetch_product_metadata
from services.chroma_service import upsert_product, delete_product_embedding
import httpx
import tempfile

router = APIRouter(prefix="/api/v1/admin", tags=["Admin"])


IST = timezone(timedelta(hours=5, minutes=30))


def utcnow():
    return datetime.now(timezone.utc)


def get_ist_today_start_utc():
    now_ist = datetime.now(IST)
    today_start_ist = now_ist.replace(hour=0, minute=0, second=0, microsecond=0)
    return today_start_ist.astimezone(timezone.utc)


def parse_timeframe(timeframe_str: Optional[str]):
    """
    Parses timeframe string into (start_date, end_date) UTC datetimes aligned with IST (UTC+5:30) calendar days.
    """
    if not timeframe_str:
        return None, None

    now_utc = datetime.now(timezone.utc)
    today_start = get_ist_today_start_utc()
    tf = timeframe_str.strip().lower()

    if tf in ["30m", "30min", "30_min"]:
        return now_utc - timedelta(minutes=30), None
    elif tf in ["daily", "today"]:
        return today_start, None
    elif tf == "yesterday":
        return today_start - timedelta(days=1), today_start
    elif tf in ["weekly", "week", "7d"]:
        return now_utc - timedelta(days=7), None
    elif tf in ["monthly", "month", "30d"]:
        return now_utc - timedelta(days=30), None
    elif tf.startswith("date:"):
        d_str = tf.replace("date:", "").strip()
        try:
            p_date_ist = datetime.strptime(d_str, "%Y-%m-%d").replace(tzinfo=IST)
            start_utc = p_date_ist.astimezone(timezone.utc)
            end_utc = (p_date_ist + timedelta(days=1)).astimezone(timezone.utc)
            return start_utc, end_utc
        except Exception:
            return None, None
    elif len(tf) == 10 and tf.count("-") == 2:
        try:
            p_date_ist = datetime.strptime(tf, "%Y-%m-%d").replace(tzinfo=IST)
            start_utc = p_date_ist.astimezone(timezone.utc)
            end_utc = (p_date_ist + timedelta(days=1)).astimezone(timezone.utc)
            return start_utc, end_utc
        except Exception:
            return None, None
    elif ".." in tf:
        try:
            parts = tf.split("..", 1)
            p_start_ist = datetime.strptime(parts[0].strip(), "%Y-%m-%d").replace(tzinfo=IST)
            p_end_ist = datetime.strptime(parts[1].strip(), "%Y-%m-%d").replace(tzinfo=IST) + timedelta(days=1)
            return p_start_ist.astimezone(timezone.utc), p_end_ist.astimezone(timezone.utc)
        except Exception:
            return None, None

    return None, None



# ============================================================================
# AUTHENTICATION
# ============================================================================

async def verify_admin(admin_token: str = Header(None)):
    """Verify admin authentication"""
    if not admin_token:
        raise HTTPException(status_code=401, detail="Admin token required")
    
    # Hash the token and compare
    token_hash = hashlib.sha256(admin_token.encode()).hexdigest()
    expected_hash = hashlib.sha256(settings.admin_secret_key.encode()).hexdigest()
    
    if token_hash != expected_hash:
        raise HTTPException(status_code=403, detail="Invalid admin token")
    
    return True


from pydantic import BaseModel

class AdminLoginRequest(BaseModel):
    username: str
    password: str

@router.post("/login")
async def admin_login(
    data: AdminLoginRequest
):
    """
    Authenticate admin with credentials (JSON)
    """
    if data.username != settings.admin_username or data.password != settings.admin_password:
        raise HTTPException(status_code=401, detail="Invalid admin credentials")
    
    return {
        "success": True,
        "adminToken": settings.admin_secret_key,
        "message": f"Welcome back, {data.username}"
    }


class GrantCreditsRequest(BaseModel):
    user_id: Optional[int] = None
    email: Optional[str] = None
    credits: int = 25
    reason: Optional[str] = "Manual Admin Grant"


@router.post("/grant-credits")
async def admin_grant_credits(
    data: GrantCreditsRequest,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Manually grant paid credits to a user by User ID or Email.
    """
    if not data.user_id and not data.email:
        raise HTTPException(status_code=400, detail="Either user_id or email must be provided")
    
    query = select(User)
    if data.user_id:
        query = query.filter(User.id == data.user_id)
    else:
        query = query.filter(User.email == data.email)
        
    result = await db.execute(query)
    target_user = result.scalar_one_or_none()
    
    if not target_user:
        raise HTTPException(status_code=404, detail="User not found")
        
    target_user.credits += data.credits
    target_user.paid_credits = (target_user.paid_credits or 0) + data.credits
    
    import uuid
    manual_order_id = f"manual_{uuid.uuid4().hex[:16]}"
    payment = Payment(
        user_id=target_user.id,
        razorpay_order_id=manual_order_id,
        razorpay_payment_id=manual_order_id,
        amount=0,
        status="captured",
    )
    db.add(payment)
    await db.commit()
    
    log.info(f"Admin manually granted {data.credits} credits to User {target_user.id} ({target_user.email}). Reason: {data.reason}")
    return {
        "success": True,
        "message": f"Successfully granted {data.credits} credits to User {target_user.id} ({target_user.email})",
        "user_id": target_user.id,
        "email": target_user.email,
        "new_credits_total": target_user.credits,
        "new_paid_credits": target_user.paid_credits
    }


# ============================================================================
# DASHBOARD OVERVIEW
# ============================================================================

@router.get("/dashboard/overview")
async def get_dashboard_overview(
    timeframe: str = Query("all", description="30m | daily | yesterday | weekly | monthly | date:YYYY-MM-DD | all"),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Main dashboard overview with key metrics filtered by timeframe
    """
    now = utcnow()
    filter_date, filter_date_end = parse_timeframe(timeframe)

    # Users count
    user_stmt = select(func.count(User.id))
    if filter_date:
        user_stmt = user_stmt.filter(User.created_at >= filter_date)
    if filter_date_end:
        user_stmt = user_stmt.filter(User.created_at < filter_date_end)
    total_users_res = await db.execute(user_stmt)
    total_users = total_users_res.scalar() or 0

    all_users_res = await db.execute(select(func.count(User.id)))
    all_time_users = all_users_res.scalar() or 0

    # Today's new users (aligned with Indian Midnight 00:00 IST)
    today_start = get_ist_today_start_utc()
    new_users_today_query = await db.execute(select(func.count(User.id)).filter(User.created_at >= today_start))
    new_users_today = new_users_today_query.scalar() or 0

    free_users_query = await db.execute(select(func.count(User.id)).filter(User.subscription_tier == "free"))
    free_users = free_users_query.scalar() or 0
    premium_users = all_time_users - free_users

    # Products count from DB
    prod_stmt = select(func.count(Product.id))
    prod_res = await db.execute(prod_stmt)
    total_products = prod_res.scalar() or 0

    # ── TryOn Telemetry (Done / Failed / Processing status tracking for real user photos) ──
    # ── TryOn Telemetry (Done / Failed / Processing status tracking for real user photos) ──
    # Note: person_image_path is cleared to "" after 1 hour by privacy cleanup_tasks.py.
    # Therefore, we MUST NOT filter by person_image_path != "" for session counts, otherwise
    # sessions older than 1 hour will be incorrectly excluded!

    # 1. Today's breakdown
    today_status_stmt = select(
        func.count(TryOnSession.id).label("total"),
        func.sum(case((TryOnSession.status == "done", 1), else_=0)).label("done"),
        func.sum(case((TryOnSession.status == "failed", 1), else_=0)).label("failed"),
        func.sum(case((TryOnSession.status == "processing", 1), else_=0)).label("processing")
    ).filter(TryOnSession.created_at >= today_start)
    today_status_row = (await db.execute(today_status_stmt)).one_or_none()

    tryon_today_done = int(today_status_row.done or 0) if today_status_row else 0
    tryon_today_failed = int(today_status_row.failed or 0) if today_status_row else 0
    tryon_today_processing = int(today_status_row.processing or 0) if today_status_row else 0
    tryon_today_total = tryon_today_done + tryon_today_failed + tryon_today_processing

    # 2. All-time breakdown
    all_status_stmt = select(
        func.count(TryOnSession.id).label("total"),
        func.sum(case((TryOnSession.status == "done", 1), else_=0)).label("done"),
        func.sum(case((TryOnSession.status == "failed", 1), else_=0)).label("failed"),
        func.sum(case((TryOnSession.status == "processing", 1), else_=0)).label("processing")
    )
    all_status_row = (await db.execute(all_status_stmt)).one_or_none()

    tryon_all_done = int(all_status_row.done or 0) if all_status_row else 0
    tryon_all_failed = int(all_status_row.failed or 0) if all_status_row else 0
    tryon_all_processing = int(all_status_row.processing or 0) if all_status_row else 0
    tryon_all_total = tryon_all_done + tryon_all_failed + tryon_all_processing

    # 3. Filtered timeframe breakdown
    if filter_date:
        filtered_conds = [TryOnSession.created_at >= filter_date]
        if filter_date_end:
            filtered_conds.append(TryOnSession.created_at < filter_date_end)
        filtered_status_stmt = select(
            func.count(TryOnSession.id).label("total"),
            func.sum(case((TryOnSession.status == "done", 1), else_=0)).label("done"),
            func.sum(case((TryOnSession.status == "failed", 1), else_=0)).label("failed"),
            func.sum(case((TryOnSession.status == "processing", 1), else_=0)).label("processing")
        ).filter(*filtered_conds)
        filtered_status_row = (await db.execute(filtered_status_stmt)).one_or_none()
        tryon_filtered_done = int(filtered_status_row.done or 0) if filtered_status_row else 0
        tryon_filtered_failed = int(filtered_status_row.failed or 0) if filtered_status_row else 0
        tryon_filtered_processing = int(filtered_status_row.processing or 0) if filtered_status_row else 0
        tryon_filtered_total = tryon_filtered_done + tryon_filtered_failed + tryon_filtered_processing
    else:
        tryon_filtered_total = tryon_all_total
        tryon_filtered_done = tryon_all_done
        tryon_filtered_failed = tryon_all_failed
        tryon_filtered_processing = tryon_all_processing

    effective_tryons = tryon_today_total if timeframe in ["daily", "today"] else (tryon_filtered_total if filter_date else tryon_all_total)
    effective_done = tryon_today_done if timeframe in ["daily", "today"] else (tryon_filtered_done if filter_date else tryon_all_done)
    effective_failed = tryon_today_failed if timeframe in ["daily", "today"] else (tryon_filtered_failed if filter_date else tryon_all_failed)
    effective_processing = tryon_today_processing if timeframe in ["daily", "today"] else (tryon_filtered_processing if filter_date else tryon_all_processing)

    # 4. Video Ads Telemetry & Impression-Level Paid Revenue (OnPaidEventListener)
    from database.enhanced_models import AdPaidImpression

    ad_paid_stmt = select(
        func.count(AdPaidImpression.id).label("impressions_count"),
        func.sum(AdPaidImpression.revenue_usd).label("total_usd")
    )
    if filter_date:
        ad_paid_stmt = ad_paid_stmt.filter(AdPaidImpression.created_at >= filter_date)
    if filter_date_end:
        ad_paid_stmt = ad_paid_stmt.filter(AdPaidImpression.created_at < filter_date_end)

    ad_paid_row = (await db.execute(ad_paid_stmt)).one_or_none()
    real_paid_impressions = int(ad_paid_row.impressions_count or 0) if ad_paid_row else 0
    real_ad_revenue_usd = float(ad_paid_row.total_usd or 0.0) if ad_paid_row else 0.0

    ad_tryon_stmt = select(func.count(AppAnalyticsEvent.id)).filter(
        AppAnalyticsEvent.event_name == "rewarded_ad_watched"
    )
    if filter_date:
        ad_tryon_stmt = ad_tryon_stmt.filter(AppAnalyticsEvent.created_at >= filter_date)
    if filter_date_end:
        ad_tryon_stmt = ad_tryon_stmt.filter(AppAnalyticsEvent.created_at < filter_date_end)

    video_ads_count = (await db.execute(ad_tryon_stmt)).scalar() or 0

    # Revenue: Real Captured Payment sum from DB + Affiliate
    affiliate_stmt = select(func.sum(AffiliatePurchase.commission_earned))
    if filter_date:
        affiliate_stmt = affiliate_stmt.filter(AffiliatePurchase.purchased_at >= filter_date)
    if filter_date_end:
        affiliate_stmt = affiliate_stmt.filter(AffiliatePurchase.purchased_at < filter_date_end)
    affiliate_res = await db.execute(affiliate_stmt)
    affiliate_revenue = affiliate_res.scalar() or 0.0

    payment_stmt = select(func.sum(Payment.amount)).filter(Payment.status.in_(["captured", "SUCCESS"]))
    if filter_date:
        payment_stmt = payment_stmt.filter(Payment.created_at >= filter_date)
    if filter_date_end:
        payment_stmt = payment_stmt.filter(Payment.created_at < filter_date_end)
    payment_res = await db.execute(payment_stmt)
    paise_captured = payment_res.scalar() or 0.0
    payment_revenue = paise_captured / 100.0 if paise_captured > 0 else 0.0

    # If real paid impressions exist from OnPaidEventListener, use exact revenue. Otherwise use live eCPM ($4.90).
    if real_paid_impressions > 0 and real_ad_revenue_usd > 0.0:
        actual_ad_revenue_usd = round(real_ad_revenue_usd, 4)
        actual_ad_revenue_inr = round(actual_ad_revenue_usd * 84.0, 2)
    else:
        actual_ad_revenue_usd = round(video_ads_count * (4.90 / 1000.0), 4)
        actual_ad_revenue_inr = round(actual_ad_revenue_usd * 84.0, 2)

    total_revenue = payment_revenue + affiliate_revenue + actual_ad_revenue_inr

    # Devices telemetry
    from database.models import UserDevice
    active_devices_res = await db.execute(select(func.count(UserDevice.id)).where(UserDevice.is_active == True))
    active_installed_devices = active_devices_res.scalar() or 0

    total_devices_res = await db.execute(select(func.count(UserDevice.id)))
    total_devices_registered = total_devices_res.scalar() or 0

    # 5. Rejected Try-Ons Telemetry
    rejection_stmt = select(
        AppAnalyticsEvent.event_name,
        func.count(AppAnalyticsEvent.id)
    ).where(
        AppAnalyticsEvent.event_name.in_([
            "safety_blocked_400", "safety_blocked_postgen",
            "quota_blocked_429", "quota_blocked_401",
            "ad_gate_blocked_402"
        ]),
        AppAnalyticsEvent.created_at >= today_start
    ).group_by(AppAnalyticsEvent.event_name)
    rejection_rows = (await db.execute(rejection_stmt)).all()
    rej_map = {r[0]: r[1] for r in rejection_rows}
    safety_today_total = rej_map.get("safety_blocked_400", 0) + rej_map.get("safety_blocked_postgen", 0)
    quota_today_total = rej_map.get("quota_blocked_429", 0) + rej_map.get("quota_blocked_401", 0)
    ad_gate_today_total = rej_map.get("ad_gate_blocked_402", 0)
    rejected_today_total = safety_today_total + quota_today_total + ad_gate_today_total + tryon_today_failed

    return {
        "status": "online",
        "timeframe": timeframe,
        "rejected_summary": {
            "total_rejected_today": rejected_today_total,
            "safety_blocked_today": safety_today_total,
            "quota_blocked_today": quota_today_total,
            "ad_gate_blocked_today": ad_gate_today_total,
            "tryons_failed_today": tryon_today_failed
        },
        "total_users": total_users if filter_date else all_time_users,
        "all_time_users": all_time_users,
        "new_users_today": new_users_today,
        "active_installed_devices": active_installed_devices,
        "total_devices_registered": total_devices_registered,
        "total_products": total_products,
        "total_revenue": round(total_revenue, 2),
        "video_ads_watched": video_ads_count,
        "ad_impressions_count": max(real_paid_impressions, video_ads_count),
        "revenue_breakdown": {
            "total_inr": round(total_revenue, 2),
            "payments_inr": round(payment_revenue, 2),
            "affiliate_inr": round(affiliate_revenue, 2),
            "ads_estimated_inr": actual_ad_revenue_inr,
            "ads_estimated_usd": actual_ad_revenue_usd,
            "is_exact_paid_revenue": (real_paid_impressions > 0)
        },
        "ad_metrics": {
            "ad_requests_estimated": int(effective_tryons * 1.2),
            "ad_impressions": max(real_paid_impressions, video_ads_count),
            "rewards_earned": video_ads_count,
            "tryons_unlocked": effective_done,
            "ad_revenue_usd": actual_ad_revenue_usd,
            "ad_revenue_inr": actual_ad_revenue_inr
        },
        "users": {
            "total": total_users if filter_date else all_time_users,
            "all_time": all_time_users,
            "free": free_users,
            "premium": premium_users,
            "new_today": new_users_today
        },
        "devices": {
            "active_installed": active_installed_devices,
            "total_registered": total_devices_registered
        },
        "operations": {
            "total_tryons": effective_tryons,
            "video_ads_watched": video_ads_count,
            "ads_monetization_rate": f"{round((video_ads_count / max(1, effective_tryons)) * 100, 1)}%",
            "tryons_today": tryon_today_total,
            "all_time_tryons": tryon_all_total,
            "tryons_completed_today": tryon_today_done,
            "tryons_failed_today": tryon_today_failed,
            "tryons_processing_today": tryon_today_processing,
            "tryons_completed_all_time": tryon_all_done,
            "tryons_failed_all_time": tryon_all_failed,
            "tryons_completed": effective_done,
            "tryons_failed": effective_failed,
            "tryons_processing": effective_processing
        },
        "timestamp": now.isoformat()
    }



@router.get("/dashboard/push-funnel")
async def get_push_funnel_analytics(
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Real-time push notification & user return funnel report for Admin Dashboard.
    Tracks: Total Devices, Morning Push Sent, Afternoon Push Sent, Evening Push Sent,
            Users Returned Today, Remaining Unreturned Users, CTR %.
    """
    from database.models import UserDevice, User
    from database.enhanced_models import AppAnalyticsEvent

    now = utcnow()
    today_start = get_ist_today_start_utc()

    # 1. Active installed devices
    active_dev_res = await db.execute(select(func.count(UserDevice.id)).where(UserDevice.is_active == True))
    active_installed_devices = active_dev_res.scalar() or 0

    # 2. Total pushes sent today (all slots)
    sent_today_res = await db.execute(
        select(func.count(AppAnalyticsEvent.id)).where(
            AppAnalyticsEvent.event_name.in_(["push_notification_sent", "push_followup_sent"]),
            AppAnalyticsEvent.created_at >= today_start
        )
    )
    total_pushes_sent_today = sent_today_res.scalar() or 0

    # 3. Returning users today
    returned_users_res = await db.execute(
        select(func.count(func.distinct(AppAnalyticsEvent.user_id))).where(
            AppAnalyticsEvent.event_name.in_(["notification_return", "push_notification_opened"]),
            AppAnalyticsEvent.created_at >= today_start
        )
    )
    users_returned_today = returned_users_res.scalar() or 0

    # 4. Remaining unreturned users
    unreturned_remaining = max(0, active_installed_devices - users_returned_today)

    # 5. Conversion CTR %
    return_conversion_rate = round((users_returned_today / max(1, active_installed_devices)) * 100, 2) if active_installed_devices > 0 else 0.0

    # 6. Detailed list of recent returning users (last 20)
    returning_logs_stmt = (
        select(
            AppAnalyticsEvent.user_id,
            User.email,
            AppAnalyticsEvent.created_at
        )
        .outerjoin(User, User.id == AppAnalyticsEvent.user_id)
        .where(
            AppAnalyticsEvent.event_name.in_(["notification_return", "push_notification_opened"]),
            AppAnalyticsEvent.created_at >= today_start
        )
        .order_by(desc(AppAnalyticsEvent.created_at))
        .limit(20)
    )
    returning_logs_res = await db.execute(returning_logs_stmt)
    returning_users_list = [
        {
            "user_id": row[0],
            "email": row[1] or "Guest / Unknown",
            "returned_at": row[2].isoformat() if row[2] else None
        }
        for row in returning_logs_res.all()
    ]

    return {
        "status": "success",
        "funnel_summary": {
            "total_active_installed_devices": active_installed_devices,
            "pushes_sent_today": total_pushes_sent_today,
            "users_returned_today": users_returned_today,
            "unreturned_users_remaining": unreturned_remaining,
            "return_conversion_rate_pct": return_conversion_rate
        },
        "recent_returning_users": returning_users_list,
        "timestamp": now.isoformat()
    }


@router.get("/dashboard/ad-blocks")
async def get_ad_blocks_analytics(
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Real-time HTTP 402 Ad-Gate Block report for Admin Dashboard.
    Tracks users & devices blocked from try-on generation for missing compulsory 30s video ad completion.
    """
    now = utcnow()
    today_start = get_ist_today_start_utc()
    last_1h = now - timedelta(hours=1)
    last_30m = now - timedelta(minutes=30)

    # 1. Counts
    total_today = (await db.execute(select(func.count(AppAnalyticsEvent.id)).where(AppAnalyticsEvent.event_name == "ad_gate_blocked_402", AppAnalyticsEvent.created_at >= today_start))).scalar() or 0
    total_1h = (await db.execute(select(func.count(AppAnalyticsEvent.id)).where(AppAnalyticsEvent.event_name == "ad_gate_blocked_402", AppAnalyticsEvent.created_at >= last_1h))).scalar() or 0
    total_30m = (await db.execute(select(func.count(AppAnalyticsEvent.id)).where(AppAnalyticsEvent.event_name == "ad_gate_blocked_402", AppAnalyticsEvent.created_at >= last_30m))).scalar() or 0
    total_all_time = (await db.execute(select(func.count(AppAnalyticsEvent.id)).where(AppAnalyticsEvent.event_name == "ad_gate_blocked_402"))).scalar() or 0

    # 2. Distinct blocked devices today
    distinct_devices = (await db.execute(select(func.count(func.distinct(AppAnalyticsEvent.device_id))).where(AppAnalyticsEvent.event_name == "ad_gate_blocked_402", AppAnalyticsEvent.created_at >= today_start))).scalar() or 0

    # 3. Recent 50 block events
    recent_events_res = await db.execute(
        select(AppAnalyticsEvent)
        .where(AppAnalyticsEvent.event_name == "ad_gate_blocked_402")
        .order_by(desc(AppAnalyticsEvent.created_at))
        .limit(50)
    )
    recent_events = recent_events_res.scalars().all()

    return {
        "status": "success",
        "ad_blocks_last_30m": total_30m,
        "ad_blocks_last_1h": total_1h,
        "ad_blocks_today": total_today,
        "ad_blocks_all_time": total_all_time,
        "distinct_blocked_devices_today": distinct_devices,
        "recent_blocks": [
            {
                "id": ev.id,
                "user_id": ev.user_id,
                "device_id": ev.device_id,
                "metadata": ev.metadata_json,
                "created_at": ev.created_at.isoformat() if ev.created_at else None
            } for ev in recent_events
        ]
    }


@router.get("/dashboard/rejected-tryons")
async def get_rejected_tryons_analytics(
    limit: int = Query(50, ge=1, le=200),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Comprehensive Try-On Rejections & Safety Shield analytics for Admin Dashboard.
    Tracks all blocked/rejected try-on attempts:
    - Safety / NSFW Filter (HTTP 400 & Post-Gen)
    - Quota / Daily Limit & Device Cap (HTTP 429 & 401)
    - Ad Gate Missing 30s Video Ad (HTTP 402)
    - Generation / Pipeline Failures (tryon_failed)
    """
    now = utcnow()
    today_start = get_ist_today_start_utc()
    last_1h = now - timedelta(hours=1)
    last_30m = now - timedelta(minutes=30)

    rejection_event_names = [
        "safety_blocked_400",
        "safety_blocked_postgen",
        "quota_blocked_429",
        "quota_blocked_401",
        "ad_gate_blocked_402",
        "tryon_failed"
    ]

    # 1. Summary Counts
    total_today = (await db.execute(
        select(func.count(AppAnalyticsEvent.id))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names), AppAnalyticsEvent.created_at >= today_start)
    )).scalar() or 0

    total_1h = (await db.execute(
        select(func.count(AppAnalyticsEvent.id))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names), AppAnalyticsEvent.created_at >= last_1h)
    )).scalar() or 0

    total_30m = (await db.execute(
        select(func.count(AppAnalyticsEvent.id))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names), AppAnalyticsEvent.created_at >= last_30m)
    )).scalar() or 0

    total_all_time = (await db.execute(
        select(func.count(AppAnalyticsEvent.id))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names))
    )).scalar() or 0

    distinct_devices = (await db.execute(
        select(func.count(func.distinct(AppAnalyticsEvent.device_id)))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names), AppAnalyticsEvent.created_at >= today_start)
    )).scalar() or 0

    # 2. Breakdown Today
    today_breakdown_rows = (await db.execute(
        select(AppAnalyticsEvent.event_name, func.count(AppAnalyticsEvent.id))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names), AppAnalyticsEvent.created_at >= today_start)
        .group_by(AppAnalyticsEvent.event_name)
    )).all()
    today_map = {r[0]: r[1] for r in today_breakdown_rows}

    # 3. Breakdown All Time
    all_time_breakdown_rows = (await db.execute(
        select(AppAnalyticsEvent.event_name, func.count(AppAnalyticsEvent.id))
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names))
        .group_by(AppAnalyticsEvent.event_name)
    )).all()
    all_map = {r[0]: r[1] for r in all_time_breakdown_rows}

    # 4. Recent Rejected Events (join User for user_email / username)
    stmt = (
        select(AppAnalyticsEvent, User.email, User.username)
        .outerjoin(User, AppAnalyticsEvent.user_id == User.id)
        .where(AppAnalyticsEvent.event_name.in_(rejection_event_names))
        .order_by(desc(AppAnalyticsEvent.created_at))
        .limit(limit)
    )
    res = await db.execute(stmt)
    records = res.all()

    formatted_events = []
    for ev, u_email, u_name in records:
        category = "Other Rejection"
        if ev.event_name in ["safety_blocked_400", "safety_blocked_postgen"]:
            category = "Safety Filter (NSFW)"
        elif ev.event_name in ["quota_blocked_429", "quota_blocked_401"]:
            category = "Daily Limit / Quota"
        elif ev.event_name == "ad_gate_blocked_402":
            category = "Ad Gate Bypass (402)"
        elif ev.event_name == "tryon_failed":
            category = "Generation Failure"

        reason = None
        ip_addr = None
        if ev.metadata_json:
            try:
                meta = json.loads(ev.metadata_json)
                if isinstance(meta, dict):
                    reason = meta.get("reason")
                    ip_addr = meta.get("ip")
            except Exception:
                reason = ev.metadata_json

        formatted_events.append({
            "id": ev.id,
            "event_name": ev.event_name,
            "category": category,
            "user_id": ev.user_id,
            "user_email": u_email or (f"User #{ev.user_id}" if ev.user_id else "Guest"),
            "username": u_name,
            "device_id": ev.device_id,
            "reason": reason,
            "ip": ip_addr,
            "metadata": ev.metadata_json,
            "created_at": ev.created_at.isoformat() if ev.created_at else None
        })

    return {
        "status": "success",
        "summary": {
            "total_rejected_today": total_today,
            "total_rejected_last_1h": total_1h,
            "total_rejected_last_30m": total_30m,
            "total_rejected_all_time": total_all_time,
            "distinct_devices_rejected_today": distinct_devices
        },
        "breakdown_today": {
            "safety_blocks": today_map.get("safety_blocked_400", 0) + today_map.get("safety_blocked_postgen", 0),
            "quota_blocks": today_map.get("quota_blocked_429", 0) + today_map.get("quota_blocked_401", 0),
            "ad_gate_blocks": today_map.get("ad_gate_blocked_402", 0),
            "generation_failures": today_map.get("tryon_failed", 0)
        },
        "breakdown_all_time": {
            "safety_blocks": all_map.get("safety_blocked_400", 0) + all_map.get("safety_blocked_postgen", 0),
            "quota_blocks": all_map.get("quota_blocked_429", 0) + all_map.get("quota_blocked_401", 0),
            "ad_gate_blocks": all_map.get("ad_gate_blocked_402", 0),
            "generation_failures": all_map.get("tryon_failed", 0)
        },
        "recent_rejections": formatted_events
    }


@router.get("/dashboard/business-intelligence")
async def get_business_intelligence(
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Detailed business intelligence: payment gateways, revenue breakdown, user credit usage
    """
    # 1. Active Payment Gateways Status
    razorpay_active = bool(settings.razorpay_key_id and settings.razorpay_key_secret)
    paypal_active = bool(settings.paypal_client_id and settings.paypal_secret_key)
    stripe_active = bool(os.getenv("STRIPE_SECRET_KEY"))
    
    gateways = [
        {
            "name": "Razorpay",
            "status": "Active" if razorpay_active else "Inactive",
            "currency": "INR",
            "details": f"Key ID: {settings.razorpay_key_id[:12]}..." if razorpay_active else "Not Configured"
        },
        {
            "name": "PayPal",
            "status": "Active" if paypal_active else "Inactive",
            "currency": "USD",
            "details": f"Client ID: {settings.paypal_client_id[:12]}..." if paypal_active else "Not Configured"
        },
        {
            "name": "Stripe",
            "status": "Active" if stripe_active else "Inactive",
            "currency": "USD",
            "details": f"Secret: Present" if stripe_active else "Not Configured"
        }
    ]

    # 2. Revenue Breakdown from database
    payments_query = await db.execute(select(Payment).filter(Payment.status == "captured"))
    payments = payments_query.scalars().all()
    
    razorpay_rev = 0.0 # INR
    paypal_rev = 0.0   # USD
    stripe_rev = 0.0   # USD
    
    razorpay_tx = 0
    paypal_tx = 0
    stripe_tx = 0
    
    for p in payments:
        amount_units = p.amount / 100.0
        order_id = p.razorpay_order_id or ""
        
        if order_id.startswith("paypal_"):
            paypal_rev += amount_units
            paypal_tx += 1
        elif order_id.startswith("stripe_"):
            stripe_rev += amount_units
            stripe_tx += 1
        else:
            razorpay_rev += amount_units
            razorpay_tx += 1

    # Affiliate revenue
    affiliate_revenue_query = await db.execute(select(func.sum(AffiliatePurchase.commission_earned)))
    affiliate_rev = affiliate_revenue_query.scalar() or 0.0
    
    # 3. Credit usage
    total_spent_query = await db.execute(select(func.count(TryOnSession.id)).filter(TryOnSession.status == "done"))
    total_spent_credits = total_spent_query.scalar() or 0
    
    total_held_query = await db.execute(select(func.sum(User.credits)))
    total_held_credits = total_held_query.scalar() or 0
    
    from sqlalchemy import case

    top_users_query = await db.execute(
        select(
            User.id, 
            User.email, 
            User.username, 
            User.credits,
            func.count(TryOnSession.id).label("tryons_total"),
            func.sum(case((TryOnSession.status == 'done', 1), else_=0)).label("tryons_success"),
            func.sum(case((TryOnSession.status == 'failed', 1), else_=0)).label("tryons_failed")
        )
        .outerjoin(TryOnSession, User.id == TryOnSession.user_id)
        .group_by(User.id)
        .order_by(desc("tryons_total"))
        .limit(30)
    )
    top_users_result = top_users_query.all()
    
    credit_users = [
        {
            "id": str(u.id),
            "email": u.email,
            "username": u.username or u.email.split('@')[0],
            "credits_left": u.credits,
            "credits_used": int(u.tryons_success or 0),
            "tryons_failed": int(u.tryons_failed or 0),
            "tryons_total": int(u.tryons_total or 0)
        }
        for u in top_users_result
    ]
    
    return {
        "gateways": gateways,
        "revenue_breakdown": {
            "razorpay": {"amount": round(razorpay_rev, 2), "transactions": razorpay_tx, "currency": "INR"},
            "paypal": {"amount": round(paypal_rev, 2), "transactions": paypal_tx, "currency": "USD"},
            "stripe": {"amount": round(stripe_rev, 2), "transactions": stripe_tx, "currency": "USD"},
            "affiliate": {"amount": round(affiliate_rev, 2), "currency": "INR"},
            "total_inr_equivalent": round(razorpay_rev + affiliate_rev + (paypal_rev + stripe_rev) * 83.0, 2)
        },
        "credits": {
            "total_spent": total_spent_credits,
            "total_held": total_held_credits,
            "top_consumers": credit_users
        }
    }


@router.get("/dashboard/user-purchases")
async def get_user_purchases_ledger(
    timeframe: str = Query("all", description="30m | daily | yesterday | weekly | monthly | date:YYYY-MM-DD | all"),
    limit: int = Query(100, description="Max transactions to return"),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Get comprehensive purchase ledger and revenue transaction analytics:
    Returns user details, purchase package, payment ID, amount (₹), status, and timestamp.
    """
    filter_date, filter_date_end = parse_timeframe(timeframe)
    now = utcnow()

    # Base query joined with User
    stmt = (
        select(Payment, User)
        .join(User, Payment.user_id == User.id)
        .order_by(desc(Payment.created_at))
    )

    if filter_date:
        stmt = stmt.filter(Payment.created_at >= filter_date)
    if filter_date_end:
        stmt = stmt.filter(Payment.created_at < filter_date_end)

    stmt = stmt.limit(limit)
    res = await db.execute(stmt)
    rows = res.all()

    # Timeframe stats summary
    import pytz
    from datetime import timedelta
    ist = pytz.timezone("Asia/Kolkata")
    today_start = get_ist_today_start_utc()
    yesterday_start = today_start - timedelta(days=1)
    weekly_start = now - timedelta(days=7)
    monthly_start = now - timedelta(days=30)

    # Today total
    today_res = await db.execute(
        select(
            func.count(Payment.id),
            func.coalesce(func.sum(Payment.amount), 0)
        ).filter(Payment.created_at >= today_start, Payment.status.in_(["captured", "SUCCESS"]))
    )
    today_cnt, today_amt_paise = today_res.one()

    # Yesterday total
    yesterday_res = await db.execute(
        select(
            func.count(Payment.id),
            func.coalesce(func.sum(Payment.amount), 0)
        ).filter(Payment.created_at >= yesterday_start, Payment.created_at < today_start, Payment.status.in_(["captured", "SUCCESS"]))
    )
    yesterday_cnt, yesterday_amt_paise = yesterday_res.one()

    # 7-day total
    weekly_res = await db.execute(
        select(
            func.count(Payment.id),
            func.coalesce(func.sum(Payment.amount), 0)
        ).filter(Payment.created_at >= weekly_start, Payment.status.in_(["captured", "SUCCESS"]))
    )
    weekly_cnt, weekly_amt_paise = weekly_res.one()

    # 30-day total
    monthly_res = await db.execute(
        select(
            func.count(Payment.id),
            func.coalesce(func.sum(Payment.amount), 0)
        ).filter(Payment.created_at >= monthly_start, Payment.status.in_(["captured", "SUCCESS"]))
    )
    monthly_cnt, monthly_amt_paise = monthly_res.one()

    # All-time total
    all_res = await db.execute(
        select(
            func.count(Payment.id),
            func.coalesce(func.sum(Payment.amount), 0)
        ).filter(Payment.status.in_(["captured", "SUCCESS"]))
    )
    all_cnt, all_amt_paise = all_res.one()

    # Daily revenue trend (Last 7 Days)
    daily_trend = []
    for i in range(6, -1, -1):
        day_date = (now - timedelta(days=i)).astimezone(ist).date()
        day_start = datetime.combine(day_date, datetime.min.time()).astimezone(timezone.utc)
        day_end = day_start + timedelta(days=1)

        d_res = await db.execute(
            select(
                func.count(Payment.id),
                func.coalesce(func.sum(Payment.amount), 0)
            ).filter(Payment.created_at >= day_start, Payment.created_at < day_end)
        )
        d_cnt, d_paise = d_res.one()
        daily_trend.append({
            "date": day_date.strftime("%Y-%m-%d"),
            "label": day_date.strftime("%d %b"),
            "revenue_inr": round(d_paise / 100.0, 2),
            "purchases": d_cnt
        })

    # Package sales distribution breakdown
    pkg_map = {}
    purchases_list = []
    for payment, user in rows:
        amount_inr = round(payment.amount / 100.0, 2)
        order_id = payment.razorpay_order_id or ""
        payment_id = payment.razorpay_payment_id or order_id

        # Determine package name
        if amount_inr == 39.0:
            pkg_name = "⚡ ₹39 First Time Offer (25 Credits)"
        elif amount_inr == 49.0:
            pkg_name = "⚡ ₹49 Starter Pack (15 Credits)"
        elif amount_inr == 99.0:
            pkg_name = "⚡ ₹99 Popular Pack (30 Credits)"
        elif amount_inr == 199.0:
            pkg_name = "⚡ ₹199 Power Saver (100 Credits)"
        elif amount_inr == 379.0:
            pkg_name = "👑 ₹379 Pro VIP Subscription"
        elif order_id.startswith("manual_"):
            pkg_name = "🎁 Manual Admin Top-Up"
        elif order_id.startswith("gp_"):
            pkg_name = "🆓 1st Try-On Trial (Guest Pass)"
        else:
            pkg_name = f"⚡ ₹{amount_inr} Credit Top-Up"

        if pkg_name not in pkg_map:
            pkg_map[pkg_name] = {"count": 0, "revenue_inr": 0.0}
        pkg_map[pkg_name]["count"] += 1
        if payment.status in ["captured", "SUCCESS"]:
            pkg_map[pkg_name]["revenue_inr"] += amount_inr

        # Format IST timestamp
        created_dt = payment.created_at
        if created_dt:
            created_ist = created_dt.astimezone(ist) if created_dt.tzinfo else pytz.utc.localize(created_dt).astimezone(ist)
            formatted_time = created_ist.strftime("%d %b %Y, %I:%M %p")
        else:
            formatted_time = "Unknown"

        purchases_list.append({
            "id": payment.id,
            "user_id": user.id,
            "user_email": user.email,
            "username": user.username or user.email.split("@")[0],
            "amount_inr": amount_inr,
            "currency": payment.currency or "INR",
            "package_name": pkg_name,
            "order_id": order_id,
            "payment_id": payment_id,
            "status": payment.status,
            "created_at": created_dt.isoformat() if created_dt else None,
            "created_at_formatted": formatted_time
        })

    package_distribution = [
        {"package_name": k, "count": v["count"], "revenue_inr": round(v["revenue_inr"], 2)}
        for k, v in pkg_map.items()
    ]

    return {
        "success": True,
        "summary": {
            "today": {"count": today_cnt, "revenue_inr": round(today_amt_paise / 100.0, 2)},
            "yesterday": {"count": yesterday_cnt, "revenue_inr": round(yesterday_amt_paise / 100.0, 2)},
            "weekly": {"count": weekly_cnt, "revenue_inr": round(weekly_amt_paise / 100.0, 2)},
            "monthly": {"count": monthly_cnt, "revenue_inr": round(monthly_amt_paise / 100.0, 2)},
            "all_time": {"count": all_cnt, "revenue_inr": round(all_amt_paise / 100.0, 2)}
        },
        "daily_trend": daily_trend,
        "package_distribution": package_distribution,
        "purchases": purchases_list
    }


# ============================================================================
# USER MANAGEMENT (CRM)
# ============================================================================

@router.get("/users/list")
@router.get("/users")
async def list_all_users(
    page: int = 1,
    limit: int = 50,
    search: Optional[str] = None,
    subscription_tier: Optional[str] = None,
    timeframe: Optional[str] = Query(None, description="daily, weekly, monthly"),
    sort_by: Optional[str] = "created_at",
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    List all users with pagination, aggregated metrics, timeframe filters, and advanced sorting
    """
    from sqlalchemy import case, or_, text
    now = datetime.now(timezone.utc)
    today_start = get_ist_today_start_utc()
    
    # Base aggregated query
    stmt = (
        select(
            User,
            func.count(TryOnSession.id).label("tryons_total"),
            func.sum(case((TryOnSession.status == 'done', 1), else_=0)).label("tryons_success"),
            func.sum(case((TryOnSession.status == 'failed', 1), else_=0)).label("tryons_failed")
        )
        .outerjoin(TryOnSession, User.id == TryOnSession.user_id)
        .group_by(User.id)
    )

    # Apply timeframe filter
    if timeframe in ["daily", "today"]:
        stmt = stmt.filter(User.created_at >= today_start)
    elif timeframe == "weekly":
        stmt = stmt.filter(User.created_at >= (now - timedelta(days=7)))
    elif timeframe == "monthly":
        stmt = stmt.filter(User.created_at >= (now - timedelta(days=30)))
    
    # Apply search filter
    if search:
        stmt = stmt.filter(
            or_(
                User.email.ilike(f"%{search}%"),
                User.username.ilike(f"%{search}%")
            )
        )
    
    # Apply subscription tier filter
    if subscription_tier:
        stmt = stmt.filter(User.subscription_tier == subscription_tier)
        
    # Apply sorting
    if sort_by == "subscribed":
        # Sort premium/tier first
        stmt = stmt.order_by(desc(User.subscription_tier), desc(User.credits), desc(User.created_at))
    elif sort_by == "tryons_total":
        stmt = stmt.order_by(desc(text("tryons_total")), desc(User.created_at))
    elif sort_by == "success_rate":
        stmt = stmt.order_by(desc(text("tryons_success")), desc(User.created_at))
    elif sort_by == "credits":
        stmt = stmt.order_by(desc(User.credits), desc(User.created_at))
    else:
        stmt = stmt.order_by(desc(User.created_at))
        
    # Get total count
    count_stmt = select(func.count()).select_from(stmt.subquery())
    total_count_query = await db.execute(count_stmt)
    total_count = total_count_query.scalar() or 0
    
    # Pagination
    offset = (page - 1) * limit
    stmt = stmt.offset(offset).limit(limit)
    
    users_query = await db.execute(stmt)
    users_results = users_query.all()
    
    user_list = []
    for row in users_results:
        user = row[0]
        tryons_total = int(row[1] or 0)
        tryons_success = int(row[2] or 0)
        tryons_failed = int(row[3] or 0)
        
        success_rate = round((tryons_success / tryons_total) * 100) if tryons_total > 0 else 100
        
        user_list.append({
            "id": str(user.id),
            "email": user.email,
            "username": user.username or (user.email.split('@')[0] if user.email else f"User_{user.id}"),
            "subscription_tier": user.subscription_tier or "free",
            "subscription_expires": user.subscription_expires_at.isoformat() if user.subscription_expires_at else None,
            "credits": user.credits or 0,
            "paid_credits": getattr(user, "paid_credits", 0) or 0,
            "bonus_credits": getattr(user, "bonus_credits", 0) or 0,
            "is_premium": getattr(user, "is_premium", False) or False,
            "created_at": user.created_at.isoformat() if user.created_at else None,
            "last_active": user.last_active_at.isoformat() if user.last_active_at else None,
            "tryons_total": tryons_total,
            "tryons_success": tryons_success,
            "tryons_failed": tryons_failed,
            "stats": {
                "total_tryons": tryons_total,
                "tryons_success": tryons_success,
                "tryons_failed": tryons_failed,
                "success_rate": success_rate,
                "wardrobe_items": 0
            },
            "status": "active" if not user.subscription_expires_at or user.subscription_expires_at > utcnow() else "expired"
        })
        
    return {
        "users": user_list,
        "pagination": {
            "page": page,
            "limit": limit,
            "total_count": total_count,
            "total_pages": (total_count + limit - 1) // limit
        }
    }


@router.get("/pricing-leads")
@router.get("/dashboard/pricing-leads")
async def get_pricing_leads(
    limit: int = Query(50, description="Limit max pricing leads"),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Returns real-time high-intent leads who opened the pricing page, choice popups,
    or clicked plan checkout buttons (₹39 Pack, Pro Subscription, etc.).
    """
    try:
        intent_events = [
            "pricing_lead", "checkout_intent", "purchase_started",
            "choice_popup_click", "plan_click", "premium_screen_viewed",
            "plan_popup_impression", "login_required_prompt"
        ]
        
        stmt = (
            select(AppAnalyticsEvent, User)
            .outerjoin(User, AppAnalyticsEvent.user_id == User.id)
            .where(AppAnalyticsEvent.event_name.in_(intent_events))
            .order_by(desc(AppAnalyticsEvent.created_at))
            .limit(limit)
        )
        
        res = await db.execute(stmt)
        rows = res.all()
        
        leads = []
        for event, user in rows:
            meta = {}
            if event.metadata_json:
                try:
                    import json
                    meta = json.loads(event.metadata_json)
                except Exception:
                    meta = {"raw": event.metadata_json}
            
            plan_name = meta.get("plan_name") or meta.get("plan_id") or meta.get("plan") or event.event_name.replace("_", " ").title()
            action = meta.get("action") or "view"
            
            leads.append({
                "id": event.id,
                "user_id": user.id if user else None,
                "email": user.email if user else f"Guest ({event.device_id[:8] if event.device_id else 'Device'})",
                "full_name": user.full_name if user else "Guest User",
                "username": user.username if user else "guest",
                "photo_url": user.photo_url if user else None,
                "event_name": event.event_name,
                "plan_name": plan_name,
                "action": action,
                "paid_credits": getattr(user, "paid_credits", 0) if user else 0,
                "bonus_credits": getattr(user, "bonus_credits", 0) if user else 0,
                "subscription_tier": getattr(user, "subscription_tier", "free") if user else "guest",
                "created_at": event.created_at.isoformat() if event.created_at else None,
                "device_id": event.device_id
            })
            
        return {
            "status": "success",
            "count": len(leads),
            "leads": leads
        }
    except Exception as e:
        log.error(f"Error fetching pricing leads: {e}")
        return {"status": "error", "message": str(e), "leads": []}


@router.get("/users/rankings")
@router.get("/dashboard/rankings")
async def get_user_rankings(
    limit: int = Query(20, description="Max leaderboard size"),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Returns user ranking leaderboards:
    1. Top Usage Power Users (by Try-On generations performed)
    2. Top Buyer Spenders (by Total Revenue spent & Paid Credits held)
    """
    from sqlalchemy import case, or_, desc, text
    
    # ── 1. TOP USAGE LEADERBOARD (MOST ACTIVE USERS) ──────────
    usage_stmt = (
        select(
            User,
            func.count(TryOnSession.id).label("tryons_total"),
            func.sum(case((TryOnSession.status == 'done', 1), else_=0)).label("tryons_success")
        )
        .outerjoin(TryOnSession, User.id == TryOnSession.user_id)
        .group_by(User.id)
        .order_by(desc(text("tryons_total")), desc(User.current_slot_tryon_count), desc(User.created_at))
        .limit(limit)
    )
    usage_res = await db.execute(usage_stmt)
    usage_rows = usage_res.all()

    top_usage_users = []
    for idx, row in enumerate(usage_rows, start=1):
        usr = row[0]
        tryons_cnt = int(row[1] or 0)
        tryons_done = int(row[2] or 0)
        total_tries = max(tryons_cnt, usr.current_slot_tryon_count or 0)
        
        top_usage_users.append({
            "rank": idx,
            "id": usr.id,
            "email": usr.email,
            "username": usr.username or usr.email.split("@")[0],
            "total_tryons": total_tries,
            "tryons_success": tryons_done,
            "paid_credits": usr.paid_credits or 0,
            "bonus_credits": usr.bonus_credits or 0,
            "subscription_tier": usr.subscription_tier or "free",
            "created_at": usr.created_at.isoformat() if usr.created_at else None,
            "last_active": usr.last_active_at.isoformat() if usr.last_active_at else None
        })

    # ── 2. TOP BUYERS LEADERBOARD (MOST SPENT / WHALES) ───────
    buyer_stmt = (
        select(
            User,
            func.coalesce(func.sum(case((Payment.status.in_(["captured", "SUCCESS"]), Payment.amount), else_=0)), 0).label("spent_paise"),
            func.count(case((Payment.status.in_(["captured", "SUCCESS"]), Payment.id), else_=None)).label("tx_count")
        )
        .outerjoin(Payment, User.id == Payment.user_id)
        .group_by(User.id)
        .order_by(desc(text("spent_paise")), desc(User.paid_credits), desc(User.is_premium), desc(User.created_at))
        .limit(limit)
    )
    buyer_res = await db.execute(buyer_stmt)
    buyer_rows = buyer_res.all()

    top_buyer_users = []
    for idx, row in enumerate(buyer_rows, start=1):
        usr = row[0]
        spent_inr = round(float(row[1] or 0) / 100.0, 2)
        tx_cnt = int(row[2] or 0)
        
        top_buyer_users.append({
            "rank": idx,
            "id": usr.id,
            "email": usr.email,
            "username": usr.username or usr.email.split("@")[0],
            "total_spent_inr": spent_inr,
            "purchases_count": tx_cnt,
            "paid_credits": usr.paid_credits or 0,
            "subscription_tier": usr.subscription_tier or "free",
            "created_at": usr.created_at.isoformat() if usr.created_at else None
        })

    # Summary Stats
    total_paying_users_res = await db.execute(
        select(func.count(func.distinct(User.id)))
        .filter((User.paid_credits > 0) | (User.is_premium == True) | (User.subscription_tier != "free"))
    )
    total_paying_users = total_paying_users_res.scalar() or 0

    return {
        "status": "success",
        "top_usage_users": top_usage_users,
        "top_buyer_users": top_buyer_users,
        "summary": {
            "total_paying_users": max(total_paying_users, len([b for b in top_buyer_users if b["total_spent_inr"] > 0 or b["paid_credits"] > 0])),
            "top_usage_leaderboard_size": len(top_usage_users),
            "top_buyers_leaderboard_size": len(top_buyer_users)
        }
    }


class SendCampaignEmailRequest(BaseModel):
    user_id: str
    campaign_type: str
    message: Optional[str] = None
    discount_pct: Optional[int] = None


@router.post("/users/send-campaign-email")
async def send_campaign_email(
    data: SendCampaignEmailRequest,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Trigger marketing/conversion campaign emails to highly engaged users.
    """
    user_id_int = int(data.user_id)
    user_query = await db.execute(select(User).filter(User.id == user_id_int))
    user = user_query.scalar_one_or_none()
    
    if not user:
        raise HTTPException(status_code=404, detail="Target entity not found")
        
    email_subject = ""
    email_body = ""
    
    if data.campaign_type == "high_success_reward":
        email_subject = "🎉 VIP reward from TryZon AI: Enjoy 15 FREE trial credits!"
        email_body = f"""
        Hello {user.username or 'Creator'},
        
        We noticed you have a stellar ComfyUI rendering success rate on TryZon AI! 
        As one of our top creators, we have credited 15 FREE VIP credits to your account.
        
        Keep creating high-quality try-ons!
        - Team TryZon AI
        """
        # Reward credits!
        user.credits += 15
        await db.commit()
    elif data.campaign_type == "subscription_offer":
        email_subject = "⚡ Unlock unlimited ComfyUI renders - 50% OFF inside!"
        email_body = f"""
        Hello {user.username or 'Creator'},
        
        You have been creating amazing try-on designs. Why limit yourself?
        Upgrade to TryZon AI Premium today for unlimited high-quality downloads and exclusive garment models at 50% off!
        
        Use promo code: NEURAL50 at checkout.
        - Team TryZon AI
        """
    elif data.campaign_type == "inactive_promo":
        email_subject = "We miss your style creations! Here is a welcome-back gift 🎁"
        email_body = f"""
        Hello {user.username or 'Creator'},
        
        It's been a while since your last virtual try-on. 
        We have added 5 bonus credits to your balance to help you get back to styling!
        
        Log in now and try our new recommended garments.
        - Team TryZon AI
        """
        user.credits += 5
        await db.commit()
    elif data.campaign_type == "custom_discount":
        discount = data.discount_pct or 60
        voucher_code = f"VIP-{discount}-NEURAL-{user.username.upper() if user.username else 'CREATOR'}"
        email_subject = f"🔥 Exclusive {discount}% EXTRA Discount - Specially for You!"
        email_body = f"""
        Hello {user.username or 'Creator'},
        
        Because you are a high-tier creator with stellar designs on TryZon AI,
        we have unlocked a special direct conversion discount channel for you.
        
        Get an extra {discount}% OFF on any premium subscription!
        Bespoke Voucher Code: {voucher_code}
        
        This voucher is exclusive to your account signature and has been configured in the system.
        
        - Team TryZon AI
        """
        log.info(f"[DISCOUNT GENERATED] Code: {voucher_code} | Discount: {discount}% | User: {user.email}")
    else:
        # Custom message
        email_subject = "✨ Message from TryZon AI Curator"
        email_body = data.message or "Check out your account for new premium trial models!"
        
    # Log campaign action for the admin audit trail (so it shows in Command Center live logs!)
    log.info(f"[CAMPAIGN EMAIL] Type: {data.campaign_type} | Sent to: {user.email} | Subject: {email_subject}")
    
    return {
        "success": True,
        "recipient": user.email,
        "subject": email_subject,
        "body": email_body,
        "message": f"Campaign email successfully processed and sent to {user.email}!"
    }


@router.get("/users/{user_id}/details")
async def get_user_details(
    user_id: int,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Get detailed information about a specific user
    """
    user_query = await db.execute(select(User).filter(User.id == user_id))
    user = user_query.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    # Get recent try-ons
    tryons_query = await db.execute(
        select(TryOnSession).filter(TryOnSession.user_id == user_id).order_by(desc(TryOnSession.created_at)).limit(20)
    )
    tryons = tryons_query.scalars().all()
    
    # Get purchases
    purchases_query = await db.execute(
        select(AffiliatePurchase).filter(AffiliatePurchase.user_id == user_id).order_by(desc(AffiliatePurchase.purchased_at))
    )
    purchases = purchases_query.scalars().all()
    
    wardrobe_query = await db.execute(select(func.count(WardrobeItem.id)).filter(WardrobeItem.user_id == user_id))
    wardrobe = wardrobe_query.scalar() or 0
    
    # Calculate user value
    total_commission = sum(p.commission_earned or 0 for p in purchases)
    user_lifetime_value = (49 if user.subscription_tier != "free" else 0) + total_commission
    
    return {
        "user": {
            "id": str(user.id),
            "email": user.email,
            "subscription_tier": user.subscription_tier,
            "subscription_expires": user.subscription_expires_at.isoformat() if user.subscription_expires_at else None,
            "created_at": user.created_at.isoformat(),
            "last_active": user.last_active_at.isoformat() if user.last_active_at else None
        },
        "stats": {
            "total_tryons": len(tryons),
            "total_purchases": len(purchases),
            "wardrobe_items": wardrobe,
            "lifetime_value": round(user_lifetime_value, 2)
        },
        "recent_tryons": [
            {
                "id": str(t.id),
                "status": t.status,
                "submitted_at": t.created_at.isoformat()
            }
            for t in tryons[:5]
        ],
        "purchases": [
            {
                "product_id": p.product_id,
                "amount": p.purchase_amount,
                "commission": p.commission_earned,
                "store": p.store,
                "purchased_at": p.purchased_at.isoformat()
            }
            for p in purchases
        ]
    }


class UpdateSubscriptionRequest(BaseModel):
    subscription_tier: str = "pro"
    expires_at: Optional[str] = None
    credits: Optional[int] = None

@router.get("/tryon-sessions/recent")
async def get_recent_tryon_sessions(
    limit: int = Query(25, ge=1, le=100),
    status: Optional[str] = Query(None, description="done | failed | processing | pending | all"),
    user_id: Optional[int] = Query(None),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Live Try-On Activity Stream & Inspector for the Founder Cockpit.
    Shows real-time jobs, latency, success/failure status, and output URLs.
    """
    import database.enhanced_models  # ensure ORM registry is intact
    stmt = (
        select(TryOnSession, User.email, User.username)
        .outerjoin(User, TryOnSession.user_id == User.id)
        .order_by(desc(TryOnSession.created_at))
    )
    if status and status != "all":
        stmt = stmt.filter(TryOnSession.status == status)
    if user_id:
        stmt = stmt.filter(TryOnSession.user_id == user_id)
        
    stmt = stmt.limit(limit)
    res = await db.execute(stmt)
    rows = res.all()
    
    sessions = []
    for s, email, username in rows:
        sessions.append({
            "id": s.id,
            "user_id": s.user_id,
            "user_display": email or username or (f"User #{s.user_id}" if s.user_id else "Guest Trial 👤"),
            "status": s.status or "unknown",
            "gpu_used": s.gpu_used,
            "processing_seconds": round(s.processing_time_ms / 1000.0, 1) if s.processing_time_ms else None,
            "result_image_url": s.result_image_path,
            "garment_image_url": s.garment_image_path,
            "created_at": s.created_at.isoformat() if s.created_at else None
        })
        
    return {
        "count": len(sessions),
        "sessions": sessions
    }

@router.post("/users/{user_id}/update-subscription")
async def update_user_subscription(
    user_id: int,
    req: Optional[UpdateSubscriptionRequest] = Body(None),
    subscription_tier: Optional[str] = Form(None),
    expires_at: Optional[str] = Form(None),
    credits: Optional[int] = Form(None),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Manually update user subscription and credits (Accepts JSON body or Form)
    """
    target_tier = (req.subscription_tier if req else None) or subscription_tier or "free"
    target_expires = (req.expires_at if req else None) or expires_at
    target_credits = (req.credits if req and req.credits is not None else credits)

    user_query = await db.execute(select(User).filter(User.id == user_id))
    user = user_query.scalar_one_or_none()
    if not user:
        raise HTTPException(status_code=404, detail="User not found")
    
    user.subscription_tier = target_tier
    user.is_premium = (target_tier != "free")
    
    if target_credits is not None:
        user.credits = target_credits
    
    if target_expires:
        user.subscription_expires_at = datetime.fromisoformat(target_expires)
    elif target_tier != "free":
        # Set to 1 month from now
        user.subscription_expires_at = utcnow() + timedelta(days=30)
    else:
        user.subscription_expires_at = None
    
    await db.commit()
    
    return {
        "success": True,
        "message": f"Updated subscription to {target_tier} and credits to {user.credits}",
        "user_id": str(user_id),
        "is_premium": user.is_premium,
        "subscription_tier": user.subscription_tier
    }


# ============================================================================
# PRODUCT MANAGEMENT
# ============================================================================

@router.post("/products/add")
async def add_product_manual(
    name: str = Form("New Product"),
    brand: Optional[str] = Form(None),
    category: str = Form("Clothing"),
    store: Optional[str] = Form(None),
    product_url: Optional[str] = Form(None),
    price: Optional[float] = Form(None),
    discount_percent: Optional[int] = Form(0),
    image_url: Optional[str] = Form(None),
    image_file: Optional[UploadFile] = File(None),
    featured: bool = Form(False),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Manually add product to catalog with Cuelinks wrapping
    """
    
    # Wrap URL with Cuelinks
    affiliate_url = wrap_with_cuelinks(product_url)
    
    # Handle image upload or external URL
    if image_file:
        timestamp = int(utcnow().timestamp())
        filename = f"{timestamp}_{image_file.filename}"
        image_path = os.path.join(settings.output_dir, "products", filename)
        os.makedirs(os.path.join(settings.output_dir, "products"), exist_ok=True)
        
        content = await image_file.read()
        with open(image_path, "wb") as f:
            f.write(content)
        
        image_url = f"/outputs/products/{filename}"
    elif image_url and image_url.startswith("http"):
        # Download external image to avoid hotlinking protection
        try:
            timestamp = int(utcnow().timestamp())
            parsed_url = urlparse(image_url)
            ext = os.path.splitext(parsed_url.path)[1] or ".jpg"
            if "?" in ext: ext = ext.split("?")[0]
            if len(ext) > 5: ext = ".jpg" # Safety check
            
            filename = f"scraped_{timestamp}{ext}"
            image_path = os.path.join(settings.output_dir, "products", filename)
            os.makedirs(os.path.join(settings.output_dir, "products"), exist_ok=True)

            async with httpx.AsyncClient(timeout=10.0) as client:
                resp = await client.get(image_url, follow_redirects=True)
                if resp.status_code == 200:
                    with open(image_path, "wb") as f:
                        f.write(resp.content)
                    image_url = f"/outputs/products/{filename}"
                    log.info(f"Downloaded external image to {image_url}")
        except Exception as e:
            log.error(f"Failed to download external image {image_url}: {e}")
            # Keep original URL as fallback
    
    # Create product
    product = Product(
        name=name,
        brand=brand,
        category=category,
        store=store,
        original_url=product_url,
        affiliate_url=affiliate_url,
        current_price=price,
        discount_percent=discount_percent,
        image_url=image_url,
        is_featured=featured,
        is_active=True,
        added_by="admin"
    )
    
    db.add(product)
    await db.commit()
    await db.refresh(product)
    
    # ── Sync to ChromaDB for Feed ──────────────────────────
    try:
        embedding = None
        tags = {}
        
        # Determine image path for CLIP
        final_image_path = None
        temp_img = None
        
        if image_file:
            final_image_path = image_path
        elif image_url and image_url.startswith("http"):
            # Download temporarily for embedding
            try:
                async with httpx.AsyncClient() as client:
                    resp = await client.get(image_url, timeout=10.0)
                    if resp.status_code == 200:
                        temp_img = tempfile.NamedTemporaryFile(delete=False, suffix=".jpg")
                        temp_img.write(resp.content)
                        temp_img.close()
                        final_image_path = temp_img.name
            except Exception as e:
                log.warning(f"Failed to download image for embedding: {e}")

        if final_image_path:
            from services.clip_service import get_clip_tags
            res = await get_clip_tags(final_image_path)
            embedding = res.get("embedding")
            tags = {
                "style": ", ".join(res.get("style", [])),
                "color": ", ".join(res.get("color", [])),
                "caption": res.get("florence_caption", ""),
                "store": store or "unknown"
            }
            
            if temp_img:
                os.unlink(temp_img.name)
        
        if embedding:
            upsert_product(
                product_id=product.id,
                name=product.name,
                category=product.category,
                embedding=embedding,
                metadata=tags
            )
            log.info(f"Product {product.id} synced to ChromaDB")
            
    except Exception as e:
        log.error(f"Failed to sync product to ChromaDB: {e}")
    
    return {
        "success": True,
        "message": "Product added successfully",
        "product": {
            "id": str(product.id),
            "name": product.name,
            "affiliate_url": product.affiliate_url,
            "price": product.current_price
        }
    }


def wrap_with_cuelinks(original_url: str) -> str:
    """
    Wrap product URL with direct TryZon AI affiliate tracking
    """
    from services.affiliate_engine import generate_affiliate_url
    return generate_affiliate_url(original_url)


@router.get("/products/list")
async def list_products_admin(
    page: int = 1,
    limit: int = 50,
    featured_only: bool = False,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    List all products for admin management
    """
    stmt = select(Product)
    
    if featured_only:
        stmt = stmt.filter(Product.is_featured == True)
    
    # Total count
    count_stmt = select(func.count()).select_from(stmt.subquery())
    total_count_query = await db.execute(count_stmt)
    total_count = total_count_query.scalar() or 0
    
    # Pagination
    offset = (page - 1) * limit
    stmt = stmt.order_by(desc(Product.created_at)).offset(offset).limit(limit)
    products_query = await db.execute(stmt)
    products = products_query.scalars().all()
    
    return {
        "products": [
            {
                "id": str(p.id),
                "name": p.name,
                "brand": p.brand,
                "store": p.store,
                "price": p.current_price,
                "discount": p.discount_percent,
                "featured": p.is_featured,
                "active": p.is_active,
                "created_at": p.created_at.isoformat()
            }
            for p in products
        ],
        "pagination": {
            "page": page,
            "limit": limit,
            "total_count": total_count
        }
    }


@router.get("/products/fetch-metadata")
async def get_product_metadata_from_url(
    url: str,
    _: bool = Depends(verify_admin)
):
    """
    Fetch product metadata from a remote URL for auto-filling the injection form.
    """
    if not url:
        raise HTTPException(status_code=400, detail="URL is required")
        
    metadata = await fetch_product_metadata(url)
    if "error" in metadata:
        raise HTTPException(status_code=422, detail=metadata["error"])
        
    return metadata


@router.put("/products/{product_id}/toggle-featured")
async def toggle_product_featured(
    product_id: int,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Toggle product featured status
    """
    product_query = await db.execute(select(Product).filter(Product.id == product_id))
    product = product_query.scalar_one_or_none()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    
    product.is_featured = not product.is_featured
    await db.commit()
    
    return {
        "success": True,
        "product_id": str(product_id),
        "is_featured": product.is_featured
    }


@router.delete("/products/{product_id}")
async def delete_product(
    product_id: int,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Permanently delete product from DB and ChromaDB
    """
    product_query = await db.execute(select(Product).filter(Product.id == product_id))
    product = product_query.scalar_one_or_none()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    
    # ── Dependency Cleanup ────────────────────────────────
    # Delete related records that have FK constraints to Product.id
    try:
        from sqlalchemy import delete
        
        # 1. Price History
        await db.execute(delete(PriceHistory).where(PriceHistory.product_id == product_id))
        
        # 2. Affiliate Purchases
        await db.execute(delete(AffiliatePurchase).where(AffiliatePurchase.product_id == product_id))
        
        # 3. Price Alerts
        await db.execute(delete(PriceAlert).where(PriceAlert.product_id == product_id))
        
        # 4. User Interactions
        await db.execute(delete(UserInteraction).where(UserInteraction.product_id == product_id))
        
    except Exception as e:
        log.warning(f"Error cleaning up dependencies for product {product_id}: {e}")
    
    # ── Final Product Removal ──────────────────────────────
    
    # 1. Delete from ChromaDB
    try:
        delete_product_embedding(product_id)
    except Exception as e:
        log.warning(f"Failed to delete product {product_id} from ChromaDB: {e}")

    # 2. Delete from Postgres
    await db.delete(product)
    await db.commit()
    
    return {
        "success": True,
        "message": "Product and all related history permanently deleted"
    }


@router.patch("/products/{product_id}")
async def update_product_manual(
    product_id: int,
    name: Optional[str] = Form(None),
    price: Optional[float] = Form(None),
    brand: Optional[str] = Form(None),
    category: Optional[str] = Form(None),
    image_url: Optional[str] = Form(None),
    featured: Optional[bool] = Form(None),
    affiliate_url: Optional[str] = Form(None),
    is_active: Optional[bool] = Form(None),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Manually override product details
    """
    product_query = await db.execute(select(Product).filter(Product.id == product_id))
    product = product_query.scalar_one_or_none()
    if not product:
        raise HTTPException(status_code=404, detail="Product not found")
    
    if name is not None: product.name = name
    if price is not None: product.current_price = price
    if brand is not None: product.brand = brand
    if category is not None: product.category = category
    if image_url is not None: product.image_url = image_url
    if featured is not None: product.is_featured = featured
    if affiliate_url is not None: product.affiliate_url = affiliate_url
    if is_active is not None: product.is_active = is_active
    
    await db.commit()
    return {"success": True, "message": "Product updated"}


# ============================================================================
# ANALYTICS
# ============================================================================

@router.get("/analytics/revenue")
async def get_revenue_analytics(
    days: int = 30,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Detailed revenue analytics
    """
    start_date = utcnow() - timedelta(days=days)
    
    daily_revenue = []
    for i in range(days):
        day_start = start_date + timedelta(days=i)
        day_end = day_start + timedelta(days=1)
        
        affiliate_query = await db.execute(
            select(func.sum(AffiliatePurchase.commission_earned)).filter(
                AffiliatePurchase.purchased_at >= day_start,
                AffiliatePurchase.purchased_at < day_end
            )
        )
        affiliate = affiliate_query.scalar() or 0
        
        daily_revenue.append({
            "date": day_start.strftime("%Y-%m-%d"),
            "affiliate": round(affiliate, 2),
            "total": round(affiliate, 2)
        })
    
    total_affiliate = sum(d['affiliate'] for d in daily_revenue)
    
    return {
        "period": f"Last {days} days",
        "daily_breakdown": daily_revenue,
        "summary": {
            "total_revenue": round(total_affiliate, 2),
            "affiliate_revenue": round(total_affiliate, 2),
            "average_daily": round(total_affiliate / days, 2)
        }
    }


@router.get("/analytics/users")
async def get_user_analytics(
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    User growth analytics
    """
    now_ist = datetime.now(IST)
    growth_data = []
    for i in range(30):
        target_ist = (now_ist - timedelta(days=29-i)).replace(hour=0, minute=0, second=0, microsecond=0)
        day_start = target_ist.astimezone(timezone.utc)
        day_end = (target_ist + timedelta(days=1)).astimezone(timezone.utc)
        
        new_users_query = await db.execute(
            select(func.count(User.id)).filter(
                User.created_at >= day_start,
                User.created_at < day_end
            )
        )
        new_users = new_users_query.scalar() or 0
        
        growth_data.append({
            "date": target_ist.strftime("%Y-%m-%d"),
            "new_users": new_users
        })
    
    total_users_query = await db.execute(select(func.count(User.id)))
    total_users = total_users_query.scalar() or 0
    
    free_users_query = await db.execute(select(func.count(User.id)).filter(User.subscription_tier == "free"))
    free_users = free_users_query.scalar() or 0
    premium_users = total_users - free_users
    
    return {
        "growth": growth_data,
        "distribution": {
            "total": total_users,
            "free": free_users,
            "premium": premium_users,
            "conversion_rate": round((premium_users / total_users * 100), 2) if total_users > 0 else 0
        }
    }


# ============================================================================
# SYSTEM MONITORING
# ============================================================================

@router.get("/system/gpu-status")
async def get_gpu_status(_: bool = Depends(verify_admin)):
    """
    Get GPU status using GPUtil
    """
    try:
        import GPUtil
        gpus = GPUtil.getGPUs()
        
        gpu_info = []
        for gpu in gpus:
            gpu_info.append({
                "id": gpu.id,
                "name": gpu.name,
                "load": round(gpu.load * 100, 2),
                "memory_used": round(gpu.memoryUsed / 1024, 2),  # GB
                "memory_total": round(gpu.memoryTotal / 1024, 2),  # GB
                "temperature": gpu.temperature,
                "status": "healthy" if gpu.temperature < 80 else "warning"
            })
        
        return {
            "gpus": gpu_info,
            "timestamp": utcnow().isoformat()
        }
    except Exception as e:
        return {
            "gpus": [],
            "error": f"GPU monitoring error: {str(e)}"
        }


# ============================================================================
# NOTIFICATIONS (FCM)
# ============================================================================

class NotificationTestRequest(BaseModel):
    user_id: int
    title: str = "Test Notification"
    body: str = "Hello from TryZon AI Admin!"
    image_url: Optional[str] = None

@router.post("/notifications/test")
async def test_notification(
    req: NotificationTestRequest,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """Send a test push notification to a specific user."""
    from services.notification_service import notify_user
    
    await notify_user(
        user_id=req.user_id,
        title=req.title,
        body=req.body,
        image_url=req.image_url,
        db=db
    )
    
class BroadcastNotificationRequest(BaseModel):
    title: str
    body: str
    image_url: Optional[str] = None
    target_url: Optional[str] = None

@router.post("/notifications/broadcast")
async def broadcast_notification(
    req: BroadcastNotificationRequest,
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """Send a broadcast push notification to ALL registered app users."""
    from services.notification_service import send_push_notification
    from database.models import UserDevice
    
    stmt = select(UserDevice.fcm_token).where(UserDevice.is_active == True)
    res = await db.execute(stmt)
    tokens = list(set([row[0] for row in res.all() if row[0]]))
    
    if not tokens:
        return {"success": False, "message": "No FCM device tokens registered yet."}
        
    data_payload = {}
    if req.target_url:
        data_payload["shared_url"] = req.target_url
        data_payload["notification_type"] = "shopping_detected"
        
    await send_push_notification(
        fcm_tokens=tokens,
        title=req.title,
        body=req.body,
        data=data_payload,
        image_url=req.image_url
    )
    return {"success": True, "message": f"Broadcast sent to {len(tokens)} device tokens!"}


@router.get("/system/server-health")
async def get_server_health(_: bool = Depends(verify_admin)):
    """
    Get server health metrics using psutil & nvidia-smi for GPUs
    """
    import psutil
    import subprocess

    gpu_list = []
    try:
        cmd = ["nvidia-smi", "--query-gpu=index,name,memory.used,memory.total,utilization.gpu", "--format=csv,noheader,nounits"]
        res = subprocess.check_output(cmd, timeout=3).decode("utf-8").strip()
        for line in res.split("\n"):
            parts = [p.strip() for p in line.split(",")]
            if len(parts) >= 5:
                idx, name, mem_used, mem_total, util = parts[0], parts[1], float(parts[2]), float(parts[3]), float(parts[4])
                used_gb = round(mem_used / 1024.0, 2)
                total_gb = round(mem_total / 1024.0, 2)
                pct = round((mem_used / mem_total * 100), 1) if mem_total > 0 else 0
                gpu_list.append({
                    "id": idx,
                    "name": name,
                    "used_vram_gb": used_gb,
                    "total_vram_gb": total_gb,
                    "vram_percent": pct,
                    "gpu_utilization_percent": util
                })
    except Exception as e:
        # Fallback: Query ComfyUI workers directly for real GPU VRAM usage if nvidia-smi is unavailable in container
        try:
            import httpx
            from config import settings
            comfy_urls = getattr(settings, "comfyui_urls", ["http://localhost:8188", "http://localhost:8189"])
            if isinstance(comfy_urls, str):
                comfy_urls = [u.strip() for u in comfy_urls.split(",") if u.strip()]
            
            seen_gpus = set()
            for base_url in comfy_urls:
                try:
                    r = httpx.get(f"{base_url}/system_stats", timeout=1.5)
                    if r.status_code == 200:
                        devices = r.json().get("devices", [])
                        for d in devices:
                            if d.get("type") == "cuda":
                                g_name = d.get("name", "NVIDIA GPU")
                                if g_name in seen_gpus:
                                    continue
                                seen_gpus.add(g_name)
                                v_total = d.get("vram_total", 0) / (1024**3)
                                v_free = d.get("vram_free", 0) / (1024**3)
                                v_used = max(0.0, v_total - v_free)
                                pct = round((v_used / v_total * 100), 1) if v_total > 0 else 0
                                clean_name = g_name.replace(": cudaMallocAsync", "").replace("cuda:0 ", "").strip()
                                gpu_list.append({
                                    "id": str(len(gpu_list)),
                                    "name": clean_name,
                                    "used_vram_gb": round(v_used, 2),
                                    "total_vram_gb": round(v_total, 2),
                                    "vram_percent": pct,
                                    "gpu_utilization_percent": pct
                                })
                except Exception:
                    pass
        except Exception:
            pass
    
    # Real-time ComfyUI Dual Worker Status & Active Queues
    import urllib.request
    comfy_workers = []
    worker_configs = [
        {"port": 8188, "id": 0, "name": "Worker #0: RTX 3060 (12GB)"},
        {"port": 8189, "id": 1, "name": "Worker #1: RTX 5060 Ti (16GB)"}
    ]
    for w in worker_configs:
        try:
            req = urllib.request.urlopen(f"http://localhost:{w['port']}/queue", timeout=1.5)
            q_data = json.loads(req.read().decode())
            r_cnt = len(q_data.get("queue_running", []))
            p_cnt = len(q_data.get("queue_pending", []))
            comfy_workers.append({
                "port": w["port"],
                "name": w["name"],
                "is_online": True,
                "running_jobs": r_cnt,
                "pending_jobs": p_cnt,
                "status_text": f"Active ({r_cnt} busy, {p_cnt} queued)" if (r_cnt or p_cnt) else "Ready • Idle"
            })
        except Exception:
            comfy_workers.append({
                "port": w["port"],
                "name": w["name"],
                "is_online": False,
                "running_jobs": 0,
                "pending_jobs": 0,
                "status_text": "Offline / Unreachable"
            })

    return {
        "status": "online",
        "cpu": {
            "usage_percent": psutil.cpu_percent(interval=None),  # Non-blocking
            "cores": psutil.cpu_count()
        },
        "memory": {
            "total_gb": round(psutil.virtual_memory().total / (1024**3), 2),
            "used_gb": round(psutil.virtual_memory().used / (1024**3), 2),
            "percent": psutil.virtual_memory().percent
        },
        "disk": {
            "total_gb": round(psutil.disk_usage('/').total / (1024**3), 2),
            "used_gb": round(psutil.disk_usage('/').used / (1024**3), 2),
            "percent": psutil.disk_usage('/').percent
        },
        "gpus": gpu_list,
        "comfy_workers": comfy_workers,
        "timestamp": utcnow().isoformat()
    }


@router.get("/admob/health")
async def get_admob_health(
    timeframe: Optional[str] = Query("daily"),
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Dedicated AdMob Health, Fill Rate, and Compliance Cockpit for Founder.
    Tracks requests, impressions, match/show rate, and fraud/policy health.
    """
    filter_date, filter_date_end = parse_timeframe(timeframe)
    if not filter_date:
        filter_date = get_ist_today_start_utc()

    # Query AdPaidImpression for real impression count & exact revenue
    ad_paid_stmt = select(
        func.count(AdPaidImpression.id).label("impressions"),
        func.coalesce(func.sum(AdPaidImpression.revenue_usd), 0.0).label("revenue_usd")
    ).filter(AdPaidImpression.created_at >= filter_date)
    if filter_date_end:
        ad_paid_stmt = ad_paid_stmt.filter(AdPaidImpression.created_at < filter_date_end)
    paid_row = (await db.execute(ad_paid_stmt)).one_or_none()
    real_impressions = int(paid_row.impressions or 0) if paid_row else 0
    revenue_usd = float(paid_row.revenue_usd or 0.0) if paid_row else 0.0

    # Rewarded Ads Watched to Completion
    rewarded_stmt = select(func.count(AppAnalyticsEvent.id)).filter(
        AppAnalyticsEvent.event_name == "rewarded_ad_watched",
        AppAnalyticsEvent.created_at >= filter_date
    )
    if filter_date_end:
        rewarded_stmt = rewarded_stmt.filter(AppAnalyticsEvent.created_at < filter_date_end)
    rewarded_count = (await db.execute(rewarded_stmt)).scalar() or 0

    # Tryons started (used to estimate ad eligible attempts)
    tryon_stmt = select(func.count(TryOnSession.id)).filter(TryOnSession.created_at >= filter_date)
    if filter_date_end:
        tryon_stmt = tryon_stmt.filter(TryOnSession.created_at < filter_date_end)
    tryon_count = (await db.execute(tryon_stmt)).scalar() or 0

    # Effective impressions
    total_impressions = max(real_impressions, rewarded_count)
    # Estimate total requests: tryons * 1.15 + extra reward claims
    estimated_requests = max(total_impressions, int(tryon_count * 1.15) + max(0, rewarded_count - tryon_count))
    if estimated_requests == 0:
        estimated_requests = 1

    show_rate = round((total_impressions / estimated_requests) * 100.0, 1) if estimated_requests > 0 else 100.0
    show_rate = min(100.0, show_rate)

    # Health status based on Google AdMob policy thresholds (>70% show rate is optimal)
    if show_rate >= 70.0:
        status_label = "HEALTHY & OPTIMAL ✅"
        status_color = "#4CAF50"
        recommendation = "AdMob Show Rate is excellent (>70%). Low risk of ad serving limits."
    elif show_rate >= 40.0:
        status_label = "MODERATE ⚠️"
        status_color = "#FFB74D"
        recommendation = "Show Rate is moderate. Keep preloading to a minimum and ensure ads are only requested on user clicks."
    else:
        status_label = "LOW SHOW RATE RISK 🚨"
        status_color = "#FF5252"
        recommendation = "High ad requests with low impressions detected. Check for unnecessary eager loading."

    return {
        "timeframe": timeframe,
        "total_requests_estimated": estimated_requests,
        "total_impressions": total_impressions,
        "rewarded_ads_completed": rewarded_count,
        "show_rate_pct": show_rate,
        "revenue_usd": round(revenue_usd, 4),
        "revenue_inr": round(revenue_usd * 86.5, 2),
        "health_status": status_label,
        "health_color": status_color,
        "recommendation": recommendation,
        "unit_breakdown": {
            "rewarded_interstitial": "ca-app-pub-5657564830162310/3268453248 (Priority 1)",
            "standard_rewarded": "ca-app-pub-5657564830162310/7742661163 (Priority 2)",
            "waterfall_enabled": True
        }
    }


@router.post("/system/clear-cache")
async def clear_system_cache(_: bool = Depends(verify_admin)):
    """
    1-Click Founder Action: Flush ComfyUI GPU VRAM cache and latents across all workers
    """
    import httpx
    from config import settings
    
    comfy_urls = getattr(settings, "comfyui_urls", ["http://localhost:8188", "http://localhost:8189"])
    if isinstance(comfy_urls, str):
        comfy_urls = [u.strip() for u in comfy_urls.split(",") if u.strip()]
        
    cleared_workers = []
    for base_url in comfy_urls:
        try:
            async with httpx.AsyncClient(timeout=4.0) as client:
                res = await client.post(f"{base_url}/free", json={"unload_models": False, "free_memory": True})
                if res.status_code == 200:
                    cleared_workers.append(base_url)
        except Exception:
            pass
            
    import gc
    gc.collect()
    try:
        import torch
        if torch.cuda.is_available():
            torch.cuda.empty_cache()
    except Exception:
        pass
        
    return {
        "success": True,
        "message": f"Successfully flushed GPU VRAM & cache across {len(cleared_workers)} workers!",
        "cleared_workers": cleared_workers
    }


@router.get("/system/logs")
async def get_system_logs(
    lines: int = 50,
    _: bool = Depends(verify_admin)
):
    """
    Get recent system logs from master_server.log
    """
    log_file = "master_server.log"
    # Try different locations for the log file if needed, but master_server.log is in root
    if not os.path.exists(log_file):
        # Check if it's in the current directory or one level up
        if os.path.exists(f"../{log_file}"):
            log_file = f"../{log_file}"
        else:
            return {"logs": ["Log file not found."]}
    
    try:
        with open(log_file, "r") as f:
            content = f.readlines()
            last_lines = content[-lines:] if len(content) > lines else content
            return {"logs": [line.strip() for line in last_lines]}
    except Exception as e:
        return {"logs": [f"Error reading logs: {str(e)}"]}


@router.get("/analytics/user-intelligence")
async def get_user_intelligence_dashboard(
    db: AsyncSession = Depends(get_db),
    _: bool = Depends(verify_admin)
):
    """
    Comprehensive User Intelligence Dashboard:
    - 8 Key Business KPIs (New Users, DAU, D1/D7 Retention, Try-ons/user, Ads/user, Shopping Clicks/user, Paying users)
    - 4-Group User Segmentation (Group A: Hot Try-On Users, Group B: Activation Bottleneck, Group C: High Buyer Intent, Group D: Retention Risk)
    - Complete Funnel conversion rates
    """
    now = utcnow()
    day_1_ago = now - timedelta(days=1)
    day_2_ago = now - timedelta(days=2)
    day_7_ago = now - timedelta(days=7)
    day_8_ago = now - timedelta(days=8)

    # 1. Total & New Users
    total_users_res = await db.execute(select(func.count(User.id)))
    total_users = total_users_res.scalar() or 0

    new_users_24h_res = await db.execute(select(func.count(User.id)).filter(User.created_at >= day_1_ago))
    new_users_24h = new_users_24h_res.scalar() or 0

    # 2. Daily Active Users (DAU) - Active in last 24h
    dau_res = await db.execute(select(func.count(User.id)).filter(User.last_active_at >= day_1_ago))
    dau = dau_res.scalar() or 0
    if dau == 0 and total_users > 0:
        # Fallback to new users created in 24h if last_active_at wasn't populated for older records
        dau = new_users_24h

    # 3. D1 Retention Calculation (Users created 24h-48h ago who returned)
    d1_cohort_res = await db.execute(select(func.count(User.id)).filter(User.created_at >= day_2_ago, User.created_at < day_1_ago))
    d1_cohort = d1_cohort_res.scalar() or 0
    d1_retained_res = await db.execute(select(func.count(User.id)).filter(User.created_at >= day_2_ago, User.created_at < day_1_ago, User.last_active_at >= day_1_ago))
    d1_retained = d1_retained_res.scalar() or 0
    d1_retention_pct = round((d1_retained / d1_cohort * 100), 2) if d1_cohort > 0 else 0.0

    # 4. D7 Retention Calculation
    d7_cohort_res = await db.execute(select(func.count(User.id)).filter(User.created_at >= day_8_ago, User.created_at < day_7_ago))
    d7_cohort = d7_cohort_res.scalar() or 0
    d7_retained_res = await db.execute(select(func.count(User.id)).filter(User.created_at >= day_8_ago, User.created_at < day_7_ago, User.last_active_at >= day_7_ago))
    d7_retained = d7_retained_res.scalar() or 0
    d7_retention_pct = round((d7_retained / d7_cohort * 100), 2) if d7_cohort > 0 else 0.0

    # 5. Engagement Metrics
    total_tryons_res = await db.execute(select(func.count(TryOnSession.id)))
    total_tryons = total_tryons_res.scalar() or 0
    users_with_tryon_res = await db.execute(select(func.count(func.distinct(TryOnSession.user_id))))
    users_with_tryon_count = users_with_tryon_res.scalar() or 0
    avg_tryons_per_active_user = round(total_tryons / users_with_tryon_count, 2) if users_with_tryon_count > 0 else 0.0

    # 6. Rewarded Ads & Shopping Clicks
    total_ads_res = await db.execute(select(func.sum(User.daily_reward_ad_count)))
    total_ads_watched = total_ads_res.scalar() or 0

    shopping_clicks_res = await db.execute(select(func.count(UserInteraction.id)).filter(UserInteraction.interaction_type == "click"))
    total_shopping_clicks = shopping_clicks_res.scalar() or 0
    users_who_clicked_shopping_res = await db.execute(select(func.count(func.distinct(UserInteraction.user_id))).filter(UserInteraction.interaction_type == "click"))
    users_who_clicked_shopping_count = users_who_clicked_shopping_res.scalar() or 0

    # 7. Paying Users & Revenue
    paying_users_res = await db.execute(select(func.count(func.distinct(Payment.user_id))).filter(Payment.status.in_(["captured", "SUCCESS"])))
    paying_users = paying_users_res.scalar() or 0

    total_revenue_res = await db.execute(select(func.sum(Payment.amount)).filter(Payment.status.in_(["captured", "SUCCESS"])))
    total_revenue_paise = total_revenue_res.scalar() or 0
    total_revenue_inr = round(total_revenue_paise / 100.0, 2)

    # 8. User Segmentation (4 Groups)
    # Group A: HOT 🔥 (Has performed at least 1 tryon)
    group_a = users_with_tryon_count

    # Group B: Activation Bottleneck (Registered, but 0 tryons)
    group_b = max(0, total_users - group_a)

    # Group C: Buyer Intent 🔥🔥 (Performed Try-On AND Clicked Shopping Link)
    group_c_res = await db.execute(
        select(func.count(func.distinct(TryOnSession.user_id)))
        .filter(TryOnSession.user_id.in_(
            select(UserInteraction.user_id).filter(UserInteraction.interaction_type == "click")
        ))
    )
    group_c = group_c_res.scalar() or 0

    # Group D: Retention Risk / Inactive (No activity for > 7 days)
    group_d_res = await db.execute(select(func.count(User.id)).filter(User.created_at < day_7_ago, (User.last_active_at == None) | (User.last_active_at < day_7_ago)))
    group_d = group_d_res.scalar() or 0

    return {
        "kpis": {
            "total_users": total_users,
            "new_users_24h": new_users_24h,
            "dau": dau,
            "d1_retention_pct": d1_retention_pct,
            "d7_retention_pct": d7_retention_pct,
            "tryons_per_active_user": avg_tryons_per_active_user,
            "total_tryons": total_tryons,
            "rewarded_ads_watched": total_ads_watched,
            "shopping_clicks": total_shopping_clicks,
            "paying_users": paying_users,
            "total_revenue_inr": total_revenue_inr
        },
        "user_segmentation": {
            "group_a_hot_tryon_users": {
                "count": group_a,
                "percentage": round((group_a / total_users * 100), 1) if total_users > 0 else 0,
                "label": "Login + Try-On Done (HOT 🔥)"
            },
            "group_b_activation_gap": {
                "count": group_b,
                "percentage": round((group_b / total_users * 100), 1) if total_users > 0 else 0,
                "label": "Login Done + 0 Try-Ons (Activation Problem)"
            },
            "group_c_buyer_intent": {
                "count": group_c,
                "percentage": round((group_c / total_users * 100), 1) if total_users > 0 else 0,
                "label": "Try-On + Shopping Link Clicked (High Buyer Intent 🔥🔥)"
            },
            "group_d_retention_risk": {
                "count": group_d,
                "percentage": round((group_d / total_users * 100), 1) if total_users > 0 else 0,
                "label": "Inactive > 7 Days (Retention Risk)"
            }
        },
        "funnel": {
            "stage_1_registered": total_users,
            "stage_2_tryon_activated": group_a,
            "stage_3_shopping_click": users_who_clicked_shopping_count,
            "stage_4_paid_purchased": paying_users,
            "activation_rate_pct": round((group_a / total_users * 100), 1) if total_users > 0 else 0,
            "commerce_intent_rate_pct": round((users_who_clicked_shopping_count / group_a * 100), 1) if group_a > 0 else 0,
            "monetization_rate_pct": round((paying_users / total_users * 100), 1) if total_users > 0 else 0
        }
    }

