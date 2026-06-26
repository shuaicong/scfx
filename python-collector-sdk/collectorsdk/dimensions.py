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
    LIANGDAWANG = "liangdawang"  # 粮达网

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
    IMPORTED_GRAIN = "imported_grain"  # 进口粮
    LIVE_PIG = "live_pig"  # 生猪

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
    PRICE_INDEX = "price_index"  # 价格指数

    @classmethod
    def values(cls):
        return [e.value for e in cls]


class DimensionDict:
    """
    维度字典构建器 - 方便构建标准维度字典

    提供链式调用方式构建维度字典，并进行校验
    """

    def __init__(
        self,
        source: str | Source = "",
        subject: str | Subject = "",
        coll_type: str | CollectType = "",
        obj: str | CollectObject = "",
        remark: str = "",
    ):
        self._source = source.value if isinstance(source, Source) else source
        self._subject = subject.value if isinstance(subject, Subject) else subject
        self._coll_type = coll_type.value if isinstance(coll_type, CollectType) else coll_type
        self._obj = obj.value if isinstance(obj, CollectObject) else obj
        self._remark = remark

    def source(self, value: str | Source) -> "DimensionDict":
        """设置采集来源"""
        self._source = value.value if isinstance(value, Source) else value
        return self

    def subject(self, value: str | Subject) -> "DimensionDict":
        """设置采集主体"""
        self._subject = value.value if isinstance(value, Subject) else value
        return self

    def coll_type(self, value: str | CollectType) -> "DimensionDict":
        """设置采集类型"""
        self._coll_type = value.value if isinstance(value, CollectType) else value
        return self

    def obj(self, value: str | CollectObject) -> "DimensionDict":
        """设置采集对象"""
        self._obj = value.value if isinstance(value, CollectObject) else value
        return self

    def remark(self, value: str) -> "DimensionDict":
        """设置采集描述"""
        self._remark = value
        return self

    def build(self) -> dict:
        """构建维度字典"""
        return {
            "source": self._source,
            "subject": self._subject,
            "type": self._coll_type,
            "object": self._obj,
            "remark": self._remark,
        }

    def __repr__(self):
        return f"DimensionDict({self._source}, {self._subject}, {self._coll_type}, {self._obj})"


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
