"""知识库API客户端"""
import logging
import requests
from typing import List, Dict, Optional
from datetime import datetime

logger = logging.getLogger(__name__)


class KnowledgeAPIError(Exception):
    """知识库API异常"""
    pass


class KnowledgeAPIClient:
    """知识库API客户端

    用于将采集数据推送到知识库系统
    """

    def __init__(self, base_url: str, timeout: int = 30):
        """初始化

        Args:
            base_url: 知识库API基础地址
            timeout: 请求超时时间（秒）
        """
        self.base_url = base_url.rstrip('/')
        self.timeout = timeout

    def ingest_reports(self, reports: List[Dict]) -> Dict:
        """批量提交报告到知识库

        Args:
            reports: 报告列表，每个报告包含:
                - title: 标题
                - content: 正文
                - source: 来源
                - url: 原文链接
                - variety: 品种
                - report_type: 报告类型
                - publish_time: 发布时间

        Returns:
            提交结果 {
                "success": True/False,
                "ingested": 数量,
                "failed": 数量,
                "results": [...]
            }
        """
        url = f"{self.base_url}/api/knowledge/ingest"

        payload = {
            "executionId": f"liangxin-{datetime.now().strftime('%Y%m%d%H%M%S')}",
            "source": "liangxin",
            "reports": reports
        }

        try:
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()

            data = resp.json()
            if data.get("code") == 200:
                logger.info(f"成功提交 {len(reports)} 条报告到知识库")
                return {
                    "success": True,
                    "ingested": data["data"].get("ingested", 0),
                    "failed": data["data"].get("failed", 0),
                    "results": data["data"].get("results", [])
                }
            else:
                raise KnowledgeAPIError(f"API返回错误: {data.get('message')}")

        except requests.RequestException as e:
            logger.error(f"提交报告到知识库失败: {e}")
            raise KnowledgeAPIError(f"网络请求失败: {e}")
        except Exception as e:
            logger.error(f"提交报告异常: {e}")
            raise