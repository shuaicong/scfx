"""采集器子包 - 包含采集器注册表和文件监听"""
import os
import sys
import importlib
import logging
from pathlib import Path

logger = logging.getLogger(__name__)

# 尝试导入 watchdog（可选依赖）
try:
    from watchdog.observers import Observer
    from watchdog.events import FileSystemEventHandler
    WATCHDOG_AVAILABLE = True
except ImportError:
    WATCHDOG_AVAILABLE = False
    logger.warning("watchdog not available, file watching disabled")

from ..base import BaseCollector

# 采集器注册表
COLLECTORS = {}


def discover_collectors():
    """自动发现所有采集器"""
    collectors = {}

    collectors_dir = Path(__file__).parent
    for filename in os.listdir(collectors_dir):
        if filename.endswith('.py') and filename not in ('__init__.py', 'base.py', 'knowledge_api.py'):
            module_name = filename[:-3]
            try:
                module = importlib.import_module(f'collectorsdk.collectors.{module_name}')

                for attr_name in dir(module):
                    cls = getattr(module, attr_name)
                    if isinstance(cls, type) and issubclass(cls, BaseCollector) and cls is not BaseCollector:
                        if hasattr(cls, 'META') and 'code' in cls.META:
                            code = cls.META['code']
                            collectors[code] = cls
                            logger.info(f"Discovered collector: {code}")

            except Exception as e:
                logger.error(f"Failed to load collector {module_name}: {e}")

    return collectors


def reload_collector(module_name: str):
    """重新加载单个采集器模块"""
    global COLLECTORS

    full_name = f'collectorsdk.collectors.{module_name}'
    if full_name in sys.modules:
        importlib.reload(sys.modules[full_name])

    # 重新发现采集器
    COLLECTORS = discover_collectors()
    logger.info(f"Collector reloaded: {module_name}, total: {len(COLLECTORS)}")


class CollectorFileHandler(FileSystemEventHandler if WATCHDOG_AVAILABLE else object):
    """监听 collectors 目录变化"""

    def __init__(self, callback):
        self.callback = callback

    def on_created(self, event):
        if event.src_path.endswith('.py') and not event.src_path.endswith('__init__.py'):
            module_name = os.path.basename(event.src_path)[:-3]
            logger.info(f"Collector file created: {module_name}")
            self.callback(module_name, "created")

    def on_modified(self, event):
        if event.src_path.endswith('.py') and not event.src_path.endswith('__init__.py'):
            module_name = os.path.basename(event.src_path)[:-3]
            logger.info(f"Collector file modified: {module_name}")
            self.callback(module_name, "modified")


def start_file_watcher():
    """启动文件监听（仅在 watchdog 可用时）"""
    if not WATCHDOG_AVAILABLE:
        logger.info("Watchdog not available, skipping file watcher")
        return

    collectors_dir = Path(__file__).parent

    def on_change(module_name: str, event_type: str):
        logger.info(f"File change detected: {module_name} ({event_type})")
        reload_collector(module_name)

    handler = CollectorFileHandler(on_change)
    observer = Observer()
    observer.schedule(handler, str(collectors_dir), recursive=False)
    observer.start()
    logger.info("Collector file watcher started")


# 初始化时发现所有采集器
COLLECTORS = discover_collectors()

__all__ = ["BaseCollector", "COLLECTORS", "start_file_watcher", "reload_collector"]