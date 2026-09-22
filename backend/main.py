"""
main.py — TryZon AI Master Server Entry Point
FastAPI app with CORS, middleware, router registration, and APScheduler.
"""

import logging
import os
from contextlib import asynccontextmanager

import structlog
from apscheduler.schedulers.asyncio import AsyncIOScheduler
from fastapi import FastAPI, Request
from fastapi.middleware.cors import CORSMiddleware
from fastapi.responses import JSONResponse
import mimetypes
mimetypes.add_type("image/webp", ".webp")
from fastapi.staticfiles import StaticFiles

from config import settings
from database.postgres import init_db
from routers import auth, tryon, stylist, price, feed, video, wardrobe, payments, admin, magic_closet, pinterest, stripe_payments, paypal_payments, reports, analytics
from tasks.price_updater import refresh_all_prices
from tasks.feed_refresher import refresh_all_feeds
from tasks.catalog_tasks import auto_discover_products
from tasks.ingest_tasks import auto_ingest_job
from tasks.pinterest_tasks import run_pinterest_bot_job
from tasks.cleanup_tasks import cleanup_zombie_sessions, cleanup_old_results, purge_comfyui_temp_previews
from tasks.scheduler import reset_daily_credits
from utils.push_service import init_firebase
from services.catalog_watchdog import start_watchdog

BASE_PATH = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))

# ── Logging ───────────────────────────────────────────────
structlog.configure(
    processors=[
        structlog.processors.TimeStamper(fmt="iso"),
        structlog.dev.ConsoleRenderer(),
    ]
)
log = structlog.get_logger()

# ── Scheduler ─────────────────────────────────────────────
scheduler = AsyncIOScheduler()


@asynccontextmanager
async def lifespan(app: FastAPI):
    """Startup / shutdown lifecycle."""
    log.info("🚀 TryZon AI Master Server starting", version=settings.app_version)

    # Initialize database tables
    await init_db()
    log.info("✅ PostgreSQL connected and tables ready")

    # Initialize Firebase
    init_firebase()

    # ── Job Staggering (Prevent OOM on Startup) ──────────────────
    from datetime import datetime, timedelta, timezone
    now = datetime.now(timezone.utc)

    # 1. Price Refresh: 4 AM Cron (Standard)
    scheduler.add_job(refresh_all_prices, "cron", hour=4, minute=0, misfire_grace_time=300)
    
    # 2. Feed Refresh: Interval (Wait 2 mins after startup)
    scheduler.add_job(
        refresh_all_feeds, 
        "interval", 
        hours=6, 
        next_run_time=now + timedelta(minutes=2),
        misfire_grace_time=600
    )
    
    # 3. Discovery: Interval (Wait 15 mins after startup)
    scheduler.add_job(
        auto_discover_products, 
        "interval", 
        hours=48, 
        next_run_time=now + timedelta(minutes=15),
        misfire_grace_time=3600
    )
    
    # 4. Pinterest: 8 AM Cron (Standard)
    scheduler.add_job(run_pinterest_bot_job, "cron", hour=8, minute=0, misfire_grace_time=1800)
    
    # 5. AI Catalog Auto-Ingest: Every 1 minute
    scheduler.add_job(
        auto_ingest_job,
        "interval",
        minutes=1,
        next_run_time=now + timedelta(seconds=10), # Start almost immediately
        misfire_grace_time=600
    )
    
    # 6. Cleanup: Interval (Zombie sessions + Old results)
    scheduler.add_job(cleanup_zombie_sessions, "interval", minutes=5, next_run_time=now + timedelta(seconds=30))
    scheduler.add_job(cleanup_old_results, "interval", minutes=15, next_run_time=now + timedelta(seconds=60))

    # 7. Midnight Credit Reset
    scheduler.add_job(reset_daily_credits, "cron", hour=0, minute=0, misfire_grace_time=3600)

    # 8. Start Catalog Watchdog for instant ingestion
    import asyncio
    loop = asyncio.get_running_loop()
    app.state.observer = start_watchdog(loop)

    scheduler.start()
    log.info("✅ APScheduler started (Staggered Mode) — All heavy jobs delayed to prevent startup OOM")

    yield  # ─── app is running ───

    app.state.observer.stop()
    app.state.observer.join()
    scheduler.shutdown(wait=False)
    log.info("🛑 TryZon AI Master Server stopped")


# ── App Instance ──────────────────────────────────────────
app = FastAPI(
    title="TryZon AI Master Server",
    description="Virtual try-on, AI stylist, price comparison, and personalised feed APIs.",
    version=settings.app_version,
    docs_url="/docs",
    redoc_url="/redoc",
    lifespan=lifespan,
)

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"], # Allow all for seamless mobile/web/android connectivity
    allow_credentials=True,
    allow_methods=["*"],
    allow_headers=["*", "Admin-Token"],
)


@app.middleware("http")
async def admin_protection(request: Request, call_next):
    """Protect admin routes with secret token"""
    if request.url.path.startswith("/api/v1/admin") and \
       not request.url.path.endswith("/login") and \
       request.method != "OPTIONS":
        admin_token = request.headers.get("Admin-Token")
        
        # Simple check against configured secret
        if not admin_token or admin_token != settings.admin_secret_key:
            return JSONResponse(
                status_code=403,
                content={"detail": "Forbidden: Admin access required"}
            )
            
    return await call_next(request)


# ── Global Exception Handler ──────────────────────────────
@app.exception_handler(Exception)
async def global_exception_handler(request: Request, exc: Exception):
    log.error("Unhandled exception", path=request.url.path, error=str(exc))
    # Return JSON instead of plain text to avoid frontend parsing errors
    return JSONResponse(
        status_code=500,
        content={
            "detail": "Internal server error. Please try again.",
            "type": "unhandled_exception",
            "path": request.url.path
        },
    )


@app.exception_handler(500)
async def internal_server_error_handler(request: Request, exc: Exception):
    log.error("Internal Server Error (500)", path=request.url.path, error=str(exc))
    return JSONResponse(
        status_code=500,
        content={
            "detail": "Internal server error. Please try again.",
            "type": "server_error"
        },
    )


# ── Routers ───────────────────────────────────────────────
app.include_router(auth.router,    prefix="/api/v1/auth",    tags=["Auth"])
app.include_router(tryon.router,   prefix="/api/v1",         tags=["Try-On"])
app.include_router(stylist.router, prefix="/api/v1",         tags=["Stylist"])
app.include_router(price.router,   prefix="/api/v1",         tags=["Price"])
app.include_router(feed.router,    prefix="/api/v1",         tags=["Feed"])
app.include_router(wardrobe.router, prefix="/api/v1", tags=["wardrobe"])
app.include_router(magic_closet.router, prefix="/api/v1", tags=["magic-closet"])
app.include_router(payments.router, prefix="/api/v1",        tags=["Payments"])
app.include_router(stripe_payments.router, prefix="/api/v1", tags=["Stripe"])
app.include_router(paypal_payments.router, prefix="/api/v1", tags=["PayPal"])
app.include_router(video.router,   prefix="/api/v1",         tags=["Video [Phase 2]"])
app.include_router(reports.router,          prefix="/api/v1",         tags=["Reports"])
app.include_router(admin.router,                             tags=["Admin"])
app.include_router(analytics.router, prefix="/api/v1", tags=["Analytics"])
app.include_router(analytics.router, prefix="/api", tags=["Analytics Legacy"])
app.include_router(pinterest.router)


# ── Static Serving ─────────────────────────────────────────
import os
os.makedirs(settings.output_dir, exist_ok=True)
os.makedirs(settings.upload_dir, exist_ok=True)
app.mount("/outputs", StaticFiles(directory=settings.output_dir), name="outputs")
app.mount("/api/v1/outputs", StaticFiles(directory=settings.output_dir), name="outputs_legacy")
app.mount("/uploads", StaticFiles(directory=settings.upload_dir), name="uploads")
app.mount("/api/v1/uploads", StaticFiles(directory=settings.upload_dir), name="uploads_legacy")
app.mount("/inputs", StaticFiles(directory=settings.upload_dir), name="inputs")
app.mount("/api/v1/inputs", StaticFiles(directory=settings.upload_dir), name="inputs_legacy")
app.mount("/outfits", StaticFiles(directory=f"{BASE_PATH}/web/public/outfits"), name="outfits")
app.mount("/api/v1/outfits", StaticFiles(directory=f"{BASE_PATH}/web/public/outfits"), name="outfits_legacy")
app.mount("/hero", StaticFiles(directory=f"{BASE_PATH}/web/public/hero"), name="hero")
app.mount("/api/v1/hero", StaticFiles(directory=f"{BASE_PATH}/web/public/hero"), name="hero_api")



# ── Health Check ──────────────────────────────────────────
@app.api_route("/api/v1/health", methods=["GET", "HEAD"], tags=["System"])
async def health():
    return {
        "status": "ok",
        "service": settings.app_name,
        "version": settings.app_version,
        "environment": settings.environment,
    }


@app.get("/api/v1/config/app-version", tags=["System"])
async def get_app_version():
    return {
        "latest_version_code": 73,
        "latest_version_name": "2.0.73",
        "min_required_version_code": 70,
        "force_update": True,
        "update_title": "🚨 Mandatory Update Required",
        "update_message": "A critical performance and security update (v2.0.73) is required to continue using TryZon AI.",
        "play_store_url": "https://play.google.com/store/apps/details?id=com.jagtarapvtltd.tryzonai"
    }


@app.get("/", tags=["System"])
async def root():
    return {
        "message": "TryZon AI Master Server is running!",
        "docs": "/docs",
        "version": settings.app_version,
    }


# ── Entry Point ───────────────────────────────────────────
if __name__ == "__main__":
    import uvicorn
    uvicorn.run(
        "main:app",
        host=settings.host,
        port=settings.port,
        reload=settings.debug,
        log_level="info",
    )
