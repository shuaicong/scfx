"""
知识摄入脚本：将 t_knowledge_base 中的活跃知识切片 → 向量化 → 写入 Qdrant

用法:
  cd ai-qa-service
  export $(cat ai-qa-service.env | xargs)
  /path/to/python scripts/ingest_knowledge.py
"""
import os
import sys
import uuid

# 添加项目根到 path，使 import 服务模块生效
sys.path.insert(0, os.path.join(os.path.dirname(__file__), ".."))

import pymysql
from app.services.chunker import chunk_with_types
from app.services.embed import embed_text
from app.db.qdrant import get_client, ensure_collection, COLLECTION_NAME

DB_CONFIG = {
    "host": os.getenv("MYSQL_HOST", "localhost"),
    "port": int(os.getenv("MYSQL_PORT", "3306")),
    "user": os.getenv("MYSQL_USER", "root"),
    "password": os.getenv("MYSQL_PASSWORD", "password"),
    "database": os.getenv("MYSQL_DATABASE", "scfx"),
    "charset": "utf8mb4",
}


def get_connection():
    return pymysql.connect(**DB_CONFIG)


def ingest():
    # 确保 Qdrant collection 存在
    ensure_collection()
    client = get_client()

    conn = get_connection()
    cursor = conn.cursor(pymysql.cursors.DictCursor)

    # 1. 查询活跃知识
    cursor.execute(
        "SELECT id, title, content, source_type, publish_time "
        "FROM t_knowledge_base WHERE deleted = 0 "
        "ORDER BY id"
    )
    rows = cursor.fetchall()
    print(f"查询到 {len(rows)} 条活跃知识")

    total_chunks = 0
    total_errors = 0

    for row in rows:
        kid = row["id"]
        title = row["title"]
        content = row["content"]
        source = row["source_type"] or "unknown"
        publish_time = row["publish_time"]
        if publish_time:
            publish_time = publish_time.isoformat()

        if not content or not content.strip():
            print(f"  ⏭️  ID={kid} 内容为空，跳过")
            continue

        # 2. 切片（含语义化改写）
        chunks = chunk_with_types(content)
        if not chunks:
            print(f"  ⏭️  ID={kid} 切片为空，跳过")
            continue

        print(f"  📄 ID={kid} 「{title[:40]}」 → {len(chunks)} 个切片")

        # 3. 逐片向量化 + 写入 Qdrant
        points = []
        vector_ids = []
        for i, item in enumerate(chunks):
            try:
                vector = embed_text(item["content"])
            except Exception as e:
                print(f"    ❌ 向量化失败 chunk[{i}]: {e}")
                total_errors += 1
                continue

            point_id = str(uuid.uuid4())
            vector_ids.append(point_id)
            points.append({
                "id": point_id,
                "vector": vector,
                "payload": {
                    "knowledge_id": kid,
                    "title": title,
                    "content": item["content"],          # ← embedding 原文 = LLM context
                    "source": source,
                    "publish_time": publish_time,
                    "chunk_index": i,
                    "chunk_type": item["type"],          # "table" | "text"
                },
            })

        if points:
            client.upsert(collection_name=COLLECTION_NAME, points=points)
            # 4. 回写 vector_ids
            ids_str = ",".join(vector_ids)
            cursor.execute(
                "UPDATE t_knowledge_base SET vector_ids = %s WHERE id = %s",
                (ids_str, kid),
            )

            # 5. 写入 t_knowledge_chunk 表 + 更新 chunk_count（保持前端展示一致）
            chunk_total = len(points)
            # 先清空旧切片再写入，避免重复
            cursor.execute(
                "DELETE FROM t_knowledge_chunk WHERE knowledge_id = %s",
                (kid,),
            )
            for idx, item in enumerate(chunks):
                cursor.execute(
                    "INSERT INTO t_knowledge_chunk "
                    "(knowledge_id, chunk_index, chunk_total, content, chunk_type, vector_id, vector_status) "
                    "VALUES (%s, %s, %s, %s, %s, %s, 'vectorized')",
                    (kid, idx, chunk_total, item["content"], item["type"], vector_ids[idx] if idx < len(vector_ids) else None),
                )
            cursor.execute(
                "UPDATE t_knowledge_base SET chunk_count = %s WHERE id = %s",
                (chunk_total, kid),
            )

            conn.commit()
            total_chunks += len(points)
            print(f"    ✅ {len(points)} 个切片已写入 Qdrant, vector_ids={ids_str[:50]}...")

    cursor.close()
    conn.close()

    print(f"\n===== 摄入完成 =====")
    print(f"总切片数: {total_chunks}")
    print(f"失败数:   {total_errors}")

    # 验证 Qdrant 数据
    info = client.get_collection(COLLECTION_NAME)
    print(f"Qdrant collection 总点数: {info.points_count}")


if __name__ == "__main__":
    ingest()
