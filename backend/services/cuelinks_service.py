"""
services/cuelinks_service.py — Cuelinks Product Feed Integration
Zero-Scraping Policy: Ingests CSV/XML feeds for Myntra, Ajio, Meesho, etc.
"""

import logging
import httpx
import pandas as pd
from io import StringIO
from sqlalchemy import select
from database.models import Product
from database.postgres import AsyncSessionLocal
from config import settings

log = logging.getLogger(__name__)

async def run_daily_cuelinks_update():
    """
    Entry point for internal scheduler. Downloads and processes the feed in chunks.
    """
    feed_url = getattr(settings, "cuelinks_feed_url", None)
    if not feed_url:
        log.warning("CUELINKS_FEED_URL not set. Skipping update.")
        return

    log.info(f"Starting Cuelinks sync from {feed_url}")
    
    try:
        async with httpx.AsyncClient(timeout=300.0) as client:
            async with client.stream("GET", feed_url) as response:
                if response.status_code != 200:
                    log.error(f"Failed to fetch Cuelinks feed: {response.status_code}")
                    return

                # Using a temporary file or streaming buffer to avoid loading all in RAM
                import tempfile
                with tempfile.NamedTemporaryFile(mode='wb', delete=True) as tmp:
                    async for chunk in response.aiter_bytes():
                        tmp.write(chunk)
                    tmp.flush()
                    
                    # Process CSV in chunks of 500 rows to keep memory low
                    chunk_size = 500
                    for chunk_df in pd.read_csv(tmp.name, chunksize=chunk_size):
                        await _process_chunk(chunk_df)
                        
        log.info("Cuelinks sync complete.")
    except Exception as e:
        log.error(f"Error during Cuelinks sync: {e}")

async def _process_chunk(df: pd.DataFrame):
    """Update products in database from a dataframe chunk."""
    async with AsyncSessionLocal() as db:
        for _, row in df.iterrows():
            try:
                # Deduplication
                name = str(row.get('product_name', ''))
                store = str(row.get('store', ''))
                if not name or not store: continue

                stmt = select(Product).where(Product.name == name, Product.store == store)
                res = await db.execute(stmt)
                product = res.scalar_one_or_none()
                
                price = float(row.get('price', 0))
                
                if not product:
                    product = Product(
                        name=name,
                        category=str(row.get('category', 'unknown')),
                        image_url=str(row.get('image_url', '')),
                        store=store,
                        affiliate_url=str(row.get('affiliate_url', '')),
                        current_price=price,
                        added_by="cuelinks_feed"
                    )
                    db.add(product)
                else:
                    product.current_price = price
                    product.affiliate_url = str(row.get('affiliate_url', ''))
            except Exception as e:
                log.debug(f"Failed to process row: {e}")
                
        await db.commit()

sync_cuelinks_products = run_daily_cuelinks_update
