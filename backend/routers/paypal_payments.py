"""
routers/paypal_payments.py — PayPal Integration for Global Payments
Handles Order Creation and Capture for Credits and Subscriptions.
"""

import logging
import httpx
from fastapi import APIRouter, Depends, HTTPException, Request
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings
from database.models import User, Payment
from database.postgres import get_db, AsyncSessionLocal
from routers.auth import get_current_user

log = logging.getLogger(__name__)
router = APIRouter()

# ── Config ──────────────────────────────────────────────
PAYPAL_API_BASE = "https://api-m.paypal.com" if settings.paypal_mode == "live" else "https://api-m.sandbox.paypal.com"

# Credits mapping
CREDIT_PACKS = {
    "Starter": 60,
    "Value": 500,
    "Business": 2000,
    "Enterprise": 7000,
    "Quick Refill": 25,
    "Power Up": 150
}
SUBSCRIPTIONS = ["Weekly Trial", "Monthly Pro", "Yearly Legend"]

# ── Helpers ─────────────────────────────────────────────

async def get_paypal_access_token():
    """Fetch an access token from PayPal using client credentials."""
    url = f"{PAYPAL_API_BASE}/v1/oauth2/token"
    auth = (settings.paypal_client_id, settings.paypal_secret_key)
    data = {"grant_type": "client_credentials"}
    
    async with httpx.AsyncClient() as client:
        resp = await client.post(url, data=data, auth=auth)
        if resp.status_code != 200:
            log.error(f"Failed to get PayPal token: {resp.text}")
            raise HTTPException(500, "PayPal authentication failed")
        return resp.json()["access_token"]

# ── Endpoints ───────────────────────────────────────────

class OrderCaptureRequest(BaseModel):
    order_id: str
    plan_name: str
    amount_usd: float

@router.post("/payments/paypal/capture-order")
async def capture_paypal_order(
    req: OrderCaptureRequest,
    current_user: User = Depends(get_current_user),
):
    """Capture an approved PayPal order and fulfill the purchase."""
    token = await get_paypal_access_token()
    url = f"{PAYPAL_API_BASE}/v2/checkout/orders/{req.order_id}/capture"
    headers = {
        "Content-Type": "application/json",
        "Authorization": f"Bearer {token}"
    }

    async with httpx.AsyncClient() as client:
        resp = await client.post(url, headers=headers)
        if resp.status_code != 201:
            log.error(f"PayPal capture failed: {resp.text}")
            raise HTTPException(400, f"PayPal Capture Error: {resp.json().get('message', 'Unknown error')}")
        
        data = resp.json()
        if data["status"] == "COMPLETED":
            await process_successful_payment(current_user.id, req.plan_name, req.amount_usd, req.order_id)
            return {"status": "success", "order_id": req.order_id}
        else:
            raise HTTPException(400, f"Order status is {data['status']}, not COMPLETED")

async def process_successful_payment(user_id: int, plan_name: str, amount: float, order_id: str):
    log.info(f"💰 Processing PayPal Payment for User {user_id}: {plan_name}")
    
    async with AsyncSessionLocal() as db:
        result = await db.execute(select(User).where(User.id == user_id))
        user = result.scalar_one_or_none()
        
        if user:
            if plan_name in SUBSCRIPTIONS:
                user.is_premium = True
                user.subscription_tier = plan_name
                log.info(f"User {user_id} upgraded to {plan_name} via PayPal")
            elif plan_name in CREDIT_PACKS:
                credits_to_add = CREDIT_PACKS[plan_name]
                user.credits += credits_to_add
                user.subscription_tier = plan_name
                log.info(f"Added {credits_to_add} credits to User {user_id} via PayPal")
            
            # Save payment record
            payment = Payment(
                user_id=user_id,
                razorpay_order_id=f"paypal_{order_id[:30]}", # Reusing order_id field
                amount=int(amount * 100), # Store in cents/paise consistency
                currency="USD",
                status="captured"
            )
            db.add(payment)
            await db.commit()
