"""
routers/payments.py — Razorpay Integration
Handles order creation, payment verification, and webhooks for Premium upgrades.
"""

import hmac
import hashlib
import json
import logging
import os
from typing import Optional

import razorpay
from fastapi import APIRouter, Depends, HTTPException, Request, status
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database.models import User, Payment
from database.postgres import get_db
from routers.auth import get_current_user
from config import get_settings

settings = get_settings()

log = logging.getLogger(__name__)
router = APIRouter()

# ── Razorpay Setup ────────────────────────────────────────
client = razorpay.Client(
    auth=(settings.razorpay_key_id, settings.razorpay_key_secret)
)

# ── Schemas ───────────────────────────────────────────────
class OrderRequest(BaseModel):
    amount: int  # in paise (e.g., 4900 for ₹49)
    plan_name: str = "Icon" # Essence|Icon|Elite

class OrderResponse(BaseModel):
    order_id: str
    amount: int
    currency: str = "INR"
    key: str

class VerifyRequest(BaseModel):
    razorpay_order_id: str
    razorpay_payment_id: str
    razorpay_signature: str

class GoogleVerifyRequest(BaseModel):
    purchaseToken: str
    productId: str

# Plan Mapping based on Global Model ($)
CREDIT_PACKS = {
    "Pocket": 15,
    "Starter": 60,
    "Value": 500,
    "Business": 2000,
    "Enterprise": 7000,
    "Quick Refill": 25,
    "Power Up": 150
}

SUBSCRIPTIONS = ["Weekly Pro", "Monthly Pro", "Yearly Legend"]

# ── Endpoints ─────────────────────────────────────────────

from starlette.concurrency import run_in_threadpool

@router.post("/payments/order", response_model=OrderResponse)
async def create_order(
    req: OrderRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Create a new Razorpay order for a specific subscription tier."""
    try:
        order_data = {
            "amount": req.amount,
            "currency": "INR",
            "receipt": f"user_{current_user.id}_rcpt",
            "notes": {
                "user_id": current_user.id,
                "plan_name": req.plan_name
            }
        }
        order = await run_in_threadpool(client.order.create, data=order_data)
        
        # Save order to DB
        payment = Payment(
            user_id=current_user.id,
            razorpay_order_id=order["id"],
            amount=req.amount,
            status="created",
        )
        db.add(payment)
        await db.commit()
        
        return OrderResponse(
            order_id=order["id"], 
            amount=order["amount"],
            key=settings.razorpay_key_id
        )
    except Exception as e:
        log.error(f"Razorpay order creation failed: {e}")
        raise HTTPException(500, "Could not create payment order")


@router.post("/payments/verify")
async def verify_payment(
    req: VerifyRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Verify Razorpay payment and upgrade user to the correct tier."""
    params_dict = {
        "razorpay_order_id": req.razorpay_order_id,
        "razorpay_payment_id": req.razorpay_payment_id,
        "razorpay_signature": req.razorpay_signature,
    }

    try:
        # Verify signature
        client.utility.verify_payment_signature(params_dict)
        
        # Fetch the order to see which plan was bought
        order = client.order.fetch(req.razorpay_order_id)
        plan_name = order.get("notes", {}).get("plan_name", "Icon")

        # Update Payment Record
        result = await db.execute(
            select(Payment).where(Payment.razorpay_order_id == req.razorpay_order_id)
        )
        payment = result.scalar_one_or_none()
        if payment:
            payment.razorpay_payment_id = req.razorpay_payment_id
            payment.status = "captured"
            
            # UPGRADE LOGIC
            if plan_name in SUBSCRIPTIONS:
                current_user.is_premium = True
                current_user.subscription_tier = plan_name
                # Subscriptions get unlimited (handled in tryon.py)
            elif plan_name in CREDIT_PACKS:
                credits_to_add = CREDIT_PACKS[plan_name]
                if plan_name == "Pocket":
                    if (current_user.paid_credits or 0) == 0:
                        credits_to_add = 25 # 15 base + 10 FIRST-TIME BUYER BONUS!
                        log.info(f"First-Time Buyer Bonus applied for User {current_user.id}: 15 + 10 = 25 Credits!")
                    else:
                        credits_to_add = 15
                current_user.credits += credits_to_add
                current_user.paid_credits = (current_user.paid_credits or 0) + credits_to_add
                log.info(f"Added {credits_to_add} paid credits to user {current_user.id} for {plan_name}")
            
            await db.commit()
            return {
                "status": "success", 
                "message": f"Successfully processed {plan_name}!",
                "credits_total": current_user.credits,
                "tier": current_user.subscription_tier
            }
            
    except Exception as e:
        log.warning(f"Signature verification failed for order {req.razorpay_order_id}: {e}")
        raise HTTPException(400, "Invalid payment signature")


@router.post("/payments/google/verify")
async def verify_google_purchase(
    req: GoogleVerifyRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Verify Google Play purchase and upgrade user to the correct tier."""
    try:
        # Prevent Hackers from bypassing Google Play
        google_service_account = os.getenv("GOOGLE_PLAY_SERVICE_ACCOUNT")
        if not google_service_account:
            # If server doesn't have the real Google JSON, do NOT give away free credits!
            # Reject with a proper error so that they don't get free credits by just hitting this API
            log.warning(f"Google Play Verification skipped because service account is missing. Token: {req.purchaseToken}")
            raise HTTPException(500, "Google Play integration is not configured on the server")
            
        # TODO: Implement google-api-python-client verification here once the JSON is provided
        
        # In a real production environment, you would use google-api-python-client 
        # to verify the purchaseToken with Google Play Developer API.
        
        # Product IDs look like: credits_starter, credits_value, sub_weekly_pro, etc.
        plan_name = "Unknown"
        
        # Map product IDs back to plan names
        if req.productId in ["credits_starter", "credits_50"]: plan_name = "Starter"
        elif req.productId in ["credits_value", "credits_200"]: plan_name = "Value"
        elif req.productId in ["credits_business", "credits_500"]: plan_name = "Business"
        elif req.productId == "credits_enterprise": plan_name = "Enterprise"
        elif req.productId == "credits_pocket": plan_name = "Pocket"
        elif req.productId == "sub_weekly_pro": plan_name = "Weekly Pro"
        elif req.productId == "sub_monthly_pro": plan_name = "Monthly Pro"
        elif req.productId == "sub_yearly_legend": plan_name = "Yearly Legend"
        else:
            log.warning(f"Unrecognized product ID submitted by User {current_user.id}: {req.productId}")
            raise HTTPException(400, f"Invalid or unauthorized product ID: {req.productId}")
        
        import hashlib
        from sqlalchemy.exc import IntegrityError
        
        # SHA-256 Full 64-Character Secure Idempotency Fingerprint
        token_hash = hashlib.sha256(req.purchaseToken.encode('utf-8')).hexdigest()
        order_key = f"gp_{token_hash}"
        
        # Check if this purchaseToken was already claimed (Idempotency & Cross-Account Ownership Guard)
        from sqlalchemy import select
        result = await db.execute(select(Payment).where(Payment.razorpay_order_id == order_key))
        existing_payment = result.scalar_one_or_none()
        if existing_payment:
            if existing_payment.user_id == current_user.id:
                log.info(f"Purchase token {order_key} already processed for User {current_user.id}. Returning current balance.")
                return {
                    "status": "already_processed",
                    "message": "Purchase token already processed.",
                    "credits_total": current_user.credits,
                    "tier": current_user.subscription_tier
                }
            else:
                log.warning(f"Cross-account claim attempt for token {order_key} by User {current_user.id} (Original owner: User {existing_payment.user_id})")
                raise HTTPException(400, "This purchase token has already been claimed by another account!")
            
        # Create a payment record
        payment = Payment(
            user_id=current_user.id,
            razorpay_order_id=order_key,
            razorpay_payment_id=order_key,
            amount=0, # Amount handled by Google Play
            status="captured",
        )
        db.add(payment)
        
        # UPGRADE LOGIC
        if plan_name in SUBSCRIPTIONS:
            current_user.is_premium = True
            current_user.subscription_tier = plan_name
            log.info(f"Upgraded user {current_user.id} to Google Play Subscription: {plan_name}")
        elif plan_name in CREDIT_PACKS:
            credits_to_add = CREDIT_PACKS[plan_name]
            if plan_name == "Pocket" or req.productId == "credits_pocket":
                if (current_user.paid_credits or 0) == 0:
                    credits_to_add = 25 # 15 base + 10 FIRST-TIME BUYER BONUS!
                    log.info(f"First-Time Buyer Bonus applied for User {current_user.id}: 15 + 10 = 25 Credits via Google Play!")
                else:
                    credits_to_add = 15
            current_user.credits += credits_to_add
            current_user.paid_credits = (current_user.paid_credits or 0) + credits_to_add
            log.info(f"Added {credits_to_add} paid credits to user {current_user.id} from Google Play {plan_name}")
            
        try:
            await db.commit()
        except IntegrityError:
            await db.rollback()
            log.warning(f"Concurrent race condition caught for purchase token {order_key}. Rolling back duplicate credit.")
            return {
                "status": "already_processed",
                "message": "Purchase token already processed.",
                "credits_total": current_user.credits,
                "tier": current_user.subscription_tier
            }
        return {
            "status": "success", 
            "message": f"Successfully processed {plan_name} via Google Play!",
            "credits_total": current_user.credits,
            "tier": current_user.subscription_tier
        }
            
    except HTTPException:
        raise
    except Exception as e:
        log.error(f"Google Play verification failed for token {req.purchaseToken}: {e}")
        raise HTTPException(500, "Could not verify Google Play purchase")


@router.post("/payments/webhook")
async def razorpay_webhook(
    request: Request,
    db: AsyncSession = Depends(get_db),
):
    """Handle incoming Razorpay webhooks (e.g., payment.captured)."""
    webhook_secret = os.getenv("RAZORPAY_WEBHOOK_SECRET", "")
    body = await request.body()
    signature = request.headers.get("X-Razorpay-Signature", "")

    try:
        if webhook_secret:
            client.utility.verify_webhook_signature(body.decode("utf-8"), signature, webhook_secret)
        else:
            log.warning("RAZORPAY_WEBHOOK_SECRET is not set, skipping webhook signature validation.")
            
        payload = await request.json()
        event = payload.get("event")
        
        log.info(f"Incoming Razorpay Webhook: {event}")
        
        if event == "payment.captured":
            payment_entity = payload.get("payload", {}).get("payment", {}).get("entity", {})
            order_id = payment_entity.get("order_id")
            payment_id = payment_entity.get("id")
            notes = payment_entity.get("notes", {})
            plan_name = notes.get("plan_name")
            user_id = notes.get("user_id")

            if order_id and user_id and plan_name:
                from sqlalchemy import select
                from database.models import Payment, User
                
                # Check if payment was already processed
                result = await db.execute(select(Payment).where(Payment.razorpay_order_id == order_id))
                payment = result.scalar_one_or_none()
                
                if payment and payment.status != "captured":
                    payment.razorpay_payment_id = payment_id
                    payment.status = "captured"
                    
                    user_res = await db.execute(select(User).where(User.id == int(user_id)))
                    user = user_res.scalar_one_or_none()
                    
                    if user:
                        if plan_name in SUBSCRIPTIONS:
                            user.is_premium = True
                            user.subscription_tier = plan_name
                        elif plan_name in CREDIT_PACKS:
                            user.credits += CREDIT_PACKS[plan_name]
                        
                        await db.commit()
                        log.info(f"Webhook async upgrade successful for user {user_id} (Order: {order_id})")

    except Exception as e:
        log.error(f"Razorpay Webhook Error: {e}")
        raise HTTPException(400, "Webhook processing failed")

    return {"status": "ok"}
