"""MinIO 对象存储客户端封装"""
import json
import logging
from typing import Optional
from botocore.exceptions import ClientError
from mypy_boto3_s3 import S3Client
import boto3
from botocore.config import Config

logger = logging.getLogger(__name__)

# 最小权限公开读策略
PUBLIC_READ_POLICY = json.dumps({
    "Version": "2012-10-17",
    "Statement": [{
        "Effect": "Allow",
        "Principal": "*",
        "Action": "s3:GetObject",
        "Resource": "arn:aws:s3:::{bucket}/*"
    }]
})

# CORS 配置
# 注意: MinIO RELEASE.2024-01 的 put_bucket_cors API 有兼容性问题
# 但 <img> 标签加载图片不触发 CORS，暂不配置
# 如果后续需要 JS fetch 加载图片，再手动设置或升级 MinIO
CORS_CONFIG = {
    "CORSRules": [{
        "AllowedOrigins": ["http://localhost:3000", "http://localhost:9528"],
        "AllowedMethods": ["GET"]
    }]
}


class MinioClient:
    """MinIO 操作客户端"""

    def __init__(
        self,
        endpoint: str = "http://localhost:9000",
        access_key: str = "admin",
        secret_key: str = "password",
        bucket: str = "knowledge-img",
        region: str = "us-east-1",
    ):
        self.endpoint = endpoint
        self.bucket = bucket
        self.region = region

        self._client: S3Client = boto3.client(
            "s3",
            endpoint_url=endpoint,
            aws_access_key_id=access_key,
            aws_secret_access_key=secret_key,
            region_name=region,
            config=Config(signature_version="s3v4"),
        )
        self._ensure_bucket()

    def _ensure_bucket(self):
        """桶不存在则自动创建并配置策略"""
        try:
            self._client.head_bucket(Bucket=self.bucket)
            logger.info("MinIO 桶已存在: %s", self.bucket)

            # CORS 配置（仅桶已存在时设置）
            try:
                self._client.put_bucket_cors(
                    Bucket=self.bucket, CORSConfiguration=CORS_CONFIG
                )
                logger.info("MinIO 桶 CORS 已配置: %s", self.bucket)
            except Exception as e:
                logger.warning("MinIO 桶 CORS 配置失败（可忽略）: %s", str(e))
        except ClientError:
            logger.info("MinIO 桶不存在，正在创建: %s", self.bucket)
            self._client.create_bucket(Bucket=self.bucket)

            # 设置公开读策略
            policy = PUBLIC_READ_POLICY.replace("{bucket}", self.bucket)
            self._client.put_bucket_policy(Bucket=self.bucket, Policy=policy)
            logger.info("MinIO 桶公开读策略已设置: %s", self.bucket)

            # CORS 配置（新桶，失败不阻塞）
            try:
                self._client.put_bucket_cors(
                    Bucket=self.bucket, CORSConfiguration=CORS_CONFIG
                )
                logger.info("MinIO 桶 CORS 已配置: %s", self.bucket)
            except Exception as e:
                logger.warning("MinIO 桶 CORS 配置失败（可忽略）: %s", str(e))
        except Exception as e:
            # MinIO 不可用时降级（连接失败等），不阻塞调用方
            logger.warning("MinIO 桶初始化失败（已降级）: %s", str(e))

    def upload_fileobj(
        self,
        data: bytes,
        object_path: str,
        metadata: Optional[dict] = None,
        content_type: str = "image/png",
    ) -> str:
        """上传二进制数据到 MinIO，返回可公开访问的 URL"""
        extra_args = {
            "ContentType": content_type,
        }
        if metadata:
            extra_args["Metadata"] = metadata

        self._client.put_object(
            Bucket=self.bucket,
            Key=object_path,
            Body=data,
            **extra_args,
        )

        # 返回公开可访问的 URL
        return f"{self.endpoint}/{self.bucket}/{object_path}"

    def delete_object(self, object_path: str):
        """删除单个对象"""
        self._client.delete_object(Bucket=self.bucket, Key=object_path)

    def delete_objects(self, object_paths: list[str]):
        """批量删除对象"""
        if not object_paths:
            return
        objects = [{"Key": p} for p in object_paths]
        self._client.delete_objects(
            Bucket=self.bucket,
            Delete={"Objects": objects},
        )
        logger.info("MinIO 批量删除 %d 个对象", len(object_paths))
