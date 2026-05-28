# ai-qa-service/app/services/hybrid.py

from app.services.vector import search_vectors
from app.services.search import search_internet
from typing import List, Dict, Any

async def hybrid_search(
    query: str,
    top_k: int = 5,
    use_internet: bool = True
) -> Dict[str, Any]:
    """混合检索：知识库 + 联网搜索

    Args:
        query: 用户问题
        top_k: 知识库返回数量
        use_internet: 是否联网搜索

    Returns:
        {
            "knowledge_results": [...],  # 知识库结果
            "internet_results": [...],   # 联网结果
            "all_sources": [...]         # 合并的来源列表
        }
    """
    # 1. 知识库搜索
    knowledge_results = search_vectors(query=query, top_k=top_k)

    # 2. 联网搜索（如果启用）
    internet_results = []
    if use_internet:
        internet_data = await search_internet(query)
        internet_results = internet_data.get("results", [])

    # 3. 合并来源
    all_sources = []

    # 添加知识库来源
    for r in knowledge_results:
        all_sources.append({
            "type": "web",
            "name": r.get("source", "知识库"),
            "url": f"/api/reports/{r.get('point_id')}"
        })

    # 添加联网来源
    for r in internet_results:
        all_sources.append({
            "type": "web",
            "name": r.get("title", "网页"),
            "url": r.get("url")
        })

    return {
        "knowledge_results": knowledge_results,
        "internet_results": internet_results,
        "all_sources": all_sources
    }

def build_context(knowledge_results: List, internet_results: List) -> str:
    """构建 LLM 上下文"""
    context_parts = []

    # 知识库上下文
    if knowledge_results:
        context_parts.append("【知识库资料】")
        for i, r in enumerate(knowledge_results):
            context_parts.append(
                f"【来源 {i+1}】{r['title']} ({r.get('publish_time', '未知时间')})\n{r['content']}"
            )

    # 联网上下文
    if internet_results:
        context_parts.append("\n【网络资料】")
        for i, r in enumerate(internet_results):
            context_parts.append(
                f"【网页 {i+1}】{r['title']}\n{r['content'][:500]}"
            )

    return "\n\n".join(context_parts) if context_parts else "暂无相关资料"