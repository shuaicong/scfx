"""
采集状态上报器 - 核心模块

完全解耦、异步非阻塞、失败隔离
"""

import atexit
import platform
import queue
import threading
import time
import uuid
from concurrent.futures import ThreadPoolExecutor
from datetime import datetime
from typing import Any, Optional

import requests

from .config import ReporterConfig
from .dimensions import Dimensions

__version__ = "1.0.0"


class CollectorReporter:
    """
    异步非阻塞上报器

    特性：
    - 线程池异步执行，不阻塞采集
    - 上报失败不影响主流程
    - 支持配置开关
    - 自动重试机制
    - 请求缓存队列
    - SDK 自动注册和心跳
    """

    def __init__(self, config: ReporterConfig):
        self.config = config
        self._execution_id: Optional[str] = None
        self._dimensions: Optional[Dimensions] = None
        self._started = False
        self._collector_id: Optional[str] = None

        # 异步执行器
        self._executor = ThreadPoolExecutor(max_workers=2, thread_name_prefix="collector_reporter")
        self._queue: queue.Queue = queue.Queue(maxsize=config.cache_size)

        # 启动后台处理线程
        if config.async_mode:
            self._worker_thread = threading.Thread(target=self._worker, daemon=True)
            self._worker_thread.start()

        # 心跳线程
        self._heartbeat_thread: Optional[threading.Thread] = None
        self._stop_heartbeat = threading.Event()

        # SDK 自动注册
        if config.enabled and config.api_base:
            self._auto_register()

        # 注册退出时清理
        atexit.register(self._cleanup)

    def set_dimensions(self, dimensions: Dimensions):
        """设置核心维度"""
        self._dimensions = dimensions

    def set_execution_id(self, execution_id: str):
        """设置执行ID"""
        self._execution_id = execution_id

    def _auto_register(self):
        """自动注册到 Java 后端"""
        try:
            collector_name = f"python-collector-{platform.node()}"
            resp = requests.post(
                f"{self.config.api_base}/collector/register",
                json={
                    "collectorName": collector_name,
                    "sdkVersion": __version__,
                    "source": self._dimensions.source if self._dimensions else "",
                    "subject": self._dimensions.subject if self._dimensions else "",
                    "collType": self._dimensions.coll_type if self._dimensions else "",
                    "collObject": self._dimensions.object if self._dimensions else "",
                    "description": f"Python {platform.python_version()} SDK",
                },
                timeout=self.config.timeout,
            )
            if resp.status_code == 200:
                data = resp.json()
                if data.get("code") == 200:
                    self._collector_id = data["data"].get("id")
                    print(f"[Reporter] SDK 注册成功: {collector_name}")
                    self._start_heartbeat()
        except Exception as e:
            print(f"[Reporter] SDK 注册失败: {e}")

    def _start_heartbeat(self):
        """启动心跳线程"""
        self._stop_heartbeat.clear()
        self._heartbeat_thread = threading.Thread(target=self._heartbeat_loop, daemon=True)
        self._heartbeat_thread.start()

    def _heartbeat_loop(self):
        """心跳循环"""
        while not self._stop_heartbeat.is_set():
            try:
                requests.post(
                    f"{self.config.api_base}/collector/heartbeat",
                    json={
                        "collectorName": f"python-collector-{platform.node()}",
                        "source": self._dimensions.source if self._dimensions else "",
                    },
                    timeout=self.config.timeout,
                )
            except Exception:
                pass
            self._stop_heartbeat.wait(60)  # 每分钟发送一次

    def _cleanup(self):
        """清理资源"""
        self._stop_heartbeat.set()
        if self._heartbeat_thread:
            self._heartbeat_thread.join(timeout=2)
        self._shutdown()

        # 通知下线
        if self.config.enabled and self.config.api_base and self._dimensions:
            try:
                requests.post(
                    f"{self.config.api_base}/collector/offline",
                    json={
                        "collectorName": f"python-collector-{platform.node()}",
                        "source": self._dimensions.source,
                    },
                    timeout=self.config.timeout,
                )
            except Exception:
                pass

    def report_start(self, task_id: int) -> dict:
        """
        上报任务开始

        Args:
            task_id: 任务ID

        Returns:
            包含execution_id的字典
        """
        if not self.config.enabled or not self.config.api_base:
            # 上报关闭或未配置API地址，生成伪execution_id供本地使用
            execution_id = str(uuid.uuid4())
            self._execution_id = execution_id
            self._started = True
            return {"executionId": execution_id, "taskId": task_id}

        try:
            resp = requests.post(
                f"{self.config.api_base}/collector/exec/start",
                json={"taskId": task_id},
                timeout=self.config.timeout,
            )
            resp.raise_for_status()
            data = resp.json()
            if data.get("code") == 200:
                self._execution_id = data["data"]["executionId"]
                self._started = True
                return data["data"]
            else:
                raise Exception(f"API返回错误: {data.get('message')}")
        except Exception as e:
            print(f"[Reporter] 启动任务失败: {e}，使用本地模式")
            execution_id = str(uuid.uuid4())
            self._execution_id = execution_id
            self._started = True
            return {"executionId": execution_id, "taskId": task_id}

    def report_log(self, level: str, message: str, phase: str = "",
                    category: str = "", elapsed_ms: int = 0):
        """
        上报日志（异步）

        Args:
            level: 日志级别 DEBUG/INFO/WARN/ERROR
            message: 日志消息
            phase: 所属阶段 login/crawl/parse/report/system
            category: 日志分类 progress/data/error/metric/checkpoint
            elapsed_ms: 相对开始时间的毫秒偏移
        """
        if not self._started or not self._execution_id:
            return

        log_entry = {
            "type": "log",
            "executionId": self._execution_id,
            "level": level,
            "message": message,
            "phase": phase,
            "category": category,
            "elapsedMs": elapsed_ms,
            "timestamp": datetime.now().isoformat(),
        }

        if self.config.async_mode:
            self._queue.put(log_entry)
        else:
            self._send_log(log_entry)

    def report_progress(self, collected_count: int):
        """
        上报进度（异步）

        Args:
            collected_count: 已采集数量
        """
        if not self._started or not self._execution_id:
            return

        entry = {
            "type": "progress",
            "executionId": self._execution_id,
            "collectedCount": collected_count,
            "timestamp": datetime.now().isoformat(),
        }

        if self.config.async_mode:
            self._queue.put(entry)
        else:
            self._send_progress(entry)

    def report_data(self, data: dict):
        """
        上报采集数据（异步）

        Args:
            data: 采集的数据
        """
        if not self._started or not self._execution_id:
            return

        entry = {
            "type": "data",
            "executionId": self._execution_id,
            "data": data,
            "timestamp": datetime.now().isoformat(),
        }

        if self.config.async_mode:
            self._queue.put(entry)
        else:
            self._send_data(entry)

    def report_error(self, error_message: str):
        """
        上报错误（异步）

        Args:
            error_message: 错误消息
        """
        if not self._started or not self._execution_id:
            return

        entry = {
            "type": "error",
            "executionId": self._execution_id,
            "errorMessage": error_message,
            "timestamp": datetime.now().isoformat(),
        }

        if self.config.async_mode:
            self._queue.put(entry)
        else:
            self._send_error(entry)

    def report_complete(self, status: str, collected_count: int,
                        total_count: int = 0, success_count: int = 0,
                        skip_count: int = 0, error_count: int = 0,
                        data_size_mb: float = 0.0,
                        phase_login_ms: int = 0, phase_crawl_ms: int = 0,
                        phase_parse_ms: int = 0, phase_report_ms: int = 0):
        """
        上报完成（异步）

        Args:
            status: success/failed
            collected_count: 总采集数量
            total_count: 总处理数
            success_count: 成功数
            skip_count: 去重跳过数
            error_count: 失败数
            data_size_mb: 数据量(MB)
            phase_login_ms: 登录阶段耗时(ms)
            phase_crawl_ms: 抓取阶段耗时(ms)
            phase_parse_ms: 解析阶段耗时(ms)
            phase_report_ms: 上报阶段耗时(ms)
        """
        if not self._started or not self._execution_id:
            return

        entry = {
            "type": "complete",
            "executionId": self._execution_id,
            "status": status,
            "collectedCount": collected_count,
            "totalCount": total_count,
            "successCount": success_count,
            "skipCount": skip_count,
            "errorCount": error_count,
            "dataSizeMb": data_size_mb,
            "phase": {
                "loginMs": phase_login_ms,
                "crawlMs": phase_crawl_ms,
                "parseMs": phase_parse_ms,
                "reportMs": phase_report_ms,
            },
            "timestamp": datetime.now().isoformat(),
        }

        if self.config.async_mode:
            self._queue.put(entry)
            # 异步模式下确保完成消息被发送
            time.sleep(0.5)
        else:
            self._send_complete(entry)

    def _worker(self):
        """后台工作线程，处理队列中的上报请求"""
        while True:
            try:
                entry = self._queue.get(timeout=1.0)
                entry_type = entry.pop("type")

                for _ in range(self.config.retry_times):
                    try:
                        if entry_type == "log":
                            self._send_log(entry)
                        elif entry_type == "progress":
                            self._send_progress(entry)
                        elif entry_type == "data":
                            self._send_data(entry)
                        elif entry_type == "error":
                            self._send_error(entry)
                        elif entry_type == "complete":
                            self._send_complete(entry)
                        break  # 成功则退出重试
                    except Exception as e:
                        if _ < self.config.retry_times - 1:
                            time.sleep(self.config.retry_delay)
                        else:
                            print(f"[Reporter] 上报失败，已重试{self.config.retry_times}次: {e}")
            except queue.Empty:
                continue
            except Exception as e:
                print(f"[Reporter] Worker异常: {e}")

    def _send_log(self, entry: dict):
        """发送日志到后端"""
        payload = {"level": entry["level"], "message": entry["message"]}
        if entry.get("phase"):
            payload["phase"] = entry["phase"]
        if entry.get("category"):
            payload["category"] = entry["category"]
        if entry.get("elapsedMs"):
            payload["elapsedMs"] = entry["elapsedMs"]
        resp = requests.post(
            f"{self.config.api_base}/collector/exec/{entry['executionId']}/log",
            json=payload,
            timeout=self.config.timeout,
        )
        resp.raise_for_status()

    def _send_progress(self, entry: dict):
        """发送进度到后端"""
        resp = requests.post(
            f"{self.config.api_base}/collector/exec/{entry['executionId']}/progress",
            json={"collectedCount": entry["collectedCount"]},
            timeout=self.config.timeout,
        )
        resp.raise_for_status()

    def _send_data(self, entry: dict):
        """发送数据到后端"""
        resp = requests.post(
            f"{self.config.api_base}/collector/exec/{entry['executionId']}/data",
            json=entry["data"],
            timeout=self.config.timeout,
        )
        resp.raise_for_status()

    def _send_error(self, entry: dict):
        """发送错误到后端"""
        resp = requests.post(
            f"{self.config.api_base}/collector/exec/{entry['executionId']}/error",
            json={"errorMessage": entry["errorMessage"]},
            timeout=self.config.timeout,
        )
        resp.raise_for_status()

    def _send_complete(self, entry: dict):
        """发送完成到后端"""
        resp = requests.post(
            f"{self.config.api_base}/collector/exec/{entry['executionId']}/complete",
            json={
                "status": entry["status"],
                "collectedCount": entry["collectedCount"],
                "totalCount": entry.get("totalCount", 0),
                "successCount": entry.get("successCount", 0),
                "skipCount": entry.get("skipCount", 0),
                "errorCount": entry.get("errorCount", 0),
                "dataSizeMb": entry.get("dataSizeMb", 0.0),
                "phase": entry.get("phase", {}),
            },
            timeout=self.config.timeout,
        )
        resp.raise_for_status()

    def _shutdown(self):
        """关闭上报器，等待队列处理完成"""
        try:
            self._executor.shutdown(wait=True)
        except Exception:
            pass

    # ==================== 便捷方法 ====================

    def log_debug(self, message: str, phase: str = "", category: str = ""):
        """快捷方法：记录DEBUG日志"""
        self.report_log("DEBUG", message, phase=phase, category=category)

    def log_info(self, message: str, phase: str = "", category: str = ""):
        """快捷方法：记录INFO日志"""
        self.report_log("INFO", message, phase=phase, category=category)

    def log_warn(self, message: str, phase: str = "", category: str = ""):
        """快捷方法：记录WARN日志"""
        self.report_log("WARN", message, phase=phase, category=category)

    def log_error(self, message: str, phase: str = "", category: str = ""):
        """快捷方法：记录ERROR日志"""
        self.report_log("ERROR", message, phase=phase, category=category)

    def submit_report(self, title: str, source: str, url: str, variety: str = "", report_type: str = "", content: str = "", content_html: str = "", publish_time: str = "", table_meta: str = "", image_count: int = 0, image_list: Optional[str] = None):
        """
        快捷方法：提交报告数据

        Args:
            title: 报告标题
            source: 数据源
            url: 原文链接
            variety: 品种
            report_type: 报告类型
            content: 正文内容
            content_html: 正文HTML（保留格式）
            publish_time: 发布时间
            table_meta: 结构化表格数据 JSON 字符串（由采集器在提取 HTML 时生成）
            image_count: 图片数量
            image_list: 图片明细 JSON 字符串
        """
        data = {
            "title": title,
            "source": source,
            "url": url,
            "variety": variety,
            "reportType": report_type,
            "content": content,
            "contentHtml": content_html,
            "publishTime": publish_time,
            "tableMeta": table_meta,
            "imageCount": image_count,
            "imageList": image_list,
        }
        self.report_data(data)
