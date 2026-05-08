import os
import httpx
from typing import Optional, AsyncGenerator

API_URL = os.getenv("SILICON_FLOW_URL", "https://api.siliconflow.cn/v1/chat/completions")
API_KEY = os.getenv("SILICON_FLOW_API_KEY", "")

STREAM_PROMPT_TEMPLATE = """一、核心定位与服务目标
你是专注于粮食价格领域的全网可信数据检索与分析助理，核心职能是从全网筛选权威、合规、可追溯的粮食行情数据及相关资讯（含稻谷、玉米、小麦、大豆等主流品种，覆盖全维度价格类型，同步涵盖供需、异动、政策、国际联动等资讯），提供价格查询、趋势研判等服务。

核心目标：数据可信、来源可溯、分析专业、输出精准、时效领先（优先抓取当天/近3天内最新数据，无则明确说明，严禁使用超3天非历史对比数据），杜绝虚假、不明来源、拼接数据，适配多场景决策需求。

二、核心能力与全网可信数据源规范

（一）全网可信数据源（全网检索，综合判断）

全网全面筛选可信数据源，不设分级，聚焦"近3天高频更新、可公开核验、来源可溯"，同一数据需至少2个数据源交叉验证，差异需说明原因。所有数据需标注完整来源（机构/平台、官网、发布时间、统计口径），所有数据源均优先抓取近3天内最新公开数据。

（二）数据源可信性排除标准

以下数据源严禁作为核心依据（辅助参考需标注风险），同时严守时效要求：

1. 无明确发布机构、时间、统计口径，且无法公开核验的匿名信息；
2. 个人自媒体、非专业平台的单一报价、无支撑主观预测；
3. 商业广告、营销文案中附带的非客观披露内容；
4. 非历史对比类超3天、趋势对比类未标注时间用途的滞后数据；
5. 拆分拼接不同数据源形成的非完整信息。

三、输出规范与场景示例

（一）输出核心要求

1. 结构：核心结论（突出近3天行情）+ 分层数据依据 + 完整来源 + 补充说明（差异/风险）；
2. 细节：价格标注金额、单位、时间、范围、口径，趋势说明变动幅度及驱动因素；
3. 专业：使用行业规范术语，简要解释专业概念，兼顾专业与易懂；
4. 风险：非官方、替代、预测数据明确标注风险，补充行情关联动态。

参考材料：
{context}

问题：{question}

要求：
1. 仅基于参考材料回答
2. 如资料不足，说明"根据现有资料无法回答"
3. 引用时标注来源
4. 回答使用中文
5. 保持专业但易于理解"""

async def generate_answer(
    question: str,
    context: str,
    model: str = "Qwen/Qwen2.5-7B-Instruct"
) -> str:
    """调用硅基流动 LLM 生成回答"""

    if not API_KEY:
        return f"【演示模式】基于以下信息回答您的问题：\n\n{context}\n\n问题：{question}\n\n（请配置 SILICON_FLOW_API_KEY 环境变量以启用真实 AI 回答）"

    prompt = f"""一、核心定位与服务目标
你是专注于粮食价格领域的全网可信数据检索与分析助理，核心职能是从全网筛选权威、合规、可追溯的粮食行情数据及相关资讯（含稻谷、玉米、小麦、大豆等主流品种，覆盖全维度价格类型，同步涵盖供需、异动、政策、国际联动等资讯），提供价格查询、趋势研判等服务。

核心目标：数据可信、来源可溯、分析专业、输出精准、时效领先（优先抓取当天/近3天内最新数据，无则明确说明，严禁使用超3天非历史对比数据），杜绝虚假、不明来源、拼接数据，适配多场景决策需求。

二、核心能力与全网可信数据源规范

（一）全网可信数据源（全网检索，综合判断）

全网全面筛选可信数据源，不设分级，聚焦"近3天高频更新、可公开核验、来源可溯"，同一数据需至少2个数据源交叉验证，差异需说明原因。所有数据需标注完整来源（机构/平台、官网、发布时间、统计口径），所有数据源均优先抓取近3天内最新公开数据。

（二）数据源可信性排除标准

以下数据源严禁作为核心依据（辅助参考需标注风险），同时严守时效要求：

1. 无明确发布机构、时间、统计口径，且无法公开核验的匿名信息；
2. 个人自媒体、非专业平台的单一报价、无支撑主观预测；
3. 商业广告、营销文案中附带的非客观披露内容；
4. 非历史对比类超3天、趋势对比类未标注时间用途的滞后数据；
5. 拆分拼接不同数据源形成的非完整信息。

三、输出规范与场景示例

（一）输出核心要求

1. 结构：核心结论（突出近3天行情）+ 分层数据依据 + 完整来源 + 补充说明（差异/风险）；
2. 细节：价格标注金额、单位、时间、范围、口径，趋势说明变动幅度及驱动因素；
3. 专业：使用行业规范术语，简要解释专业概念，兼顾专业与易懂；
4. 风险：非官方、替代、预测数据明确标注风险，补充行情关联动态。

参考材料：
{context}

问题：{question}

要求：
1. 仅基于参考材料回答
2. 如资料不足，说明"根据现有资料无法回答"
3. 引用时标注来源
4. 回答使用中文
5. 保持专业但易于理解"""

    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            response = await client.post(
                API_URL,
                json={
                    "model": model,
                    "messages": [
                        {"role": "system", "content": "你是一个专业的农业情报助手。"},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.7,
                    "max_tokens": 2000
                },
                headers={
                    "Authorization": f"Bearer {API_KEY}",
                    "Content-Type": "application/json"
                }
            )
            result = response.json()
            if "choices" in result and len(result["choices"]) > 0:
                return result["choices"][0]["message"]["content"]
            else:
                return f"AI 服务返回异常：{result}"
    except Exception as e:
        return f"调用 AI 服务失败：{str(e)}\n\n参考材料：\n{context}"


async def generate_answer_stream(
    question: str,
    context: str,
    model: str = "Qwen/Qwen2.5-7B-Instruct"
) -> AsyncGenerator[dict, None]:
    """流式调用硅基流动 LLM 生成回答，逐字符yield返回"""

    if not API_KEY:
        # 演示模式，直接返回演示内容
        demo_content = f"【演示模式】基于以下信息回答您的问题：\n\n{context}\n\n问题：{question}\n\n（请配置 SILICON_FLOW_API_KEY 环境变量以启用真实 AI 回答）"
        for char in demo_content:
            yield {"type": "text", "content": char}
        return

    prompt = STREAM_PROMPT_TEMPLATE.format(context=context, question=question)

    try:
        async with httpx.AsyncClient(timeout=120.0) as client:
            async with client.stream(
                "POST",
                API_URL,
                json={
                    "model": model,
                    "messages": [
                        {"role": "system", "content": "你是一个专业的农业情报助手。"},
                        {"role": "user", "content": prompt}
                    ],
                    "temperature": 0.7,
                    "max_tokens": 2000,
                    "stream": True
                },
                headers={
                    "Authorization": f"Bearer {API_KEY}",
                    "Content-Type": "application/json"
                }
            ) as response:
                async for line in response.aiter_lines():
                    if line.startswith("data: "):
                        data = line[6:]  # Remove "data: " prefix
                        if data == "[DONE]":
                            break
                        import json
                        try:
                            chunk = json.loads(data)
                            if "choices" in chunk and len(chunk["choices"]) > 0:
                                delta = chunk["choices"][0].get("delta", {})
                                content = delta.get("content", "")
                                for char in content:
                                    yield {"type": "text", "content": char}
                        except json.JSONDecodeError:
                            continue
    except Exception as e:
        yield {"type": "error", "content": str(e)}