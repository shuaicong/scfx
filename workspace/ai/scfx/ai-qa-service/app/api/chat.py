from fastapi import APIRouter

router = APIRouter(prefix="/api", tags=["chat"])

@router.post("/chat")
async def chat(question: str):
    return {"code": 200, "data": {"answer": "AI 问答功能待实现", "references": []}}
