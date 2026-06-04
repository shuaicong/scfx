from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams
import os

QDRANT_HOST = os.getenv("QDRANT_HOST", "localhost")
QDRANT_PORT = int(os.getenv("QDRANT_PORT", "6333"))
QDRANT_LOCAL_PATH = os.getenv("QDRANT_LOCAL_PATH", "")
COLLECTION_NAME = "grain_knowledge"
VECTOR_SIZE = 1024

_client = None

def get_client():
    global _client
    if _client is None:
        if QDRANT_LOCAL_PATH:
            # 持久化本地模式（无需独立 Qdrant 服务器）
            _client = QdrantClient(path=QDRANT_LOCAL_PATH)
        else:
            _client = QdrantClient(host=QDRANT_HOST, port=QDRANT_PORT)
    return _client

def ensure_collection():
    client = get_client()
    collections = client.get_collections().collections
    if not any(c.name == COLLECTION_NAME for c in collections):
        client.create_collection(
            collection_name=COLLECTION_NAME,
            vectors_config=VectorParams(size=VECTOR_SIZE, distance=Distance.COSINE)
        )
    return COLLECTION_NAME
