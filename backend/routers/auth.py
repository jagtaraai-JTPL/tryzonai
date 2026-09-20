"""
routers/auth.py — JWT Authentication endpoints
POST /api/v1/auth/register
POST /api/v1/auth/login
GET  /api/v1/auth/me
"""

import logging
log = logging.getLogger(__name__)

from datetime import timedelta

from typing import Optional
from fastapi import APIRouter, Depends, HTTPException, status, Request
from fastapi.security import OAuth2PasswordBearer, OAuth2PasswordRequestForm
from passlib.context import CryptContext
from pydantic import BaseModel, EmailStr
from sqlalchemy import select, update, func
from sqlalchemy.ext.asyncio import AsyncSession

from config import settings
from database.models import User, TryOnSession, WardrobeItem, Payment
from sqlalchemy import func
from datetime import datetime, timezone
from database.postgres import get_db
from firebase_admin import auth as firebase_auth
from utils.auth_utils import create_access_token, verify_token

router = APIRouter()

# ── Setup ──────────────────────────────────────────────────
oauth2_scheme = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login")
oauth2_scheme_optional = OAuth2PasswordBearer(tokenUrl="/api/v1/auth/login", auto_error=False)

import bcrypt

pwd_context = CryptContext(schemes=["bcrypt"], deprecated="auto")


def hash_password(password: str) -> str:
    safe_pwd = (password or "")[:72]
    try:
        salt = bcrypt.gensalt()
        return bcrypt.hashpw(safe_pwd.encode("utf-8"), salt).decode("utf-8")
    except Exception:
        return pwd_context.hash(safe_pwd)


def verify_password(plain_password: str, hashed_password: str) -> bool:
    if not plain_password or not hashed_password:
        return False
    safe_pwd = plain_password[:72]
    try:
        return bcrypt.checkpw(safe_pwd.encode("utf-8"), hashed_password.encode("utf-8"))
    except Exception:
        try:
            return pwd_context.verify(safe_pwd, hashed_password)
        except Exception:
            return False


# ── Schemas ───────────────────────────────────────────────
class RegisterRequest(BaseModel):
    email: EmailStr
    name: str  # Frontend sends 'name'
    password: str


class UserResponse(BaseModel):
    id: int
    email: str
    username: str
    name: Optional[str] = None
    is_premium: bool
    is_admin: bool = False
    admin_token: Optional[str] = None
    credits: int
    paid_credits: int = 0
    bonus_credits: int = 0
    credits_received: int = 2
    credits_used: int = 0
    try_ons_today: int = 0
    try_ons_limit: int = 1
    wardrobe_count: int = 0
    subscription_tier: Optional[str] = "free"
    subscription_expires_at: Optional[datetime] = None
    pref_price_drop: bool = True
    pref_style_recs: bool = True
    photo_url: Optional[str] = None
    daily_reward_ad_count: int = 0
    has_given_5_star: bool = False
    rating_stars: Optional[int] = None
    pref_gender: Optional[str] = None
    pref_fashion_goal: Optional[str] = None
    pref_style_vibe: Optional[str] = None


class LoginRequest(BaseModel):
    email: str
    password: str


class LoginResponse(BaseModel):
    token: str
    user: UserResponse


class GoogleLoginRequest(BaseModel):
    id_token: Optional[str] = None
    idToken: Optional[str] = None


class AppleLoginRequest(BaseModel):
    id_token: Optional[str] = None
    idToken: Optional[str] = None
    email: Optional[str] = None
    name: Optional[str] = None



class UserProfile(BaseModel):
    id: int
    email: str
    username: str
    name: Optional[str] = None
    is_premium: bool
    is_admin: bool = False
    admin_token: Optional[str] = None
    credits: int
    paid_credits: int = 0
    bonus_credits: int = 0
    credits_received: int = 5
    credits_used: int = 0
    try_ons_today: int = 0
    try_ons_limit: int = 1
    wardrobe_count: int = 0
    subscription_tier: Optional[str] = "free"
    subscription_expires_at: Optional[datetime] = None
    pref_price_drop: bool = True
    pref_style_recs: bool = True
    pref_tryon_reminders: bool = False
    photo_url: Optional[str] = None
    daily_reward_ad_count: int = 0
    has_given_5_star: bool = False
    rating_stars: Optional[int] = None
    pref_gender: Optional[str] = None
    pref_fashion_goal: Optional[str] = None
    pref_style_vibe: Optional[str] = None

class ProfileUpdateRequest(BaseModel):
    name: Optional[str] = None
    pref_price_drop: Optional[bool] = None
    pref_style_recs: Optional[bool] = None
    pref_tryon_reminders: Optional[bool] = None
    has_given_5_star: Optional[bool] = None
    rating_stars: Optional[int] = None
    pref_gender: Optional[str] = None
    pref_fashion_goal: Optional[str] = None
    pref_style_vibe: Optional[str] = None

class RateRequest(BaseModel):
    stars: int
    comment: Optional[str] = None

# ── Helpers ───────────────────────────────────────────────
async def get_user_stats(user: User, db: AsyncSession, request: Optional[Request] = None):
    now_utc = datetime.now(timezone.utc)
    today_start = now_utc.replace(hour=0, minute=0, second=0, microsecond=0)
    today_str = now_utc.strftime("%Y-%m-%d")
    
    same_day = False
    if user.last_tryon_timestamp:
        last_time = user.last_tryon_timestamp
        if getattr(last_time, "tzinfo", None) is None:
            last_time = last_time.replace(tzinfo=timezone.utc)
        if last_time.date() == now_utc.date():
            same_day = True

    user_count = (user.current_slot_tryon_count or 0) if same_day else 0

    # Expired bonus credits check (Midnight UTC reset)
    user_bonus = getattr(user, 'bonus_credits', 0) or 0
    bonus_expiry = getattr(user, 'bonus_credits_expiry', None)
    if user_bonus > 0 and bonus_expiry:
        if getattr(bonus_expiry, "tzinfo", None) is None:
            bonus_expiry = bonus_expiry.replace(tzinfo=timezone.utc)
        if bonus_expiry < now_utc:
            user.bonus_credits = 0
            await db.execute(update(User).where(User.id == user.id).values(bonus_credits=0))
            await db.commit()

    # For logged-in users, try_ons_today is strictly tracked per user account (user.current_slot_tryon_count)
    try_ons_today = user_count

    wardrobe_count = (await db.execute(select(func.count()).select_from(WardrobeItem).where(
        WardrobeItem.user_id == user.id
    ))).scalar() or 0
    
    # Base welcome credits (Master Rule 5: 2 Free Bonus Credits)
    credits_received = 2
    
    # Query successful payments
    result = await db.execute(
        select(Payment).where(Payment.user_id == user.id, Payment.status == "captured")
    )
    payments = result.scalars().all()
    
    for p in payments:
        amt = p.amount
        curr = p.currency.upper()
        
        if curr == "INR":
            if 14000 <= amt <= 18000: # Starter (₹149 / ₹165)
                credits_received += 60
            elif 70000 <= amt <= 98000: # Value (₹799 / ₹883)
                credits_received += 500
            elif 200000 <= amt <= 280000: # Business (₹2299 / ₹2540)
                credits_received += 2000
            elif 500000 <= amt <= 750000: # Enterprise (₹5999 / ₹6629)
                credits_received += 7000
            elif 7000 <= amt <= 10000: # Quick Refill (₹79)
                credits_received += 25
            elif 35000 <= amt <= 45000: # Power Up (₹399)
                credits_received += 150
        else:
            if 150 <= amt <= 250: # Starter ($1.99)
                credits_received += 60
            elif 800 <= amt <= 1100: # Value ($9.99)
                credits_received += 500
            elif 2500 <= amt <= 3500: # Business ($29.99)
                credits_received += 2000
            elif 7000 <= amt <= 9000: # Enterprise ($79.99)
                credits_received += 7000
            elif 50 <= amt <= 120: # Quick Refill ($0.99)
                credits_received += 25
            elif 400 <= amt <= 600: # Power Up ($4.99)
                credits_received += 150
                
    credits_used = max(0, credits_received - user.credits)
    
    return try_ons_today, wardrobe_count, credits_received, credits_used
async def get_current_user(
    token: str = Depends(oauth2_scheme),
    db: AsyncSession = Depends(get_db),
) -> User:
    payload = verify_token(token)
    user_id = payload.get("sub")
    if not user_id:
        raise HTTPException(status_code=401, detail="Invalid token")

    result = await db.execute(select(User).where(User.id == int(user_id)))
    user = result.scalar_one_or_none()
    if not user or not user.is_active:
        raise HTTPException(status_code=401, detail="User not found or inactive")
    return user


async def get_optional_user(
    token: Optional[str] = Depends(oauth2_scheme_optional), 
    db: AsyncSession = Depends(get_db)
) -> Optional[User]:
    """
    Optional authentication: returns User if token is valid, else None.
    Does NOT raise HTTPException if token is missing or invalid.
    """
    if not token:
        return None
    
    try:
        # 1. Decode token
        try:
            payload = verify_token(token)
        except HTTPException:
            return None
            
        user_id = payload.get("sub")
        if not user_id:
            return None
            
        # 2. Fetch user
        result = await db.execute(select(User).where(User.id == int(user_id)))
        user = result.scalar_one_or_none()
        if user and user.is_active:
            return user
    except Exception as e:
        # If it's a DB error, we must rollback to allow further queries in this request
        log.warning(f"Optional user fetch failed: {e}")
        await db.rollback()
        return None


# ── Endpoints ─────────────────────────────────────────────
@router.post("/register", response_model=LoginResponse, status_code=status.HTTP_201_CREATED)
async def register(req: RegisterRequest, request: Request, db: AsyncSession = Depends(get_db)):
    # 1. IP Based Registration Limit (Max 3 accounts per IP per day)
    cf_connecting_ip = request.headers.get("CF-Connecting-IP")
    forwarded_for = request.headers.get("X-Forwarded-For")
    real_ip = cf_connecting_ip.strip() if cf_connecting_ip else (forwarded_for.split(",")[0].strip() if forwarded_for else request.client.host)
    
    # We can't directly query users by IP since we don't store IP on User.
    # However, to stop massive bot registration, we can just use Redis or an in-memory cache.
    # For now, let's just make sure the email is completely unique and valid.
    
    # Check duplicate email
    existing = await db.execute(select(User).where(User.email == req.email))
    if existing.scalar_one_or_none():
        raise HTTPException(status_code=400, detail="Email already registered")

    # For TryZon, we use 'name' as 'username' internally if username isn't provided
    username = req.name.replace(" ", "_").lower()
    
    # Ensure unique username
    existing_u = await db.execute(select(User).where(User.username == username))
    if existing_u.scalar_one_or_none():
        import random
        username += str(random.randint(100, 999))

    _welcome_expiry = datetime.now(timezone.utc) + timedelta(hours=24)
    user = User(
        email=req.email,
        username=username,
        full_name=req.name,
        hashed_password=hash_password(req.password),
        credits=0,          # Daily free quota — starts at 0, reset logic grants 1/day
        paid_credits=0,
        bonus_credits=2,    # Rule 5: 2 Welcome bonus credits — expire in 24h
        bonus_credits_expiry=_welcome_expiry,
        welcome_bonus_given=True,
    )
    db.add(user)
    await db.commit()
    await db.refresh(user)

    token = create_access_token(
        {"sub": str(user.id)},
        expires_delta=timedelta(minutes=settings.access_token_expire_minutes),
    )

    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(user, db, request)

    return LoginResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            email=user.email,
            username=user.username,
            name=user.full_name,
            is_premium=user.is_premium,
            credits=user.credits,
            paid_credits=getattr(user, 'paid_credits', 0),
            bonus_credits=getattr(user, 'bonus_credits', 0),
            credits_received=credits_received,
            credits_used=credits_used,
            try_ons_today=try_ons_today,
            try_ons_limit=999 if user.is_premium else 1,
            wardrobe_count=wardrobe_count,
            pref_price_drop=user.pref_price_drop,
            pref_style_recs=user.pref_style_recs,
            pref_tryon_reminders=user.pref_tryon_reminders,
            photo_url=user.photo_url,
            subscription_tier=user.subscription_tier,
            subscription_expires_at=user.subscription_expires_at,
            daily_reward_ad_count=getattr(user, 'daily_reward_ad_count', 0) or 0,
            has_given_5_star=getattr(user, 'has_given_5_star', False),
            rating_stars=getattr(user, 'rating_stars', None)
        )
    )


@router.post("/login", response_model=LoginResponse)
async def login(req: LoginRequest, request: Request, db: AsyncSession = Depends(get_db)):
    result = await db.execute(select(User).where(User.email == req.email))
    user = result.scalar_one_or_none()

    safe_pwd = (req.password or "")[:72]
    if not user or not user.hashed_password or not verify_password(safe_pwd, user.hashed_password):
        raise HTTPException(status_code=401, detail="Invalid email or password")

    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account deactivated")

    token = create_access_token(
        {"sub": str(user.id)},
        expires_delta=timedelta(minutes=settings.access_token_expire_minutes),
    )

    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(user, db, request)

    return LoginResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            email=user.email,
            username=user.username,
            name=user.full_name,
            is_premium=user.is_premium,
            credits=user.credits,
            paid_credits=getattr(user, 'paid_credits', 0),
            bonus_credits=getattr(user, 'bonus_credits', 0),
            credits_received=credits_received,
            credits_used=credits_used,
            try_ons_today=try_ons_today,
            try_ons_limit=999 if user.is_premium else 1,
            wardrobe_count=wardrobe_count,
            pref_price_drop=user.pref_price_drop,
            pref_style_recs=user.pref_style_recs,
            pref_tryon_reminders=user.pref_tryon_reminders,
            photo_url=user.photo_url,
            subscription_tier=user.subscription_tier,
            subscription_expires_at=user.subscription_expires_at,
            daily_reward_ad_count=getattr(user, 'daily_reward_ad_count', 0) or 0,
            has_given_5_star=getattr(user, 'has_given_5_star', False),
            rating_stars=getattr(user, 'rating_stars', None)
        )
    )


def ensure_firebase_initialized():
    import firebase_admin
    from firebase_admin import credentials
    import os
    if not firebase_admin._apps:
        for json_path in ["firebase_account.json", "tryzonai0-firebase-adminsdk-fbsvc-b696238433.json", "backend/firebase_account.json"]:
            if os.path.exists(json_path):
                try:
                    cred = credentials.Certificate(json_path)
                    firebase_admin.initialize_app(cred)
                    log.info(f"Firebase Admin initialized with {json_path}")
                    break
                except Exception as e:
                    log.warning(f"Failed to initialize Firebase Admin with {json_path}: {e}")


def verify_google_id_token(token: str) -> Optional[dict]:
    ensure_firebase_initialized()
    try:
        decoded_token = firebase_auth.verify_id_token(token)
        return decoded_token
    except Exception as e:
        log.warning(f"Google ID token verification failed via Firebase Admin: {e}")
        try:
            import httpx
            resp = httpx.get(f"https://oauth2.googleapis.com/tokeninfo?id_token={token}", timeout=5.0)
            if resp.status_code == 200:
                data = resp.json()
                if "email" in data:
                    return data
        except Exception as ge:
            log.error(f"Fallback Google token verification failed: {ge}")
        return None


def verify_apple_id_token(token: str) -> Optional[dict]:
    ensure_firebase_initialized()
    try:
        decoded_token = firebase_auth.verify_id_token(token)
        log.info("Apple ID token successfully verified via Firebase Admin SDK")
        return decoded_token
    except Exception as e:
        log.debug(f"Firebase Admin SDK Apple token verification skipped/failed: {e}")

    try:
        import jwt
        payload = jwt.decode(token, options={"verify_signature": False})
        log.info("Apple ID token decoded via PyJWT")
        return payload
    except Exception as e:
        log.debug(f"PyJWT decode failed for Apple token: {e}")

    try:
        import base64
        import json
        parts = token.split(".")
        if len(parts) >= 2:
            p_b64 = parts[1]
            rem = len(p_b64) % 4
            if rem:
                p_b64 += "=" * (4 - rem)
            payload = json.loads(base64.urlsafe_b64decode(p_b64).decode("utf-8"))
            return payload
    except Exception as e:
        log.warning(f"Failed to parse Apple JWT token payload: {e}")
    return None


@router.post("/google", response_model=LoginResponse)
async def google_login(req: GoogleLoginRequest, request: Request, db: AsyncSession = Depends(get_db)):
    token_str = req.id_token or req.idToken
    if not token_str:
        raise HTTPException(status_code=400, detail="Missing Google ID token")
    payload = verify_google_id_token(token_str)
    if not payload:
        raise HTTPException(status_code=401, detail="Invalid Google ID token")

    email = payload.get("email")
    name = payload.get("name", "")
    photo_url = payload.get("picture")

    if not email:
        raise HTTPException(status_code=400, detail="Google token payload missing email")

    result = await db.execute(select(User).where(User.email == email))
    user = result.scalar_one_or_none()

    if not user:
        username = email.split("@")[0].lower()
        existing_u = await db.execute(select(User).where(User.username == username))
        if existing_u.scalar_one_or_none():
            import random
            username += str(random.randint(100, 999))
        
        import secrets
        dummy_password = secrets.token_hex(16)
        
        _welcome_expiry = datetime.now(timezone.utc) + timedelta(hours=24)
        user = User(
            email=email,
            username=username,
            full_name=name,
            hashed_password=hash_password(dummy_password),
            photo_url=photo_url,
            credits=0,          # Daily free quota — starts at 0
            paid_credits=0,
            bonus_credits=2,    # Rule 5: 2 Welcome bonus credits — expire in 24h
            bonus_credits_expiry=_welcome_expiry,
            welcome_bonus_given=True,
        )
        db.add(user)
        await db.commit()
        await db.refresh(user)
    else:
        updated = False
        if photo_url and user.photo_url != photo_url:
            user.photo_url = photo_url
            updated = True
        if name and user.full_name != name:
            user.full_name = name
            updated = True
        if updated:
            await db.commit()
            await db.refresh(user)

    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account deactivated")

    token = create_access_token(
        {"sub": str(user.id)},
        expires_delta=timedelta(minutes=settings.access_token_expire_minutes),
    )

    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(user, db, request)

    return LoginResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            email=user.email,
            username=user.username,
            name=user.full_name,
            credits=user.credits,
            paid_credits=getattr(user, 'paid_credits', 0) or 0,
            bonus_credits=getattr(user, 'bonus_credits', 0) or 0,
            bonus_credits_expiry=user.bonus_credits_expiry.isoformat() if getattr(user, 'bonus_credits_expiry', None) else None,
            welcome_bonus_given=getattr(user, 'welcome_bonus_given', False),
            is_premium=user.is_premium,
            try_ons_today=try_ons_today,
            try_ons_limit=1 if not user.is_premium else 999999,
            subscription_tier=getattr(user, 'subscription_tier', None),
            pref_gender=getattr(user, 'pref_gender', 'Women') or 'Women',
            is_admin=user.is_admin,
            photo_url=user.photo_url,
            daily_reward_ad_count=getattr(user, 'daily_reward_ad_count', 0) or 0,
            has_given_5_star=getattr(user, 'has_given_5_star', False),
            rating_stars=getattr(user, 'rating_stars', None)
        )
    )


@router.post("/apple", response_model=LoginResponse)
async def apple_login(req: AppleLoginRequest, request: Request, db: AsyncSession = Depends(get_db)):
    email = req.email
    name = req.name or "Apple User"
    token_str = req.id_token or req.idToken

    if token_str:
        if "@" in token_str and not email:
            email = token_str
        else:
            payload = verify_apple_id_token(token_str)
            if payload:
                if not email and payload.get("email"):
                    email = payload.get("email")
                if payload.get("name") and name == "Apple User":
                    name = payload.get("name")
                if not email and payload.get("sub"):
                    email = f"apple_{payload['sub']}@privaterelay.appleid.com"
                elif not email and payload.get("uid"):
                    email = f"apple_{payload['uid']}@privaterelay.appleid.com"

    if not email:
        raise HTTPException(status_code=400, detail="Missing Apple email or identity token")
    email = email.strip().lower()
    result = await db.execute(select(User).where(User.email == email))
    user = result.scalar_one_or_none()

    if not user:
        username = email.split("@")[0].lower()
        existing_u = await db.execute(select(User).where(User.username == username))
        if existing_u.scalar_one_or_none():
            import random
            username += str(random.randint(100, 999))
        
        import secrets
        dummy_password = secrets.token_hex(16)
        
        _welcome_expiry = datetime.now(timezone.utc) + timedelta(hours=24)
        user = User(
            email=email,
            username=username,
            full_name=name,
            hashed_password=hash_password(dummy_password),
            credits=0,
            paid_credits=0,
            bonus_credits=2,
            bonus_credits_expiry=_welcome_expiry,
            welcome_bonus_given=True,
        )
        db.add(user)
        await db.commit()
        await db.refresh(user)

    if not user.is_active:
        raise HTTPException(status_code=403, detail="Account deactivated")

    token = create_access_token(
        {"sub": str(user.id)},
        expires_delta=timedelta(minutes=settings.access_token_expire_minutes),
    )

    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(user, db, request)

    return LoginResponse(
        token=token,
        user=UserResponse(
            id=user.id,
            email=user.email,
            username=user.username,
            name=user.full_name,
            is_premium=user.is_premium,
            credits=user.credits,
            paid_credits=getattr(user, 'paid_credits', 0),
            bonus_credits=getattr(user, 'bonus_credits', 0),
            credits_received=credits_received,
            credits_used=credits_used,
            try_ons_today=try_ons_today,
            try_ons_limit=999 if user.is_premium else 1,
            wardrobe_count=wardrobe_count,
            pref_price_drop=user.pref_price_drop,
            pref_style_recs=user.pref_style_recs,
            pref_tryon_reminders=user.pref_tryon_reminders,
            photo_url=user.photo_url,
            subscription_tier=user.subscription_tier,
            subscription_expires_at=user.subscription_expires_at,
            daily_reward_ad_count=getattr(user, 'daily_reward_ad_count', 0) or 0,
            has_given_5_star=getattr(user, 'has_given_5_star', False),
            rating_stars=getattr(user, 'rating_stars', None)
        )
    )


@router.get("/me", response_model=UserProfile)
async def get_me(request: Request, current_user: User = Depends(get_current_user), db: AsyncSession = Depends(get_db)):
    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(current_user, db, request)
    is_admin = (current_user.email.lower() == settings.admin_email.lower())
    now_utc = datetime.now(timezone.utc)
    last_ad = getattr(current_user, 'last_reward_ad_timestamp', None)
    if last_ad:
        if getattr(last_ad, "tzinfo", None) is None:
            last_ad = last_ad.replace(tzinfo=timezone.utc)
        if last_ad.date() < now_utc.date():
            ad_count_today = 0
        else:
            ad_count_today = getattr(current_user, 'daily_reward_ad_count', 0) or 0
    else:
        ad_count_today = 0

    return UserProfile(
        id=current_user.id,
        email=current_user.email,
        username=current_user.username,
        is_premium=current_user.is_premium,
        is_admin=is_admin,
        admin_token=settings.admin_secret_key if is_admin else None,
        credits=current_user.credits,
        paid_credits=getattr(current_user, 'paid_credits', 0),
        bonus_credits=getattr(current_user, 'bonus_credits', 0),
        credits_received=credits_received,
        credits_used=credits_used,
        try_ons_today=try_ons_today,
        try_ons_limit=999 if current_user.is_premium else 1,
        wardrobe_count=wardrobe_count,
        subscription_tier=current_user.subscription_tier,
        subscription_expires_at=current_user.subscription_expires_at,
        pref_price_drop=current_user.pref_price_drop,
        pref_style_recs=current_user.pref_style_recs,
        pref_tryon_reminders=current_user.pref_tryon_reminders,
        photo_url=current_user.photo_url,
        daily_reward_ad_count=ad_count_today,
        has_given_5_star=getattr(current_user, 'has_given_5_star', False),
        rating_stars=getattr(current_user, 'rating_stars', None)
    )


@router.post("/claim-ad-reward")
async def claim_ad_reward(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """
    Grants +0.5 Credits for watching 1 Rewarded Video Ad (30s).
    Enforces daily hard cap of Max 8 Ads / 4 Try-Ons per day (resets at Midnight UTC).
    """
    now = datetime.now(timezone.utc)
    
    last_ad = getattr(current_user, 'last_reward_ad_timestamp', None)
    if last_ad:
        if getattr(last_ad, "tzinfo", None) is None:
            last_ad = last_ad.replace(tzinfo=timezone.utc)
        if last_ad.date() < now.date():
            current_user.daily_reward_ad_count = 0
        elif last_ad.date() == now.date():
            time_since = (now - last_ad).total_seconds()
            if time_since < 10.0:
                log.warning(f"⚠️ User {current_user.id} tried to claim ad reward too rapidly ({time_since:.1f}s since last ad). Blocked.")
                curr_cnt = getattr(current_user, 'daily_reward_ad_count', 0) or 0
                total_balance = (getattr(current_user, 'paid_credits', 0) or 0) + (getattr(current_user, 'bonus_credits', 0) or 0) + (0.5 if (curr_cnt % 2 == 1) else 0.0)
                return {
                    "status": "too_fast",
                    "message": "Please watch the video ad completely before claiming reward!",
                    "daily_reward_ad_count": curr_cnt,
                    "max_daily_ads": 8,
                    "credits_added": 0.0,
                    "total_credits": total_balance
                }
        
    current_count = getattr(current_user, 'daily_reward_ad_count', 0) or 0
    if current_count >= 8:
        total_balance = (getattr(current_user, 'paid_credits', 0) or 0) + (getattr(current_user, 'bonus_credits', 0) or 0)
        return {
            "status": "limit_reached",
            "message": "Daily ad reward limit reached (8/8 ads watched today). Buy ₹20 Starter Pack for 10 instant ad-free try-ons!",
            "daily_reward_ad_count": 8,
            "max_daily_ads": 8,
            "credits_added": 0.0,
            "total_credits": total_balance
        }
        
    new_count = current_count + 1
    current_user.daily_reward_ad_count = new_count
    current_user.last_reward_ad_timestamp = now
    
    # 0.5 Credits granted per ad (every 2 ads = 1 full bonus credit added to DB)
    if new_count % 2 == 0:
        current_user.bonus_credits = (getattr(current_user, 'bonus_credits', 0) or 0) + 1
        midnight_utc = (now + timedelta(days=1)).replace(hour=23, minute=59, second=59, microsecond=0)
        current_user.bonus_credits_expiry = midnight_utc
        
    await db.commit()
    await db.refresh(current_user)
    
    half_credit_bonus = 0.5 if (new_count % 2 == 1) else 0.0
    total_balance = (getattr(current_user, 'paid_credits', 0) or 0) + (getattr(current_user, 'bonus_credits', 0) or 0) + half_credit_bonus
    
    log.info(f"🎁 User {current_user.id} claimed ad reward ({new_count}/8 today). Total balance: {total_balance}")
    return {
        "status": "success",
        "message": f"Ad Reward Claimed! +0.5 ⚡ Credit added ({new_count}/8 ads watched today).",
        "daily_reward_ad_count": new_count,
        "max_daily_ads": 8,
        "credits_added": 0.5,
        "total_credits": total_balance
    }


@router.put("/profile", response_model=UserResponse)
async def update_profile(
    req: ProfileUpdateRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Update user profile and preferences."""
    if req.name is not None:
        current_user.full_name = req.name
    if req.pref_price_drop is not None:
        current_user.pref_price_drop = req.pref_price_drop
    if req.pref_style_recs is not None:
        current_user.pref_style_recs = req.pref_style_recs
    if req.pref_tryon_reminders is not None:
        current_user.pref_tryon_reminders = req.pref_tryon_reminders
    if req.has_given_5_star is not None:
        current_user.has_given_5_star = req.has_given_5_star
    if req.pref_gender is not None:
        current_user.pref_gender = req.pref_gender
    if req.pref_fashion_goal is not None:
        current_user.pref_fashion_goal = req.pref_fashion_goal
    if req.pref_style_vibe is not None:
        current_user.pref_style_vibe = req.pref_style_vibe
        
    await db.commit()
    await db.refresh(current_user)
    
    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(current_user, db, request)
    return UserResponse(
        id=current_user.id,
        email=current_user.email,
        username=current_user.username,
        is_premium=current_user.is_premium,
        credits=current_user.credits,
        paid_credits=getattr(current_user, 'paid_credits', 0),
        bonus_credits=getattr(current_user, 'bonus_credits', 0),
        credits_received=credits_received,
        credits_used=credits_used,
        try_ons_today=try_ons_today,
        try_ons_limit=999 if current_user.is_premium else 1,
        wardrobe_count=wardrobe_count,
        subscription_tier=current_user.subscription_tier,
        subscription_expires_at=current_user.subscription_expires_at,
        pref_price_drop=current_user.pref_price_drop,
        pref_style_recs=current_user.pref_style_recs,
        pref_tryon_reminders=current_user.pref_tryon_reminders,
        photo_url=current_user.photo_url,
        daily_reward_ad_count=getattr(current_user, 'daily_reward_ad_count', 0) or 0,
        has_given_5_star=getattr(current_user, 'has_given_5_star', False),
        rating_stars=getattr(current_user, 'rating_stars', None),
        pref_gender=getattr(current_user, 'pref_gender', None),
        pref_fashion_goal=getattr(current_user, 'pref_fashion_goal', None),
        pref_style_vibe=getattr(current_user, 'pref_style_vibe', None)
    )


@router.post("/rate", response_model=UserResponse)
async def submit_rating(
    req: RateRequest,
    request: Request,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db)
):
    """Submit user rating. Remembers stars given and sets has_given_5_star if stars == 5."""
    current_user.rating_stars = req.stars
    if req.stars == 5:
        current_user.has_given_5_star = True
    await db.commit()
    await db.refresh(current_user)
    
    try_ons_today, wardrobe_count, credits_received, credits_used = await get_user_stats(current_user, db, request)
    return UserResponse(
        id=current_user.id,
        email=current_user.email,
        username=current_user.username,
        is_premium=current_user.is_premium,
        credits=current_user.credits,
        paid_credits=getattr(current_user, 'paid_credits', 0),
        credits_received=credits_received,
        credits_used=credits_used,
        try_ons_today=try_ons_today,
        try_ons_limit=999 if current_user.is_premium else 1,
        wardrobe_count=wardrobe_count,
        subscription_tier=current_user.subscription_tier,
        subscription_expires_at=current_user.subscription_expires_at,
        pref_price_drop=current_user.pref_price_drop,
        pref_style_recs=current_user.pref_style_recs,
        pref_tryon_reminders=current_user.pref_tryon_reminders,
        photo_url=current_user.photo_url,
        daily_reward_ad_count=getattr(current_user, 'daily_reward_ad_count', 0) or 0,
        has_given_5_star=getattr(current_user, 'has_given_5_star', False),
        rating_stars=getattr(current_user, 'rating_stars', None)
    )


@router.delete("/me/history", status_code=status.HTTP_204_NO_CONTENT)
async def clear_history(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Delete all try-on history for the current user."""
    from database.models import TryOnSession
    from sqlalchemy import delete
    
    await db.execute(delete(TryOnSession).where(TryOnSession.user_id == current_user.id))
    await db.commit()
    return None


@router.delete("/me", status_code=status.HTTP_204_NO_CONTENT)
async def delete_me(
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Permanently delete user account and all associated data."""
    await db.delete(current_user)
    await db.commit()
    return None


class DeviceRegisterRequest(BaseModel):
    fcm_token: str
    device_type: str = "android"


@router.post("/me/devices", status_code=status.HTTP_201_CREATED)
async def register_device(
    req: DeviceRegisterRequest,
    current_user: User = Depends(get_current_user),
    db: AsyncSession = Depends(get_db),
):
    """Register or update an FCM device token for push notifications."""
    from database.models import UserDevice
    
    # Check if token exists
    stmt = select(UserDevice).where(UserDevice.fcm_token == req.fcm_token)
    res = await db.execute(stmt)
    existing = res.scalar_one_or_none()
    
    if existing:
        existing.user_id = current_user.id
        existing.device_type = req.device_type
        existing.is_active = True
    else:
        new_device = UserDevice(
            user_id=current_user.id,
            fcm_token=req.fcm_token,
            device_type=req.device_type
        )
        db.add(new_device)
    
    await db.commit()
    
    return {"status": "registered"}
