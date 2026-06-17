"""MinIO 客户端单元测试"""
from collectorsdk.minio_client import MinioClient


def test_minio_client_init():
    """测试初始化不报错（不真正连接）"""
    client = MinioClient(
        endpoint="http://localhost:19000",
        access_key="test",
        secret_key="test",
    )
    assert client.bucket == "knowledge-img"
    assert client.endpoint == "http://localhost:19000"
