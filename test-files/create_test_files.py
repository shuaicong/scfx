#!/usr/bin/env python3
"""创建测试文件并上传到知识库"""

import os
import uuid

# 创建测试文件目录
TEST_DIR = os.path.dirname(os.path.abspath(__file__))
STORAGE_DIR = os.path.join(TEST_DIR, "storage")

os.makedirs(STORAGE_DIR, exist_ok=True)

def create_test_files():
    """创建测试文件"""

    # 1. 创建测试文本文件
    txt_content = """粮食价格周报
=============

本周粮食市场综述：

一、玉米价格走势
本周玉米价格呈现稳中略涨态势。东北产区玉米收购价格维持在2400-2500元/吨区间，
华北产区玉米价格约2550-2650元/吨。

二、小麦市场动态
小麦价格整体平稳，局部地区小幅波动。面粉加工企业收购价约2800-2900元/吨。

三、稻谷行情
早籼稻价格约2700-2800元/吨，中晚籼稻约2850-2950元/吨，粳稻约3000-3100元/吨。

四、后市展望
预计短期内粮食价格将维持当前水平波动，建议密切关注国家政策调控信息。
"""

    txt_path = os.path.join(STORAGE_DIR, "test_report.txt")
    with open(txt_path, "w", encoding="utf-8") as f:
        f.write(txt_content)
    print(f"Created: {txt_path}")

    # 2. 创建测试 Markdown 文件
    md_content = """# 粮食市场分析报告

## 一、行情概述

本周（2024年第15周）粮食市场整体平稳，主要品种价格变化如下：

| 品种 | 产区 | 价格区间（元/吨） | 环比 |
|------|------|------------------|------|
| 玉米 | 东北 | 2400-2500 | +0.5% |
| 小麦 | 华北 | 2800-2900 | 持平 |
| 稻谷 | 南方 | 2700-3100 | -0.3% |

## 二、市场分析

### 2.1 利好因素

- **政策支撑**：国家对粮食生产给予补贴支持
- **需求稳定**：饲料和深加工需求保持平稳
- **进口减少**：国际粮价高企，进口量同比下降

### 2.2 利空因素

- **售粮进度偏慢**：农户余粮同比偏多
- **替代品冲击**：饲料中玉米替代品使用增加
- **国际传导**：国际价格波动对国内市场情绪影响

## 三、后市研判

短期来看，粮食价格仍将维持震荡偏弱走势。中长期需关注：
1. 天气变化对春播影响
2. 国家政策性粮食拍卖动向
3. 国际市场动态

> 风险提示：以上分析仅供参考，投资有风险，入市需谨慎。
"""

    md_path = os.path.join(STORAGE_DIR, "test_market_analysis.md")
    with open(md_path, "w", encoding="utf-8") as f:
        f.write(md_content)
    print(f"Created: {md_path}")

    # 3. 创建模拟 PDF 文件（二进制）
    # 注意：这是一个占位符，实际 PDF 需要特殊生成
    pdf_header = b"%PDF-1.4\n1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n"
    pdf_content = pdf_header + b"Test PDF content - 粮食价格报告"
    pdf_path = os.path.join(STORAGE_DIR, "test_report.pdf")
    with open(pdf_path, "wb") as f:
        f.write(pdf_content)
    print(f"Created: {pdf_path}")

    return [txt_path, md_path, pdf_path]

if __name__ == "__main__":
    print("Creating test files...")
    files = create_test_files()
    print(f"\nCreated {len(files)} test files in: {STORAGE_DIR}")