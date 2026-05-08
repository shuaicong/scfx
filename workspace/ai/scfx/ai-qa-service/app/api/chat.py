from fastapi import APIRouter
from fastapi.responses import StreamingResponse
from pydantic import BaseModel
from typing import Optional, List
from app.services.vector import search_vectors
from app.services.llm import generate_answer, generate_answer_stream
from app.db.qdrant import get_client
import json

COLLECTION_NAME = "grain_knowledge"

router = APIRouter(prefix="/api", tags=["chat"])

class ChatRequest(BaseModel):
    question: str
    top_k: Optional[int] = 5
    source_filter: Optional[List[str]] = None

@router.post("/chat")
async def chat(request: ChatRequest):
    # 1. 搜索相关知识
    search_results = search_vectors(
        query=request.question,
        top_k=request.top_k,
        source_filter=None  # TODO: 支持 source_filter
    )

    # 2. 构建上下文
    context_parts = []
    references = []
    for i, result in enumerate(search_results):
        context_parts.append(f"【来源 {i+1}】{result['title']} ({result.get('publish_time', '未知时间')})\n{result['content']}")
        references.append({
            "report_id": result.get('point_id'),
            "title": result['title'],
            "source": result.get('source', '未知'),
            "publish_time": result.get('publish_time'),
            "similarity": result.get('similarity', 0)
        })

    context = "\n\n".join(context_parts) if context_parts else "暂无相关资料"

    # 3. 生成回答
    answer = await generate_answer(
        question=request.question,
        context=context
    )

    return {
        "code": 200,
        "data": {
            "answer": answer,
            "references": references
        }
    }


async def sse_generator(question: str, context: str):
    """SSE流式生成器"""
    async for chunk in generate_answer_stream(question=question, context=context):
        data = json.dumps(chunk)
        yield f"data: {data}\n\n"


@router.post("/chat/stream")
async def chat_stream(request: ChatRequest):
    """流式聊天接口，返回SSE格式数据流"""
    # 1. 搜索相关知识
    search_results = search_vectors(
        query=request.question,
        top_k=request.top_k,
        source_filter=None  # TODO: 支持 source_filter
    )

    # 2. 构建上下文
    context_parts = []
    for i, result in enumerate(search_results):
        context_parts.append(f"【来源 {i+1}】{result['title']} ({result.get('publish_time', '未知时间')})\n{result['content']}")

    context = "\n\n".join(context_parts) if context_parts else "暂无相关资料"

    return StreamingResponse(
        sse_generator(question=request.question, context=context),
        media_type="text/event-stream"
    )


@router.get("/reports/{report_id}")
async def get_report(report_id: str):
    """获取报告详情 - report_id 可以是 UUID 字符串"""
    client = get_client()
    try:
        results = client.retrieve(
            collection_name=COLLECTION_NAME,
            ids=[report_id]
        )
        if results and len(results) > 0:
            r = results[0]
            return {
                "code": 200,
                "data": {
                    "id": r.id,
                    "title": r.payload.get("title"),
                    "content": r.payload.get("content"),
                    "source": r.payload.get("source"),
                    "publish_time": r.payload.get("publish_time")
                }
            }
        return {"code": 404, "message": "报告不存在"}
    except Exception as e:
        return {"code": 500, "message": str(e)}
