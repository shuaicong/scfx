"""
采集维度枚举定义

5个核心维度：
- source: 采集来源（数据来自哪个网站/系统）
- subject: 采集主体（业务主体，如玉米、小麦）
- type: 采集类型（采集方式）
- object: 采集对象（具体采集目标）
- remark: 采集描述（任务一句话说明）
"""

from enum import Enum


class Source(Enum):
    """采集来源枚举"""

    LIANGXIN = "liangxin"  # 粮信网
    MYSTEEL = "mysteel"  # 我的钢铁网
    CHINAGRAIN = "chinagrain"  # 中华粮网
    USDA = "usda"  # 美国农业部
    GOV_BR = "gov_br"  # 巴西政府网
    WEATHER = "weather"  # 气象数据
    CUSTOM = "custom"  # 自定义数据源

    @classmethod
    def values(cls):
        return [e.value for e in cls]


class Subject(Enum):
    """采集主体枚举"""

    CORN = "corn"  # 玉米
    WHEAT = "wheat"  # 小麦
    RICE = "rice"  # 稻米
    SOYBEAN = "soybean"  # 大豆
    BARLEY = "barley"  # 大麦
    WEATHER = "weather"  # 气象
    POLICY = "policy"  # 政策
    REPORT = "report"  # 行业报告
    PRICE = "price"  # 价格
    INVENTORY = "inventory"  # 库存
    SHIP = "ship"  # 船期

    @classmethod
    def values(cls):
        return [e.value for e in cls]


class CollectType(Enum):
    """采集类型枚举"""

    LOGIN_CRAWL = "login_crawl"  # 模拟登录爬取
    PUBLIC_CRAWL = "public_crawl"  # 公开页面爬取
    API_COLLECT = "api_collect"  # 接口采集
    FILE_DOWNLOAD = "file_download"  # 文件下载（PDF/Word）
    PAGE_PARSE = "page_parse"  # 页面解析
    SCRAPE = "scrape"  # 通用爬取

    @classmethod
    def values(cls):
        return [e.value for e in cls]


class CollectObject(Enum):
    """采集对象枚举"""

    DAILY_REPORT = "daily_report"  # 日报
    WEEKLY_REPORT = "weekly_report"  # 周报
    MONTHLY_REPORT = "monthly_report"  # 月报
    SPECIAL_REPORT = "special_report"  # 专题报告
    NEWS = "news"  # 资讯
    PRICE = "price"  # 价格数据
    INVENTORY = "inventory"  # 库存数据
    ARTICLE = "article"  # 文章
    SHIP_SCHEDULE = "ship_schedule"  # 船期
    POLICY_DOC = "policy_doc"  # 政策文件
    WEATHER_DATA = "weather_data"  # 气象数据

    @classmethod
    def values(cls):
        return [e.value for e in cls]


class Dimensions:
    """
    维度容器 - 封装5个核心维度
    用于在上报时携带统一维度信息
    """

    def __init__(
        self,
        source: str,
        subject: str,
        coll_type: str,
        obj: str,
        remark: str = "",
    ):
        self.source = source
        self.subject = subject
        self.coll_type = coll_type
        self.object = obj
        self.remark = remark

    def to_dict(self) -> dict:
        return {
            "source": self.source,
            "subject": self.subject,
            "type": self.coll_type,
            "object": self.object,
            "remark": self.remark,
        }

    def __repr__(self):
        return f"Dimensions({self.source}, {self.subject}, {self.coll_type}, {self.object})"
