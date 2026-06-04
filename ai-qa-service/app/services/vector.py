import uuid
from app.db.qdrant import get_client, ensure_collection

COLLECTION_NAME = "grain_knowledge"

def store_vectors(kb_id: int, title: str, chunks: list, source: str, source_type: str, publish_time: str = None):
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
            "chunk_index": i
        }

        points.append({
            "id": point_id,
            "vector": vector,
            "payload": payload
        })

    client.upsert(collection_name=COLLECTION_NAME, points=points)
    return ",".join(vector_ids)

def search_vectors(query: str, top_k: int = 5, source_filter: str = None) -> list:
    """搜索向量"""
    from app.services.embed import embed_text

    client = get_client()
    query_vector = embed_text(query)

    results = client.search(
        collection_name=COLLECTION_NAME,
        query_vector=query_vector,
        limit=top_k
    )

    return [
        {
            "kb_id": r.payload.get("knowledge_id") or r.payload.get("kb_id"),
            "title": r.payload["title"],
            "content": r.payload["content"],
            "source": r.payload["source"],
            "publish_time": r.payload.get("publish_time"),
            "similarity": r.score,
            "chunk_index": r.payload.get("chunk_index"),
            "chunk_type": r.payload.get("chunk_type"),
            "point_id": r.id,
        }
        for r in results
    ]

def delete_vectors(vector_ids: str):
    """删除向量"""
    if not vector_ids:
        return
    client = get_client()
    ids = vector_ids.split(",")
    client.delete(collection_name=COLLECTION_NAME, point_ids=ids)
