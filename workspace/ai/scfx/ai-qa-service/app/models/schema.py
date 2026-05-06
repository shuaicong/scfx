from pydantic import BaseModel
from typing import Optional, List

class ReportItem(BaseModel):
    title: str
    source: str
    url: Optional[str] = None
    author: Optional[str] = None
    publishTime: Optional[str] = None
    content: str

class IngestRequest(BaseModel):
    executionId: str
    source: str
    reports: List[ReportItem]

class ManualAddRequest(BaseModel):
    title: str
    content: str
    source: str = "人工录入"
    author: Optional[str] = None
    publishTime: Optional[str] = None
