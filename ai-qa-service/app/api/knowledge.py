from fastapi import APIRouter, HTTPException, Query

from app.services.vector import search_vectors, delete_vectors, store_vectors
from app.services.chunker import chunk_text
from app.services.parser import clean_text
from app.db.mysql import execute_query, execute_update

router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])

@router.get("/search")
async def search(query: str, top_k: int = 5):
    """语义检索"""
    try:
        results = search_vectors(query, top_k)
        return {"code": 200, "data": {"results": results}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("")
async def list_knowledge(page: int = 1, page_size: int = Query(default=20, ge=1, le=100)):
    """知识列表"""
    try:
        offset = (page - 1) * page_size
        sql = "SELECT * FROM t_knowledge_base ORDER BY id DESC LIMIT %s OFFSET %s"
        items = execute_query(sql, (page_size, offset))

        count_sql = "SELECT COUNT(*) as total FROM t_knowledge_base"
        total = execute_query(count_sql)[0]['total']

        return {"code": 200, "data": {"items": items, "total": total, "page": page, "page_size": page_size}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/stats")
async def stats():
    """知识库统计"""
    try:
        total_sql = "SELECT COUNT(*) as total FROM t_knowledge_base"
        total = execute_query(total_sql)[0]['total']

        by_source_sql = """
            SELECT source_name, source_type, COUNT(*) as count
            FROM t_knowledge_base
            GROUP BY source_name, source_type
        """
        by_source = execute_query(by_source_sql)

        return {"code": 200, "data": {"total": total, "by_source": by_source}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/{kb_id}")
async def get_knowledge(kb_id: int):
    """知识详情"""
    try:
        sql = "SELECT * FROM t_knowledge_base WHERE id = %s"
        items = execute_query(sql, (kb_id,))
        if not items:
            raise HTTPException(status_code=404, detail="Knowledge not found")
        return {"code": 200, "data": items[0]}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.delete("/{kb_id}")
async def delete_knowledge(kb_id: int):
    """删除知识"""
    try:
        sql = "SELECT vector_ids FROM t_knowledge_base WHERE id = %s"
        items = execute_query(sql, (kb_id,))
        if not items:
            raise HTTPException(status_code=404, detail="Knowledge not found")

        vector_ids = items[0].get('vector_ids')
        if vector_ids:
            delete_vectors(vector_ids)

        delete_sql = "DELETE FROM t_knowledge_base WHERE id = %s"
        execute_update(delete_sql, (kb_id,))

        return {"code": 200, "data": {"deleted": kb_id}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{kb_id}/revectorize")
async def revectorize(kb_id: int):
    """重新向量化：从 t_knowledge_chunk 读取切片同步到 Qdrant

    优先读取 Java DocumentPipeline 已切好的切片（含 chunk_type），
    降级：旧数据无切片时走原有 chunk_text() 逻辑。
    """
    try:
        # 1. 查询知识基本信息
        sql = "SELECT * FROM t_knowledge_base WHERE id = %s"
        items = execute_query(sql, (kb_id,))
        if not items:
            raise HTTPException(status_code=404, detail="Knowledge not found")

        item = items[0]

        # 2. 删除旧 Qdrant 向量
        old_vector_ids = item.get('vector_ids')
        if old_vector_ids:
            delete_vectors(old_vector_ids)

        # 3. 优先从 t_knowledge_chunk 读取切片
        chunks = execute_query(
            "SELECT content, chunk_index, chunk_type FROM t_knowledge_chunk "
            "WHERE knowledge_id = %s AND is_active = 1 ORDER BY chunk_index",
            (kb_id,)
        )

        if chunks:
            # ○ 有切片：使用 Java 切好的结果
            chunk_texts = [row['content'] for row in chunks]
            chunk_types = [row.get('chunk_type', 'text') or 'text' for row in chunks]
        else:
            # ○ 无切片（旧数据）：降级为 chunk_text() 自行分块
            content = clean_text(item['content'])
            chunk_texts = chunk_text(content)
            chunk_types = None
            if not chunk_texts:
                raise HTTPException(status_code=400, detail="No chunks generated")

        # 4. 写入 Qdrant
        vector_ids = store_vectors(
            kb_id=kb_id,
            title=item['title'],
            chunks=chunk_texts,
            source=item.get('source_name') or item.get('collection_source') or '未知',
            source_type=item.get('source_type', ''),
            publish_time=item.get('publish_time'),
            chunk_types=chunk_types
        )

        # 5. 回写 vector_ids
        execute_update(
            "UPDATE t_knowledge_base SET vector_ids = %s WHERE id = %s",
            (vector_ids, kb_id)
        )

        return {"code": 200, "data": {"kb_id": kb_id, "chunks": len(chunk_texts)}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
