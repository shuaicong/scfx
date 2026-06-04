from sentence_transformers import SentenceTransformer
import os
import numpy as np

MODEL_NAME = os.getenv("BGE_MODEL", "BAAI/bge-large-zh-v1.5")

_model = None
_use_mock = False

def get_model():
    global _model, _use_mock
    if _use_mock:
        return None
    if _model is None:
        try:
            _model = SentenceTransformer(MODEL_NAME)
        except Exception as e:
            print(f"Warning: Failed to load model, using mock embeddings: {e}")
            _use_mock = True
            return None
    return _model

def embed_text(text: str) -> list:
    """使用 BGE 将文本转为向量"""
    model = get_model()
    if model is None:
        # Return mock embedding (1024 dimensions for BGE-large-zh-v1.5)
        return np.random.randn(1024).tolist()
    embedding = model.encode(text)
    # 手动归一化（新版 sentence-transformers encode() 不再支持 normalize= 参数）
    norm = np.linalg.norm(embedding)
    if norm > 0:
        embedding = embedding / norm
    return embedding.tolist()

def embed_batch(texts: list) -> list:
    """批量向量化"""
    model = get_model()
    if model is None:
        # Return mock embeddings
        return [np.random.randn(1024).tolist() for _ in texts]
    embeddings = model.encode(texts)
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    norms[norms == 0] = 1  # 避免除零
    embeddings = embeddings / norms
    return [e.tolist() for e in embeddings]
