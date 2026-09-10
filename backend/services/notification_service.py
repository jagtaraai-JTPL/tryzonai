import logging
import firebase_admin
from firebase_admin import credentials, messaging
from typing import List, Optional
from config import settings
import os

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

async def send_push_notification(
    fcm_tokens: List[str],
    title: str,
    body: str,
    data: Optional[dict] = None,
    image_url: Optional[str] = None
):
    """
    Send push notifications to multiple devices via FCM.
    """
    if image_url and image_url.startswith("/"):
        image_url = settings.website_url + image_url

    if not firebase_admin._apps:
        log.warning("Push failed: Firebase not initialized")
        return False

    if not fcm_tokens:
        return False

    # Create Message object
    # Android specific config for high priority and images
    android_config = messaging.AndroidConfig(
        priority='high',
        notification=messaging.AndroidNotification(
            image=image_url
        )
    )

    # Web specific config
    webpush_config = messaging.WebpushConfig(
        notification=messaging.WebpushNotification(
            image=image_url,
            icon="/logo192.png"
        )
    )

    message = messaging.MulticastMessage(
        tokens=fcm_tokens,
        notification=messaging.Notification(
            title=title,
            body=body,
            image=image_url
        ),
        data=data,
        android=android_config,
        webpush=webpush_config
    )

    try:
        response = messaging.send_multicast(message)
        log.info(f"Successfully sent {response.success_count} notifications. Failed: {response.failure_count}")
        
        # You could handle token cleanup here if response.failure_count > 0
        # by checking response.responses[i].exception for 'invalid-registration-token'
        
        return True
    except Exception as e:
        log.error(f"Error sending multicast message: {e}")
        return False

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
