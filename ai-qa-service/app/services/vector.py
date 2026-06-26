import uuid
import re
import requests
import os
from app.db.qdrant import get_client, ensure_collection

COLLECTION_NAME = "grain_knowledge"
QDRANT_URL = f"http://{os.getenv('QDRANT_HOST', 'localhost')}:{os.getenv('QDRANT_PORT', '6333')}"

def store_vectors(kb_id: int, title: str, chunks: list, source: str, source_type: str, publish_time: str = None, chunk_types: list = None):
    """存储向量到 Qdrant"""
    client = get_client()
    ensure_collection()

    vector_ids = []
    points = []

    for i, chunk in enumerate(chunks):
        from app.services.embed import embed_text
        vector = embed_text(chunk)

        point_id = str(uuid.uuid4())
        vector_ids.append(point_id)

        payload = {
            "kb_id": kb_id,
            "title": title,
            "content": chunk,
            "source": source,
            "source_type": source_type,
            "publish_time": publish_time,
            "chunk_index": i,
            "chunk_type": chunk_types[i] if chunk_types else "text"
        }

        points.append({
            "id": point_id,
            "vector": vector,
            "payload": payload
        })

    client.upsert(collection_name=COLLECTION_NAME, points=points)
    return ",".join(vector_ids)

def search_vectors(query: str, top_k: int = 5, source_filter: str = None) -> list:
    """搜索向量（使用 Qdrant REST API，兼容新旧版本）"""
    from app.services.embed import embed_text

    try:
        query_vector = embed_text(query)
    except Exception as e:
        logger = __import__('logging').getLogger(__name__)
        logger.warning("[VECTOR] embed_text failed, returning empty: %s", e)
        return []

    payload = {
        "vector": query_vector,
        "limit": top_k,
        "with_payload": True,
    }
    if source_filter:
        payload["filter"] = {"must": [{"key": "source", "match": {"value": source_filter}}]}

    try:
        resp = requests.post(
            f"{QDRANT_URL}/collections/{COLLECTION_NAME}/points/search",
            json=payload,
            timeout=10,
        )
        resp.raise_for_status()
        data = resp.json()
        results = data.get("result", [])
    except Exception as e:
        logger = __import__('logging').getLogger(__name__)
        logger.warning("[VECTOR] qdrant search failed, returning empty: %s", e)
        return []

    return [
        {
            "kb_id": r["payload"].get("knowledge_id") or r["payload"].get("kb_id"),
            "title": r["payload"]["title"],
            "content": r["payload"]["content"],
            "source": r["payload"]["source"],
            "publish_time": r["payload"].get("publish_time"),
            "similarity": r["score"],
            "chunk_index": r["payload"].get("chunk_index"),
            "chunk_type": r["payload"].get("chunk_type"),
            "point_id": r["id"],
        }
        for r in results
    ]

def delete_vectors(vector_ids: str):
    """删除向量，自动跳过非 UUID 格式的 ID（如 SiliconFlow ID）"""
    if not vector_ids:
        return
    client = get_client()
    ids = vector_ids.split(",")
    # 过滤：只保留合法 UUID 格式的 ID（Qdrant 要求 UUID 或整数）
    uuid_pattern = re.compile(
        r'^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$',
        re.IGNORECASE
    )
    valid_ids = [i for i in ids if uuid_pattern.match(i.strip())]
    if not valid_ids:
        return
    from qdrant_client.http import models
    client.delete(
        collection_name=COLLECTION_NAME,
        points_selector=models.PointIdsList(
            points=valid_ids
        )
    )
