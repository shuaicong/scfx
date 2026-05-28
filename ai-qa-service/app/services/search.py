import os
import httpx

# Tavily 配置
TAVILY_API_KEY = os.getenv("TAVILY_API_KEY", "")
TAVILY_API_URL = "https://api.tavily.com/search"

# Serper 配置
SERPER_API_KEY = os.getenv("SERPER_API_KEY", "")
SERPER_API_URL = "https://google.serper.dev/search"

class SearchServiceSwitcher:
    """搜索服务自动切换器"""

    def __init__(self):
        self.tavily_available = True
        self.serper_available = True
        self.current = "tavily"

    def switch_to(self, service: str):
        self.current = service

    def mark_unavailable(self, service: str):
        if service == "tavily":
            self.tavily_available = False
            if self.serper_available:
                self.switch_to("serper")
        elif service == "serper":
            self.serper_available = False

    def reset(self):
        self.tavily_available = True
        self.serper_available = True
        self.current = "tavily"

switcher = SearchServiceSwitcher()

async def search_internet(query: str, depth: str = "basic") -> dict:
    """联网搜索，自动切换服务"""
    global switcher

    if switcher.current == "tavily" and switcher.tavily_available:
        result = await _search_tavily(query, depth)
        if result.get("error") == "quota_exceeded":
            switcher.mark_unavailable("tavily")
            if switcher.serper_available:
                result = await _search_serper(query)
        return result
    elif switcher.current == "serper" and switcher.serper_available:
        result = await _search_serper(query)
        if result.get("error") == "quota_exceeded":
            switcher.mark_unavailable("serper")
            if switcher.tavily_available:
                result = await _search_tavily(query, depth)
        return result
    else:
        if switcher.tavily_available:
            return await _search_tavily(query, depth)
        elif switcher.serper_available:
            return await _search_serper(query)
        return {"results": [], "error": "All search services unavailable"}

async def _search_tavily(query: str, depth: str = "basic") -> dict:
    if not TAVILY_API_KEY:
        return {"results": [], "error": "TAVILY_API_KEY not configured"}
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                TAVILY_API_URL,
                json={
                    "api_key": TAVILY_API_KEY,
                    "query": query,
                    "search_depth": depth,
                    "max_results": 5,
                    "include_answer": True,
                    "include_raw_content": False,
                    "include_images": False
                },
                headers={"Content-Type": "application/json"}
            )
            if response.status_code == 429:
                return {"results": [], "error": "quota_exceeded", "source": "tavily"}
            result = response.json()
            return {
                "results": [
                    {"title": item.get("title", ""), "url": item.get("url", ""), "content": item.get("content", ""), "source": "web"}
                    for item in result.get("results", [])
                ],
                "answer": result.get("answer"),
                "source": "tavily"
            }
    except Exception as e:
        return {"results": [], "error": str(e), "source": "tavily"}

async def _search_serper(query: str) -> dict:
    if not SERPER_API_KEY:
        return {"results": [], "error": "SERPER_API_KEY not configured"}
    try:
        async with httpx.AsyncClient(timeout=30.0) as client:
            response = await client.post(
                SERPER_API_URL,
                json={"q": query, "num": 5},
                headers={"Content-Type": "application/json", "X-API-Key": SERPER_API_KEY}
            )
            if response.status_code == 429:
                return {"results": [], "error": "quota_exceeded", "source": "serper"}
            result = response.json()
            organic_results = result.get("organic", [])
            return {
                "results": [
                    {"title": item.get("title", ""), "url": item.get("link", ""), "content": item.get("snippet", ""), "source": "web"}
                    for item in organic_results
                ],
                "source": "serper"
            }
    except Exception as e:
        return {"results": [], "error": str(e), "source": "serper"}

def get_current_service() -> str:
    return switcher.current