from sentence_transformers import SentenceTransformer
import os

MODEL_NAME = os.getenv("BGE_MODEL", "BAAI/bge-large-zh-v1.5")

_model = None

def get_model():
    global _model
    if _model is None:
        _model = SentenceTransformer(MODEL_NAME)
    return _model

def embed_text(text: str) -> list:
    """使用 BGE 将文本转为向量"""
    model = get_model()
    embedding = model.encode(text, normalize=True)
    return embedding.tolist()

def embed_batch(texts: list) -> list:
    """批量向量化"""
    model = get_model()
    embeddings = model.encode(texts, normalize=True)
    return [e.tolist() for e in embeddings]
