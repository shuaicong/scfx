from sentence_transformers import SentenceTransformer
import os
import numpy as np
import logging

logger = logging.getLogger(__name__)

MODEL_NAME = os.getenv("BGE_MODEL", "BAAI/bge-large-zh-v1.5")

_model = None
_use_mock = False

def get_model():
    global _model, _use_mock
    if _use_mock:
        return None
    if _model is None:
        # 检查模型是否已缓存到本地
        import huggingface_hub
        cached = huggingface_hub.try_to_load_from_cache(
            MODEL_NAME, "modules.json"
        )
        if cached is None or not os.path.exists(cached):
            logger.error(
                "[EMBED] BGE 模型 '%s' 未缓存到本地，需要先下载。"
                "设置 HF_HUB_OFFLINE=1 后会自动使用本地缓存。"
                "回退到随机嵌入，问答质量将严重下降！",
                MODEL_NAME,
            )
        try:
            _model = SentenceTransformer(MODEL_NAME)
        except Exception as e:
            logger.error(
                "[EMBED] BGE 模型加载失败，问答将使用随机嵌入（质量严重下降）：%s", e
            )
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
