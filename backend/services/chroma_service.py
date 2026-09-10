"""
services/chroma_service.py — ChromaDB vector store operations
Manages product embeddings and user interest vectors.
"""

import json
import logging
import uuid
from typing import Optional

from database.chroma import get_chroma_client, COLLECTION_PRODUCTS, COLLECTION_INTERESTS, COLLECTION_WARDROBE

log = logging.getLogger(__name__)


# ── Products ──────────────────────────────────────────────

def upsert_product(
    product_id: int,
    name: str,
    category: str,
    embedding: list[float],
    metadata: Optional[dict] = None,
):
    """Upsert a product embedding in ChromaDB."""
    client = get_chroma_client()
    if not client: return
    col = client.get_or_create_collection(COLLECTION_PRODUCTS)
    col.upsert(
        ids=[str(product_id)],
        embeddings=[embedding],
        documents=[name],
        metadatas=[{
            "category": category,
            **(metadata or {}),
        }],
    )


def search_products(query_embedding: list[float], top_k: int = 10, metadata_filter: Optional[dict] = None) -> list[dict]:
    """Find similar products from ChromaDB by embedding with optional metadata filtering."""
    client = get_chroma_client()
    if not client: return []
    try:
        col = client.get_or_create_collection(COLLECTION_PRODUCTS)
        
        # Build ChromaDB filter
        where = None
        if metadata_filter:
            if len(metadata_filter) == 1:
                where = metadata_filter
            else:
                where = {"$and": [{k: v} for k, v in metadata_filter.items()]}

        results = col.query(
            query_embeddings=[query_embedding],
            n_results=top_k,
            where=where,
            include=["documents", "metadatas", "distances"],
        )
        items = []
        for i, doc_id in enumerate(results["ids"][0]):
            items.append({
                "product_id": doc_id,
                "name": results["documents"][0][i],
                "metadata": results["metadatas"][0][i],
                "score": 1 - results["distances"][0][i],  # cosine similarity
            })
        return items
    except Exception as e:
        log.error(f"Chroma search_products error: {e}")
        return []


def get_product_embedding(product_id: int) -> Optional[list[float]]:
    """Retrieve stored embedding for a product."""
    client = get_chroma_client()
    if not client: return None
    try:
        col = client.get_or_create_collection(COLLECTION_PRODUCTS)
        results = col.get(ids=[str(product_id)], include=["embeddings"])
        if results["embeddings"]:
            return results["embeddings"][0]
    except Exception as e:
        log.error(f"Chroma get_product_embedding error: {e}")
    return None


def delete_product_embedding(product_id: int):
    """Delete a product from ChromaDB."""
    client = get_chroma_client()
    if not client: return
    col = client.get_or_create_collection(COLLECTION_PRODUCTS)
    col.delete(ids=[str(product_id)])


# ── User Interests ────────────────────────────────────────

def upsert_user_interest(
    user_id: int,
    interest_embedding: list[float],
    tags: list[str],
):
    """Store/update a user's interest vector from their activity."""
    client = get_chroma_client()
    if not client: return
    col = client.get_or_create_collection(COLLECTION_INTERESTS)
    col.upsert(
        ids=[str(user_id)],
        embeddings=[interest_embedding],
        documents=[json.dumps(tags)],
        metadatas=[{"user_id": user_id, "tags": json.dumps(tags)}],
    )


def get_user_interest_embedding(user_id: int) -> Optional[list[float]]:
    """Retrieve stored interest embedding for a user."""
    client = get_chroma_client()
    if not client: return None
    try:
        col = client.get_or_create_collection(COLLECTION_INTERESTS)
        results = col.get(ids=[str(user_id)], include=["embeddings"])
        if results["embeddings"]:
            return results["embeddings"][0]
    except Exception as e:
        log.error(f"Chroma get_user_interest_embedding error: {e}")
    return None


def get_personalised_feed(user_id: int, top_k: int = 20) -> list[dict]:
    """
    Get personalised product recommendations based on user interest vector.
    Falls back to random top products if no interest vector found.
    """
    embedding = get_user_interest_embedding(user_id)
    if not embedding:
        return []

    return search_products(embedding, top_k=top_k)


# ── Wardrobe ──────────────────────────────────────────────

def add_wardrobe_embedding(
    wardrobe_item_id: int,
    user_id: int,
    embedding: list[float],
    tags: list[str],
) -> str:
    """Add a wardrobe item's CLIP embedding to ChromaDB."""
    doc_id = f"wardrobe_{wardrobe_item_id}"
    client = get_chroma_client()
    if not client: return doc_id
    col = client.get_or_create_collection(COLLECTION_WARDROBE)
    col.upsert(
        ids=[doc_id],
        embeddings=[embedding],
        documents=[json.dumps(tags)],
        metadatas=[{"user_id": user_id, "item_id": wardrobe_item_id}],
    )
    return doc_id
