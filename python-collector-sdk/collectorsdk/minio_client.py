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

    def stat_object(self, object_path: str, bucket: str = None) -> bool:
        """检查 MinIO 中对象是否存在

        Args:
            object_path: 对象路径, 如 "wasde/2026/wasde0626v2.pdf"
                         （不含桶名，桶名由 bucket 参数或实例默认 bucket 决定）
            bucket: 桶名，为 None 时使用实例初始化时的默认 bucket (knowledge-img)

        Returns:
            True 如果对象存在, False 如果不存在或无法访问
        """
        target_bucket = bucket or self.bucket
        try:
            self._client.head_object(Bucket=target_bucket, Key=object_path)
            return True
        except ClientError as e:
            error_code = e.response['Error']['Code']
            if error_code == '404' or error_code == 'NoSuchKey':
                logger.debug(f"MinIO 对象不存在: {target_bucket}/{object_path}")
                return False
            logger.error(f"MinIO head_object 错误: {target_bucket}/{object_path} - {e}")
            return False
        except Exception as e:
            logger.error(f"MinIO 访问异常: {target_bucket}/{object_path} - {e}")
            return False

    def put_object(self, object_path: str, data: bytes,
                   content_type: str = "application/octet-stream",
                   bucket: str = None) -> str:
        """上传文件到 MinIO（支持指定桶）

        与 upload_fileobj 的区别:
        - upload_fileobj: 使用实例默认 bucket (knowledge-img)，主用于图片
        - put_object: 可指定 bucket，用于 reports 等非图片桶

        Args:
            object_path: 对象路径, 如 "wasde/2026/wasde0626v2.pdf"
            data: 文件二进制数据
            content_type: MIME 类型
            bucket: 桶名，为 None 时使用实例默认 bucket

        Returns:
            文件的公开访问 URL
        """
        target_bucket = bucket or self.bucket
        try:
            # 仅首次使用该桶时检查存在性，后续复用缓存
            if not hasattr(self, '_buckets_checked'):
                self._buckets_checked = set()
            if target_bucket not in self._buckets_checked:
                try:
                    self._client.head_bucket(Bucket=target_bucket)
                except ClientError:
                    logger.info(f"MinIO 桶不存在，正在创建: {target_bucket}")
                    self._client.create_bucket(Bucket=target_bucket)
                    policy = PUBLIC_READ_POLICY.replace("{bucket}", target_bucket)
                    self._client.put_bucket_policy(Bucket=target_bucket, Policy=policy)
                self._buckets_checked.add(target_bucket)

            self._client.put_object(
                Bucket=target_bucket,
                Key=object_path,
                Body=data,
                ContentType=content_type,
            )

            url = f"{self.endpoint}/{target_bucket}/{object_path}"
            logger.info(f"MinIO 上传成功: {url}")
            return url
        except Exception as e:
            logger.error(f"MinIO 上传失败: {target_bucket}/{object_path} - {e}")
            raise
