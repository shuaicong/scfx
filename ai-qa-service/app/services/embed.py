import os
import numpy as np
import logging
import httpx

logger = logging.getLogger(__name__)

MODEL_NAME = os.getenv("BGE_MODEL", "BAAI/bge-large-zh-v1.5")
SILICON_FLOW_KEY = os.getenv("SILICON_FLOW_API_KEY", "")
EMBED_API_URL = os.getenv("EMBED_API_URL", "https://api.siliconflow.cn/v1/embeddings")

_model = None
_use_mock = False
_use_api = False  # 使用 SiliconFlow API 替代本地模型

# sentence_transformers 可能因 torch 缺失而不可用，降级到 API
try:
    from sentence_transformers import SentenceTransformer
    _sentence_transformers_available = True
except ImportError:
    SentenceTransformer = None  # type: ignore
    _sentence_transformers_available = False
    if SILICON_FLOW_KEY:
        _use_api = True
        logger.info("[EMBED] sentence_transformers 不可用，切换到 SiliconFlow API 嵌入")
    else:
        logger.warning("[EMBED] sentence_transformers 不可用且无 API Key，将使用 mock 嵌入")

def get_model():
    global _model, _use_mock
    if _use_mock or not _sentence_transformers_available:
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

def _api_embed_text(text: str) -> list:
    """通过 SiliconFlow API 获取文本向量"""
    if not SILICON_FLOW_KEY:
        return np.random.randn(1024).tolist()
    try:
        resp = httpx.post(
            EMBED_API_URL,
            json={"model": MODEL_NAME, "input": text},
            headers={"Authorization": f"Bearer {SILICON_FLOW_KEY}"},
            timeout=30,
        )
        resp.raise_for_status()
        data = resp.json()
        embedding = data["data"][0]["embedding"]
        # 归一化
        norm = np.linalg.norm(embedding)
        if norm > 0:
            embedding = (np.array(embedding) / norm).tolist()
        return embedding
    except Exception as e:
        logger.error("[EMBED] API 嵌入失败，回退到 mock: %s", e)
        return np.random.randn(1024).tolist()

def _api_embed_batch(texts: list) -> list:
    """批量通过 API 向量化"""
    if not SILICON_FLOW_KEY:
        return [np.random.randn(1024).tolist() for _ in texts]
    try:
        resp = httpx.post(
            EMBED_API_URL,
            json={"model": MODEL_NAME, "input": texts},
            headers={"Authorization": f"Bearer {SILICON_FLOW_KEY}"},
            timeout=60,
        )
        resp.raise_for_status()
        data = resp.json()
        results = sorted(data["data"], key=lambda x: x["index"])
        embeddings = [r["embedding"] for r in results]
        # 归一化
        norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
        norms[norms == 0] = 1
        embeddings = (np.array(embeddings) / norms).tolist()
        return embeddings
    except Exception as e:
        logger.error("[EMBED] API 批量嵌入失败，回退到 mock: %s", e)
        return [np.random.randn(1024).tolist() for _ in texts]

def embed_text(text: str) -> list:
    """将文本转为向量（优先本地 BGE，其次 API，最后 mock）"""
    if _use_api:
        return _api_embed_text(text)
    model = get_model()
    if model is None:
        return np.random.randn(1024).tolist()
    embedding = model.encode(text)
    norm = np.linalg.norm(embedding)
    if norm > 0:
        embedding = embedding / norm
    return embedding.tolist()

def embed_batch(texts: list) -> list:
    """批量向量化（优先本地 BGE，其次 API，最后 mock）"""
    if _use_api:
        return _api_embed_batch(texts)
    model = get_model()
    if model is None:
        return [np.random.randn(1024).tolist() for _ in texts]
    embeddings = model.encode(texts)
    norms = np.linalg.norm(embeddings, axis=1, keepdims=True)
    norms[norms == 0] = 1  # 避免除零
    embeddings = embeddings / norms
    return [e.tolist() for e in embeddings]
