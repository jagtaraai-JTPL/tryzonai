"""
routers/stripe_payments.py — Stripe Integration for Global Payments
Handles Checkout Sessions and Webhooks for Credits and Subscriptions.
"""

import logging
import os
import stripe
from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, Request, Header
from pydantic import BaseModel
from sqlalchemy import select
from sqlalchemy.ext.asyncio import AsyncSession

from database.models import User, Payment
from database.postgres import get_db, AsyncSessionLocal
from routers.auth import get_current_user

log = logging.getLogger(__name__)
router = APIRouter()

# ── Stripe Setup ──────────────────────────────────────────
stripe.api_key = os.getenv("STRIPE_SECRET_KEY", "")
STRIPE_WEBHOOK_SECRET = os.getenv("STRIPE_WEBHOOK_SECRET", "")

# Same mapping as in payments.py for consistency
CREDIT_PACKS = {
    "Pocket": 15,
    "Starter": 60,
    "Value": 500,
    "Business": 2000,
    "Enterprise": 7000,
    "Quick Refill": 25,
    "Power Up": 150
}
SUBSCRIPTIONS = ["Weekly Trial", "Monthly Pro", "Yearly Legend"]

# ── Schemas ───────────────────────────────────────────────
class StripeCheckoutRequest(BaseModel):
    plan_name: str
    price_usd: float

# ── Endpoints ─────────────────────────────────────────────

@router.post("/payments/stripe/create-checkout")
async def create_stripe_checkout(
    req: StripeCheckoutRequest,
    current_user: User = Depends(get_current_user),
):
    """Create a Stripe Checkout Session for a specific plan."""
    try:
        # Determine if it's a subscription or one-time payment
        mode = "subscription" if req.plan_name in SUBSCRIPTIONS else "payment"
        
        # In a real app, you'd use Price IDs from Stripe dashboard.
        # For this implementation, we'll use inline price data for flexibility.
        
        session = stripe.checkout.Session.create(
            payment_method_types=["card"],
            line_items=[{
                "price_data": {
                    "currency": "usd",
                    "product_data": {
                        "name": f"TryZon AI {req.plan_name}",
                        "description": f"Access to {req.plan_name} features and credits.",
                    },
                    "unit_amount": int(req.price_usd * 100), # Stripe uses cents
                    # For subscriptions, you'd add recurring info here
                },
                "quantity": 1,
            }],
            mode=mode,
            success_url=os.getenv("STRIPE_SUCCESS_URL", "https://tryzonai.com/premium?success=true"),
            cancel_url=os.getenv("STRIPE_CANCEL_URL", "https://tryzonai.com/premium?cancel=true"),
            metadata={
                "user_id": current_user.id,
                "plan_name": req.plan_name
            },
            customer_email=current_user.email
        )
        
        return {"checkout_url": session.url}
    except Exception as e:
        log.error(f"Stripe Checkout creation failed: {e}")
        raise HTTPException(500, f"Stripe error: {str(e)}")

@router.post("/payments/stripe/webhook")
async def stripe_webhook(request: Request):
    """Handle Stripe Webhooks for asynchronous payment processing."""
    payload = await request.body()
    sig_header = request.headers.get("stripe-signature")

    try:
        event = stripe.Webhook.construct_event(
            payload, sig_header, STRIPE_WEBHOOK_SECRET
        )
    except ValueError as e:
        raise HTTPException(400, "Invalid payload")
    except stripe.error.SignatureVerificationError as e:
        raise HTTPException(400, "Invalid signature")

    # Handle the event
    if event['type'] == 'checkout.session.completed':
        session = event['data']['object']
        await process_successful_payment(session)

    return {"status": "success"}

async def process_successful_payment(session):
    user_id = int(session.metadata.get("user_id"))
    plan_name = session.metadata.get("plan_name")
    
    log.info(f"💰 Processing Stripe Payment for User {user_id}: {plan_name}")
    
    async with AsyncSessionLocal() as db:
        result = await db.execute(select(User).where(User.id == user_id))
        user = result.scalar_one_or_none()
        
        if user:
            if plan_name in SUBSCRIPTIONS:
                user.is_premium = True
                user.subscription_tier = plan_name
                log.info(f"User {user_id} upgraded to {plan_name} via Stripe")
            elif plan_name in CREDIT_PACKS:
                credits_to_add = CREDIT_PACKS[plan_name]
                user.credits += credits_to_add
                user.subscription_tier = plan_name
                log.info(f"Added {credits_to_add} credits to User {user_id} via Stripe")
            
            # Save Stripe payment record if needed (using Payment model)
            payment = Payment(
                user_id=user_id,
                razorpay_order_id=f"stripe_{session.id[:30]}", # Reusing model for now
                amount=session.amount_total,
                currency="USD",
                status="captured"
            )
            db.add(payment)
            await db.commit()
