"""AI 问答服务入口

注意：dotenv 加载必须在所有模块 import 之前执行，
否则子模块在 import 时读取 os.environ 会拿到空值。
"""
import os
from pathlib import Path
from dotenv import load_dotenv

_env_path = Path(__file__).resolve().parent.parent / "ai-qa-service.env"
if _env_path.exists():
    load_dotenv(_env_path, override=False)
    _key = os.environ.get("SILICON_FLOW_API_KEY", "")
    if _key:
        print(f"[main] 已加载 SILICON_FLOW_API_KEY (前缀={_key[:8]}..., len={len(_key)})")
    else:
        print("[main] 警告: SILICON_FLOW_API_KEY 未设置，AI 问答将使用演示模式")
del _env_path, _key, load_dotenv

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware
from app.api.knowledge import router as knowledge_router
from app.api.chat import router as chat_router

app = FastAPI(title="AI QA Service", version="1.0.0")

app.add_middleware(
    CORSMiddleware,
    allow_origins=["*"],
    allow_credentials=False,
    allow_methods=["*"],
    allow_headers=["*"],
)

app.include_router(knowledge_router)
app.include_router(chat_router)

@app.get("/health")
async def health():
    return {"status": "ok"}

if __name__ == "__main__":
    import uvicorn
    uvicorn.run(app, host="0.0.0.0", port=5002)
