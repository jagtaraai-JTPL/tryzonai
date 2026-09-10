import logging
import asyncio
import os
from watchdog.observers import Observer
from watchdog.events import FileSystemEventHandler
from tasks.ingest_tasks import auto_ingest_job

log = logging.getLogger(__name__)

class CatalogInboxHandler(FileSystemEventHandler):
    def __init__(self, loop):
        self.loop = loop

    def on_created(self, event):
        if not event.is_directory:
            log.info(f"✨ New file detected in catalog_inbox: {event.src_path}")
            # Trigger ingestion immediately
            asyncio.run_coroutine_threadsafe(auto_ingest_job(), self.loop)

    def on_moved(self, event):
        if not event.is_directory:
            log.info(f"✨ File moved to catalog_inbox: {event.dest_path}")
            asyncio.run_coroutine_threadsafe(auto_ingest_job(), self.loop)

def start_watchdog(loop):
    BASE_PATH = "/app" if os.path.exists("/app/web") else "/home/hemendramehra/tryzonai_server"
    INBOX_DIR = f"{BASE_PATH}/catalog_inbox"
    
    os.makedirs(INBOX_DIR, exist_ok=True)
    
    event_handler = CatalogInboxHandler(loop)
    observer = Observer()
    observer.schedule(event_handler, INBOX_DIR, recursive=True)
    observer.start()
    log.info(f"👀 Catalog Watchdog started monitoring: {INBOX_DIR}")
    return observer
