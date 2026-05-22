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
        self.execution_id: Optional[str] = None

    def start_execution(self, task_id: int = 1) -> str:
        """启动采集执行，获取executionId

        Args:
            task_id: 任务ID

        Returns:
            executionId
        """
        url = f"{self.base_url}/collector/exec/start"

        payload = {"taskId": task_id}

        try:
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()

            data = resp.json()
            if data.get("code") == 200:
                self.execution_id = data["data"].get("executionId")
                logger.info(f"启动采集执行: {self.execution_id}")
                return self.execution_id
            else:
                raise KnowledgeAPIError(f"启动执行失败: {data.get('message')}")

        except requests.RequestException as e:
            logger.error(f"启动执行失败: {e}")
            raise KnowledgeAPIError(f"网络请求失败: {e}")

    def submit_report(self, report: Dict) -> Dict:
        """提交单条报告到知识库

        Args:
            report: 报告数据，包含:
                - title: 标题
                - content: 正文
                - source: 来源
                - url: 原文链接
                - variety: 品种
                - report_type: 报告类型
                - publish_time: 发布时间

        Returns:
            提交结果
        """
        if not self.execution_id:
            self.start_execution()

        url = f"{self.base_url}/collector/exec/{self.execution_id}/data"

        payload = {
            "title": report.get("title"),
            "source": report.get("source", "liangxin"),
            "url": report.get("url"),
            "variety": report.get("variety", "玉米"),
            "reportType": report.get("report_type", "晨报"),
            "content": report.get("content"),
            "contentHtml": report.get("contentHtml"),
            "publishTime": report.get("publish_time"),
        }

        try:
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()

            data = resp.json()
            if data.get("code") == 200:
                knowledge_id = data["data"].get("knowledgeId")
                logger.info(f"报告已提交: {report.get('title')[:30]}... -> knowledgeId={knowledge_id}")
                return {
                    "success": True,
                    "knowledgeId": knowledge_id,
                }
            else:
                error_msg = data.get("message", "未知错误")
                logger.warning(f"报告提交失败: {error_msg}")
                return {
                    "success": False,
                    "error": error_msg,
                }

        except requests.RequestException as e:
            logger.error(f"提交报告失败: {e}")
            return {
                "success": False,
                "error": str(e),
            }

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
        if not self.execution_id:
            self.start_execution()

        ingested = 0
        failed = 0
        results = []

        for report in reports:
            result = self.submit_report(report)
            results.append(result)
            if result.get("success"):
                ingested += 1
            else:
                failed += 1

        logger.info(f"批量提交完成: 成功={ingested}, 失败={failed}")
        return {
            "success": failed == 0,
            "ingested": ingested,
            "failed": failed,
            "results": results,
        }

    def complete_execution(self, status: str = "success", collected_count: int = 0):
        """完成采集执行

        Args:
            status: 执行状态 success/failed
            collected_count: 采集数量
        """
        if not self.execution_id:
            logger.warning("没有executionId，跳过完成通知")
            return

        url = f"{self.base_url}/collector/exec/{self.execution_id}/complete"

        payload = {
            "status": status,
            "collectedCount": collected_count,
        }

        try:
            resp = requests.post(url, json=payload, timeout=self.timeout)
            resp.raise_for_status()
            logger.info(f"执行完成: status={status}, count={collected_count}")
        except requests.RequestException as e:
            logger.error(f"完成执行通知失败: {e}")