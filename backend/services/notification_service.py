import logging
import os
from typing import List, Optional

import firebase_admin
from firebase_admin import credentials, messaging
from config import settings

log = logging.getLogger(__name__)

# Initialize Firebase Admin SDK
def init_firebase():
    if not firebase_admin._apps:
        try:
            cred_path = os.getenv("FIREBASE_CREDENTIALS") or settings.firebase_credentials
            if os.path.exists(cred_path):
                cred = credentials.Certificate(cred_path)
                firebase_admin.initialize_app(cred)
                log.info("🔥 Firebase Admin initialized successfully")
            else:
                log.warning(f"⚠️ Firebase credentials not found at {cred_path}. Push notifications will be disabled.")
        except Exception as e:
            log.error(f"❌ Failed to initialize Firebase: {e}")

init_firebase()

# Global concurrency locks & bounded thread pool to prevent thread explosions and socket exhaustion
import asyncio
import concurrent.futures
_PUSH_DISPATCH_LOCK = asyncio.Lock()
_FCM_EXECUTOR = concurrent.futures.ThreadPoolExecutor(max_workers=2, thread_name_prefix="fcm_worker")

async def send_push_notification(
    fcm_tokens: List[str],
    title: str,
    body: str,
    data: Optional[dict] = None,
    image_url: Optional[str] = None
) -> bool:
    """
    Send push notifications to multiple devices via FCM with 500-token batching.
    Uses sequential batches and strict timeouts to prevent thread explosion and socket starvation.
    """
    if image_url and image_url.startswith("/"):
        image_url = settings.website_url + image_url

    if not firebase_admin._apps:
        log.warning("Push failed: Firebase not initialized")
        return False

    if not fcm_tokens:
        return False

    # Unique & clean token list
    clean_tokens = list(set([t for t in fcm_tokens if t and len(t) > 10]))
    if not clean_tokens:
        return False

    payload_data = {
        "title": title,
        "body": body,
        "from_notification": "true",
    }
    if image_url:
        payload_data["image_url"] = image_url
        payload_data.setdefault("result_url", image_url)
    if data:
        # Convert all data values to strings for FCM compatibility
        for k, v in data.items():
            payload_data[str(k)] = str(v)

    android_config = messaging.AndroidConfig(
        priority='high',
        data=payload_data,
        notification=messaging.AndroidNotification(
            title=title,
            body=body,
            image=image_url,
            sound='default',
            channel_id='tryzon_notifications'
        )
    )

    webpush_config = messaging.WebpushConfig(
        notification=messaging.WebpushNotification(
            title=title,
            body=body,
            image=image_url,
            icon="/logo192.png"
        )
    )

    notification_obj = messaging.Notification(
        title=title,
        body=body,
        image=image_url
    )

    # Firebase Admin SDK allows max 500 tokens, use 100 per chunk
    chunk_size = 100
    chunks = [clean_tokens[i:i + chunk_size] for i in range(0, len(clean_tokens), chunk_size)]
    total_chunks = len(chunks)

    def _sync_send_batch(batch_tokens):
        batch_msg = messaging.MulticastMessage(
            tokens=batch_tokens,
            notification=notification_obj,
            data=payload_data,
            android=android_config,
            webpush=webpush_config
        )
        return messaging.send_each_for_multicast(batch_msg)

    async with _PUSH_DISPATCH_LOCK:
        log.info(f"🚀 Launching FCM dispatch to {len(clean_tokens)} devices across {total_chunks} batches...")
        total_success = 0
        total_failure = 0
        all_uninstalled_tokens = []
        loop = asyncio.get_running_loop()

        for idx, chunk in enumerate(chunks):
            try:
                # Run batch in bounded thread pool with 30s timeout
                response = await asyncio.wait_for(
                    loop.run_in_executor(_FCM_EXECUTOR, _sync_send_batch, chunk),
                    timeout=30.0
                )
                succ = response.success_count
                fail = response.failure_count
                total_success += succ
                total_failure += fail
                log.info(f"  └ Batch {idx + 1}/{total_chunks} completed: {succ} succeeded, {fail} failed.")

                for r_idx, resp in enumerate(response.responses):
                    if not resp.success and resp.exception:
                        err_msg = str(resp.exception).lower()
                        if "not-registered" in err_msg or "unregistered" in err_msg or "invalid-argument" in err_msg:
                            all_uninstalled_tokens.append(chunk[r_idx])
            except asyncio.TimeoutError:
                log.warning(f"⚠️ FCM batch {idx + 1}/{total_chunks} timed out after 30s, skipping.")
                total_failure += len(chunk)
            except Exception as e:
                log.error(f"Error sending multicast message batch {idx + 1}/{total_chunks}: {e}")
                total_failure += len(chunk)

            # Pacing yield between batches to keep event loop 100% responsive
            await asyncio.sleep(0.1)

    # Bulk deactivate uninstalled tokens
    if all_uninstalled_tokens:
        try:
            from database.postgres import AsyncSessionLocal
            from database.models import UserDevice
            from sqlalchemy import update
            async with AsyncSessionLocal() as session:
                await session.execute(
                    update(UserDevice).where(UserDevice.fcm_token.in_(all_uninstalled_tokens)).values(is_active=False)
                )
                await session.commit()
            log.info(f"🗑️ Deactivated {len(all_uninstalled_tokens)} uninstalled/invalid FCM tokens in DB.")
        except Exception as db_err:
            log.error(f"Failed to deactivate uninstalled tokens: {db_err}")

    log.info(f"✅ FCM Push Notification Summary: {total_success} delivered, {total_failure} failed out of {len(clean_tokens)} total devices.")
    return total_success > 0

async def notify_user(
    user_id: int,
    title: str,
    body: str,
    data: Optional[dict] = None,
    image_url: Optional[str] = None,
    db=None
):
    """
    Helper to send notification to all registered devices of a specific user.
    """
    from sqlalchemy import select
    from database.models import UserDevice
    
    if not db:
        from database.postgres import AsyncSessionLocal
        async with AsyncSessionLocal() as session:
            stmt = select(UserDevice.fcm_token).where(
                UserDevice.user_id == user_id, 
                UserDevice.is_active == True
            )
            res = await session.execute(stmt)
            tokens = [r for r in res.scalars().all()]
    else:
        stmt = select(UserDevice.fcm_token).where(
            UserDevice.user_id == user_id, 
            UserDevice.is_active == True
        )
        res = await db.execute(stmt)
        tokens = [r for r in res.scalars().all()]

    if tokens:
        await send_push_notification(tokens, title, body, data, image_url)
    else:
        log.debug(f"No active tokens found for user {user_id}")
