from fastapi import APIRouter, UploadFile, File, HTTPException, Query
from typing import Optional
import tempfile
import os

from app.models.schema import IngestRequest, ManualAddRequest
from app.services.vector import store_vectors, search_vectors, delete_vectors
from app.services.chunker import chunk_text
from app.services.parser import clean_text
from app.db.mysql import execute_query, execute_update

router = APIRouter(prefix="/api/knowledge", tags=["knowledge"])

@router.post("/ingest")
async def ingest(request: IngestRequest):
    """接收采集报告"""
    try:
        results = []
        for report in request.reports:
            content = clean_text(report.content)
            if not content:
                continue

            chunks = chunk_text(content)
            if not chunks:
                continue

            sql = """
                INSERT INTO knowledge_base (title, content, source, source_type, author, publish_time, execution_id)
                VALUES (%s, %s, %s, %s, %s, %s, %s)
            """
            kb_id = execute_update(
                sql,
                (report.title, content, request.source, "采集", report.author, report.publishTime, request.executionId)
            )

            vector_ids = store_vectors(
                kb_id=kb_id,
                title=report.title,
                chunks=chunks,
                source=request.source,
                source_type="采集",
                publish_time=report.publishTime
            )

            execute_update(
                "UPDATE knowledge_base SET vector_ids = %s WHERE id = %s",
                (vector_ids, kb_id)
            )

            results.append({"kb_id": kb_id, "title": report.title, "chunks": len(chunks)})

        return {"code": 200, "data": {"count": len(results), "items": results}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/upload")
async def upload(title: str, source: str = "文档上传", file: UploadFile = File(...)):
    """文档上传"""
    try:
        suffix = os.path.splitext(file.filename)[1].lower().strip('.')
        if suffix not in ['pdf', 'doc', 'docx', 'txt']:
            raise HTTPException(status_code=400, detail="Unsupported file type")

        with tempfile.NamedTemporaryFile(delete=False, suffix=f'.{suffix}') as tmp:
            content = await file.read()
            tmp.write(content)
            tmp_path = tmp.name

        try:
            from app.services.parser import parse_document
            text = parse_document(tmp_path, suffix)
            text = clean_text(text)

            chunks = chunk_text(text)
            if not chunks:
                raise HTTPException(status_code=400, detail="No content extracted")

            sql = """
                INSERT INTO knowledge_base (title, content, source, source_type)
                VALUES (%s, %s, %s, %s)
            """
            kb_id = execute_update(sql, (title, text, source, "文档上传"))

            vector_ids = store_vectors(
                kb_id=kb_id,
                title=title,
                chunks=chunks,
                source=source,
                source_type="文档上传"
            )

            execute_update(
                "UPDATE knowledge_base SET vector_ids = %s WHERE id = %s",
                (vector_ids, kb_id)
            )

            return {"code": 200, "data": {"kb_id": kb_id, "chunks": len(chunks)}}
        finally:
            os.unlink(tmp_path)
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/manual")
async def manual_add(request: ManualAddRequest):
    """人工录入"""
    try:
        content = clean_text(request.content)
        if not content:
            raise HTTPException(status_code=400, detail="Content is empty")

        chunks = chunk_text(content)
        if not chunks:
            raise HTTPException(status_code=400, detail="No chunks generated")

        sql = """
            INSERT INTO knowledge_base (title, content, source, source_type, author, publish_time)
            VALUES (%s, %s, %s, %s, %s, %s)
        """
        kb_id = execute_update(
            sql,
            (request.title, content, request.source, "人工录入", request.author, request.publishTime)
        )

        vector_ids = store_vectors(
            kb_id=kb_id,
            title=request.title,
            chunks=chunks,
            source=request.source,
            source_type="人工录入",
            publish_time=request.publishTime
        )

        execute_update(
            "UPDATE knowledge_base SET vector_ids = %s WHERE id = %s",
            (vector_ids, kb_id)
        )

        return {"code": 200, "data": {"kb_id": kb_id, "chunks": len(chunks)}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

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
        sql = "SELECT * FROM knowledge_base ORDER BY id DESC LIMIT %s OFFSET %s"
        items = execute_query(sql, (page_size, offset))

        count_sql = "SELECT COUNT(*) as total FROM knowledge_base"
        total = execute_query(count_sql)[0]['total']

        return {"code": 200, "data": {"items": items, "total": total, "page": page, "page_size": page_size}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/stats")
async def stats():
    """知识库统计"""
    try:
        total_sql = "SELECT COUNT(*) as total FROM knowledge_base"
        total = execute_query(total_sql)[0]['total']

        by_source_sql = """
            SELECT source, source_type, COUNT(*) as count
            FROM knowledge_base
            GROUP BY source, source_type
        """
        by_source = execute_query(by_source_sql)

        return {"code": 200, "data": {"total": total, "by_source": by_source}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.get("/{kb_id}")
async def get_knowledge(kb_id: int):
    """知识详情"""
    try:
        sql = "SELECT * FROM knowledge_base WHERE id = %s"
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
        sql = "SELECT vector_ids FROM knowledge_base WHERE id = %s"
        items = execute_query(sql, (kb_id,))
        if not items:
            raise HTTPException(status_code=404, detail="Knowledge not found")

        vector_ids = items[0].get('vector_ids')
        if vector_ids:
            delete_vectors(vector_ids)

        delete_sql = "DELETE FROM knowledge_base WHERE id = %s"
        execute_update(delete_sql, (kb_id,))

        return {"code": 200, "data": {"deleted": kb_id}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))

@router.post("/{kb_id}/revectorize")
async def revectorize(kb_id: int):
    """重新向量化"""
    try:
        sql = "SELECT * FROM knowledge_base WHERE id = %s"
        items = execute_query(sql, (kb_id,))
        if not items:
            raise HTTPException(status_code=404, detail="Knowledge not found")

        item = items[0]
        old_vector_ids = item.get('vector_ids')
        if old_vector_ids:
            delete_vectors(old_vector_ids)

        content = clean_text(item['content'])
        chunks = chunk_text(content)
        if not chunks:
            raise HTTPException(status_code=400, detail="No chunks generated")

        vector_ids = store_vectors(
            kb_id=kb_id,
            title=item['title'],
            chunks=chunks,
            source=item['source'],
            source_type=item['source_type'],
            publish_time=item.get('publish_time')
        )

        execute_update(
            "UPDATE knowledge_base SET vector_ids = %s WHERE id = %s",
            (vector_ids, kb_id)
        )

        return {"code": 200, "data": {"kb_id": kb_id, "chunks": len(chunks)}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
