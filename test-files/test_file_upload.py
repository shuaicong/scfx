#!/usr/bin/env python3
"""
测试文件上传和预览功能的脚本
需要先在数据库中创建 t_knowledge_files 表
"""

import requests
import uuid
import os
import json

BASE_URL = "http://localhost:5002/api"
STORAGE_DIR = "/Users/hucong/workspace/ai/scfx/ai-qa-service/storage/files"

def create_test_file_record(filename, file_type):
    """通过 file.py 的 upload 接口创建文件记录"""
    file_id = str(uuid.uuid4())

    try:
        response = requests.post(
            f"{BASE_URL}/files/upload",
            json={
                "file_id": file_id,
                "filename": filename,
                "file_type": file_type
            }
        )
        if response.status_code == 200:
            print(f"Created record: {filename} (ID: {file_id})")
            return file_id
        else:
            print(f"Failed to create record: {response.text}")
            return None
    except Exception as e:
        print(f"Error: {e}")
        return None

def upload_test_file(file_id, filename, content):
    """创建实际文件"""
    filepath = os.path.join(STORAGE_DIR, file_id)

    # 确保目录存在
    os.makedirs(os.path.dirname(filepath), exist_ok=True)

    with open(filepath, 'wb' if isinstance(content, bytes) else 'w', encoding='utf-8' if isinstance(content, str) else None) as f:
        f.write(content)

    print(f"Created file: {filepath}")

def main():
    print("=" * 50)
    print("测试文件上传和预览")
    print("=" * 50)

    # 创建测试文件记录并上传
    test_files = [
        ("粮食价格周报.txt", "txt", "本周玉米价格稳中略涨..."),
        ("市场分析报告.md", "md", "# 粮食市场分析报告\n\n本周粮食市场..."),
        ("测试报告.pdf", "pdf", b"%PDF-1.4\n1 0 obj\n<<>>\nendobj"),
    ]

    created_files = []

    for filename, file_type, content in test_files:
        file_id = create_test_file_record(filename, file_type)
        if file_id:
            upload_test_file(file_id, filename, content)
            created_files.append({
                "file_id": file_id,
                "filename": filename,
                "file_type": file_type,
                "stream_url": f"/api/files/{file_id}/stream",
                "info_url": f"/api/files/{file_id}/info"
            })

    # 输出测试信息
    print("\n" + "=" * 50)
    print("创建的文件:")
    print("=" * 50)

    for f in created_files:
        print(f"\n文件名: {f['filename']}")
        print(f"文件ID: {f['file_id']}")
        print(f"流地址: {f['stream_url']}")
        print(f"信息地址: {f['info_url']}")

        # 测试各类型预览
        if f['file_type'] == 'txt' or f['file_type'] == 'md':
            print(f"文本内容: {BASE_URL}/files/{f['file_id']}/text")
        elif f['file_type'] == 'pdf':
            print(f"PDF预览: {BASE_URL}/files/{f['file_id']}/pdfjs")

    print("\n" + "=" * 50)
    print("测试方法:")
    print("=" * 50)
    print("1. 在前端知识库页面查看这些文件")
    print("2. 点击预览按钮测试 DocumentPreview 组件")
    print("3. 检查浏览器控制台是否有错误")

if __name__ == "__main__":
    main()