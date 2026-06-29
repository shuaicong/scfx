#!/usr/bin/env python3
"""
采集SDK CLI

从后端动态获取数据源和采集脚本，不再硬编码子命令。

Usage:
    python main.py list                        # 列出所有数据源
    python main.py run <code> [options]         # 运行指定采集器
    python main.py run <code> --local           # 从本地 dev/collectors/ 加载
"""

import argparse
import importlib.util
import inspect
import json
import os
import re
import sys
from pathlib import Path
from urllib.request import Request, urlopen
from urllib.error import URLError

# 添加项目路径
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))

from collectorsdk import ReporterConfig, BaseCollector

CACHE_DIR = os.path.expanduser("~/.cache/collectors")
DEV_DIR = os.path.join(os.path.dirname(os.path.abspath(__file__)), "dev", "collectors")
API_BASE_DEFAULT = "http://localhost:8080/api"


def api_get(url):
    """调用后端 GET API"""
    req = Request(url)
    try:
        with urlopen(req, timeout=10) as resp:
            return json.loads(resp.read().decode("utf-8"))
    except URLError as e:
        sys.stderr.write(f"API 请求失败: {e}\n")
        sys.exit(1)


def fetch_data_sources(api_base):
    """从后端获取所有数据源"""
    result = api_get(f"{api_base}/datasource")
    if result.get("code") != 200:
        sys.stderr.write(f"获取数据源失败: {result.get('message')}\n")
        sys.exit(1)
    return result["data"]


def check_script_exists(api_base, code):
    """检查数据源是否有已上传的采集脚本"""
    try:
        result = api_get(f"{api_base}/datasource/{code}/script?version=0")
        return result.get("code") == 200 and bool(result.get("data"))
    except (URLError, json.JSONDecodeError, SystemExit):
        return False


def download_script(api_base, code):
    """从后端下载脚本到缓存目录"""
    result = api_get(f"{api_base}/datasource/{code}/script?version=0")
    if result.get("code") != 200:
        sys.stderr.write(f"获取脚本失败: {result.get('message')}\n")
        sys.exit(1)

    content = result["data"]
    if not content:
        sys.stderr.write(f"数据源 '{code}' 尚未上传采集脚本\n")
        sys.exit(1)

    os.makedirs(CACHE_DIR, exist_ok=True)
    cache_path = os.path.join(CACHE_DIR, f"{code}.py")
    with open(cache_path, "w", encoding="utf-8") as f:
        f.write(content)
    return cache_path


def load_collector_module(code, local=False):
    """加载采集器模块，返回 (module, collector_class)"""
    if local:
        module_path = os.path.join(DEV_DIR, f"{code}.py")
        if not os.path.exists(module_path):
            sys.stderr.write(f"本地开发目录未找到脚本: {module_path}\n")
            sys.exit(1)
    else:
        module_path = os.path.join(CACHE_DIR, f"{code}.py")
        if not os.path.exists(module_path):
            sys.stderr.write(f"脚本未缓存，请先运行: python main.py run {code}\n")
            sys.exit(1)

    load_dir = os.path.dirname(module_path)
    if load_dir not in sys.path:
        sys.path.insert(0, load_dir)

    spec = importlib.util.spec_from_file_location(code, module_path)
    if spec is None or spec.loader is None:
        sys.stderr.write(f"无法加载模块: {module_path}\n")
        sys.exit(1)

    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)

    # 查找第一个非抽象 BaseCollector 子类（仅限当前模块定义的类，排除 import 进来的）
    for attr_name in dir(module):
        cls = getattr(module, attr_name)
        if (isinstance(cls, type)
                and issubclass(cls, BaseCollector)
                and cls is not BaseCollector
                and not inspect.isabstract(cls)
                and cls.__module__ == module.__name__):
            return module, cls

    sys.stderr.write(f"脚本中未找到 BaseCollector 实现类: {module_path}\n")
    sys.exit(1)


def build_collector_params(collector_class, code, args):
    """根据 __init__ 签名推断构造参数

    已知的参数名直接匹配，未知参数尝试从环境变量 {CODE}_{NAME} 读取。
    """
    sig = inspect.signature(collector_class.__init__)
    params = {}

    KNOWN = {"config", "task_id", "execution_id", "target_date"}

    for name, param in sig.parameters.items():
        if name == "self":
            continue

        if name == "config":
            params[name] = ReporterConfig(
                api_base=args.api_base or API_BASE_DEFAULT,
                enabled=not args.no_report,
            )
        elif name == "task_id":
            params[name] = args.task_id
        elif name == "execution_id":
            params[name] = args.execution_id
        elif name == "target_date":
            params[name] = args.date
        else:
            # 尝试环境变量 {CODE}_{NAME}
            # 数据源编码中的特殊字符替换为下划线，多个连续特殊字符合并为一个下划线
            safe_code = re.sub(r'[^a-zA-Z0-9]+', '_', code).upper().strip('_')
            env_key = f"{safe_code}_{name.upper()}"
            env_val = os.environ.get(env_key)
            if env_val is not None:
                # 带类型注解的参数尝试类型转换
                if param.annotation is not inspect.Parameter.empty and param.annotation is int:
                    try:
                        params[name] = int(env_val)
                    except ValueError:
                        params[name] = env_val
                else:
                    params[name] = env_val
            elif param.default is not inspect.Parameter.empty:
                continue  # 有默认值，跳过
            else:
                sys.stderr.write(f"缺少必要参数 '{name}'，请设置环境变量 {env_key}\n")
                sys.exit(1)

    return params


# ── 命令实现 ──────────────────────────────────────────

def cmd_list(args):
    """列出所有数据源及脚本状态"""
    api_base = args.api_base or API_BASE_DEFAULT
    sources = fetch_data_sources(api_base)

    print(f"\n可用数据源 (API: {api_base}):")
    print(f"  {'编码':<20} {'名称':<20} {'状态'}")
    print(f"  {'─' * 54}")

    for ds in sources:
        code = ds["code"]
        name = ds.get("name") or ""
        has = check_script_exists(api_base, code)
        status = "✅ 有采集脚本" if has else "❌ 未上传脚本"
        print(f"  {code:<20} {name:<20} {status}")


def cmd_run(args):
    """运行指定采集器"""
    code = args.code
    api_base = args.api_base or API_BASE_DEFAULT

    # 优先尝试从 SDK 内置采集器加载（liangdawang 等预置采集器）
    module, collector_class = None, None
    try:
        sdk_module = importlib.import_module(f"collectorsdk.collectors.{code}")
        for attr_name in dir(sdk_module):
            cls = getattr(sdk_module, attr_name)
            if (isinstance(cls, type)
                    and issubclass(cls, BaseCollector)
                    and cls is not BaseCollector
                    and not inspect.isabstract(cls)):
                module, collector_class = sdk_module, cls
                break
    except (ImportError, ModuleNotFoundError):
        pass

    # SDK 没有内置采集器 → 从后端下载
    if collector_class is None:
        if not args.local:
            download_script(api_base, code)
        module, collector_class = load_collector_module(code, local=args.local)

    params = build_collector_params(collector_class, code, args)

    # 自动获取 task_id（从 active-script 接口），覆盖默认值
    if not args.task_id or args.task_id == 1:
        try:
            resp_data = api_get(f"{api_base}/datasource/{code}/active-script")
            if resp_data.get("code") == 200:
                params["task_id"] = resp_data["data"]["id"]
        except Exception:
            pass  # 拿不到就用默认值

    collector = collector_class(**params)
    collector.run()


# ── 主入口 ─────────────────────────────────────────

def main():
    parser = argparse.ArgumentParser(
        description="采集SDK CLI - 动态从后端获取数据源和采集脚本",
        formatter_class=argparse.RawDescriptionHelpFormatter,
        epilog="""
示例:
  python main.py list                    # 列出数据源
  python main.py run liangxin             # 运行粮信网采集器
  LIANGXIN_USERNAME=xxx LIANGXIN_PASSWORD=yyy python main.py run liangxin
  python main.py run liangxin --local     # 本地开发模式
        """,
    )
    parser.add_argument("--api-base", default=None, help=f"API 地址 (默认 {API_BASE_DEFAULT})")

    sub = parser.add_subparsers(dest="command", help="子命令")

    # list
    sub.add_parser("list", help="列出所有数据源及脚本状态")

    # run
    p = sub.add_parser("run", help="运行指定采集器")
    p.add_argument("code", help="数据源编码 (如 liangxin)")
    p.add_argument("--task-id", type=int, default=0, help="任务ID（默认自动获取）")
    p.add_argument("--execution-id", default=None, help="执行ID (不传则自动创建)")
    p.add_argument("--no-report", action="store_true", help="禁用上报（本地调试）")
    p.add_argument("--local", action="store_true", help="从 dev/collectors/ 加载脚本，不从后端下载")
    p.add_argument("--date", default=None, help="目标采集日期 (yyyy-MM-dd)，不传则默认今天")

    args = parser.parse_args()

    if args.command == "list":
        cmd_list(args)
    elif args.command == "run":
        cmd_run(args)
    else:
        parser.print_help()


if __name__ == "__main__":
    main()
