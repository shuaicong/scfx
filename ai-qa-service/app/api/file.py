from fastapi import APIRouter, HTTPException
from fastapi.responses import StreamingResponse, FileResponse
from pydantic import BaseModel
from typing import Optional
import os
import uuid
import aiofiles

from app.db.mysql import execute_query, execute_update

router = APIRouter(prefix="/api/files", tags=["files"])

# 文件存储目录
STORAGE_DIR = os.path.join(os.path.dirname(os.path.dirname(os.path.dirname(__file__))), "storage", "files")

# 确保存储目录存在
os.makedirs(STORAGE_DIR, exist_ok=True)


class FileUploadResponse(BaseModel):
    file_id: str
    filename: str
    file_type: str
    size: int


@router.post("/upload")
async def upload_file(file_id: str, filename: str, file_type: str):
    """创建文件上传会话，返回上传ID"""
    try:
        # 保存文件信息到数据库
        sql = """
            INSERT INTO t_knowledge_files (file_id, filename, file_type, status)
            VALUES (%s, %s, %s, 'pending')
        """
        execute_update(sql, (file_id, filename, file_type))
        return {"code": 200, "data": {"file_id": file_id}}
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{file_id}/info")
async def get_file_info(file_id: str):
    """获取文件信息"""
    try:
        sql = "SELECT * FROM t_knowledge_files WHERE file_id = %s"
        items = execute_query(sql, (file_id,))
        if not items:
            raise HTTPException(status_code=404, detail="File not found")

        file_info = items[0]
        return {
            "code": 200,
            "data": {
                "file_id": file_info['file_id'],
                "filename": file_info['filename'],
                "file_type": file_info['file_type'],
                "status": file_info['status'],
                "size": file_info.get('size', 0)
            }
        }
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{file_id}/stream")
async def stream_file(file_id: str):
    """流式获取文件内容"""
    try:
        sql = "SELECT * FROM t_knowledge_files WHERE file_id = %s"
        items = execute_query(sql, (file_id,))
        if not items:
            raise HTTPException(status_code=404, detail="File not found")

        file_info = items[0]
        filepath = os.path.join(STORAGE_DIR, file_id)

        if not os.path.exists(filepath):
            raise HTTPException(status_code=404, detail="File not found on disk")

        # 根据文件类型设置 Content-Type
        content_type_map = {
            'pdf': 'application/pdf',
            'doc': 'application/msword',
            'docx': 'application/vnd.openxmlformats-officedocument.wordprocessingml.document',
            'xls': 'application/vnd.ms-excel',
            'xlsx': 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet',
            'jpg': 'image/jpeg',
            'jpeg': 'image/jpeg',
            'png': 'image/png',
            'gif': 'image/gif',
            'txt': 'text/plain',
            'md': 'text/markdown',
        }

        file_type = file_info['file_type']
        content_type = content_type_map.get(file_type, 'application/octet-stream')

        async def iterfile():
            async with aiofiles.open(filepath, 'rb') as f:
                while True:
                    chunk = await f.read(1024 * 1024)  # 1MB chunks
                    if not chunk:
                        break
                    yield chunk

        return StreamingResponse(
            iterfile(),
            media_type=content_type,
            headers={
                "Content-Disposition": f"inline; filename={file_info['filename']}",
                "Content-Length": str(os.path.getsize(filepath))
            }
        )
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{file_id}/text")
async def get_file_text(file_id: str):
    """获取文本文件内容（用于在线预览）"""
    try:
        sql = "SELECT * FROM t_knowledge_files WHERE file_id = %s"
        items = execute_query(sql, (file_id,))
        if not items:
            raise HTTPException(status_code=404, detail="File not found")

        file_info = items[0]
        file_type = file_info['file_type']

        # 只支持文本类文件
        text_types = ['txt', 'md', 'json', 'html']
        if file_type not in text_types:
            raise HTTPException(status_code=400, detail=f"File type {file_type} not supported for text preview")

        filepath = os.path.join(STORAGE_DIR, file_id)
        if not os.path.exists(filepath):
            raise HTTPException(status_code=404, detail="File not found on disk")

        async with aiofiles.open(filepath, 'r', encoding='utf-8') as f:
            content = await f.read()

        return {
            "code": 200,
            "data": {
                "content": content,
                "filename": file_info['filename'],
                "file_type": file_type
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{file_id}/pdfjs")
async def get_pdf_js_config(file_id: str):
    """获取 PDF.js 预览配置"""
    try:
        sql = "SELECT * FROM t_knowledge_files WHERE file_id = %s"
        items = execute_query(sql, (file_id,))
        if not items:
            raise HTTPException(status_code=404, detail="File not found")

        file_info = items[0]
        if file_info['file_type'] != 'pdf':
            raise HTTPException(status_code=400, detail="Not a PDF file")

        return {
            "code": 200,
            "data": {
                "pdf_url": f"/api/files/{file_id}/stream",
                "filename": file_info['filename']
            }
        }
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{file_id}/word")
async def get_word_html(file_id: str):
    """获取 Word 文件的 HTML 转换结果"""
    try:
        sql = "SELECT * FROM t_knowledge_files WHERE file_id = %s"
        items = execute_query(sql, (file_id,))
        if not items:
            raise HTTPException(status_code=404, detail="File not found")

        file_info = items[0]
        file_type = file_info['file_type']

        if file_type not in ['doc', 'docx']:
            raise HTTPException(status_code=400, detail="Not a Word file")

        filepath = os.path.join(STORAGE_DIR, file_id)
        if not os.path.exists(filepath):
            raise HTTPException(status_code=404, detail="File not found on disk")

        # 使用 mammoth 转换 Word 为 HTML
        try:
            import mammoth
            with open(filepath, 'rb') as docx_file:
                result = mammoth.convert_to_html({'stream': docx_file})
                html = result.value
                messages = result.messages

            return {
                "code": 200,
                "data": {
                    "html": html,
                    "filename": file_info['filename'],
                    "messages": [str(m) for m in messages]
                }
            }
        except ImportError:
            raise HTTPException(status_code=500, detail="mammoth library not installed")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))


@router.get("/{file_id}/excel")
async def get_excel_html(file_id: str):
    """获取 Excel 文件的 HTML 表格"""
    try:
        sql = "SELECT * FROM t_knowledge_files WHERE file_id = %s"
        items = execute_query(sql, (file_id,))
        if not items:
            raise HTTPException(status_code=404, detail="File not found")

        file_info = items[0]
        file_type = file_info['file_type']

        if file_type not in ['xls', 'xlsx']:
            raise HTTPException(status_code=400, detail="Not an Excel file")

        filepath = os.path.join(STORAGE_DIR, file_id)
        if not os.path.exists(filepath):
            raise HTTPException(status_code=404, detail="File not found on disk")

        try:
            import pandas as pd
            import io

            df = pd.read_excel(filepath)
            html = df.to_html(index=False, classes='excel-table')

            return {
                "code": 200,
                "data": {
                    "html": html,
                    "filename": file_info['filename'],
                    "rows": len(df),
                    "columns": list(df.columns)
                }
            }
        except ImportError:
            raise HTTPException(status_code=500, detail="pandas library not installed")
    except HTTPException:
        raise
    except Exception as e:
        raise HTTPException(status_code=500, detail=str(e))
