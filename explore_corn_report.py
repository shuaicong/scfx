from playwright.sync_api import sync_playwright
import time
from datetime import datetime

# 配置
LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
REPORT_URL = "https://www.chinagrain.cn/report/"
USERNAME = "33022"
PASSWORD = "qlp707"

def explore_corn_report():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        page = context.new_page()

        # 1. 登录
        print("步骤1：登录...")
        page.goto(LOGIN_URL)
        page.fill('#username', USERNAME)
        page.fill('#password', PASSWORD)
        page.click('input[type="submit"]')
        time.sleep(5)

        # 2. 访问报告页面
        print("步骤2：访问报告页面...")
        page.goto(REPORT_URL)
        time.sleep(3)

        # 3. 查找今天的玉米报告
        print("步骤3：查找今天的玉米报告...")
        now = datetime.now()
        today = f"（{now.year}年{now.month}月{now.day}日）"
        print(f"今天的日期格式: {today}")

        # 查找所有链接
        all_links = page.query_selector_all('a')
        corn_report_link = None

        for link in all_links:
            text = link.inner_text().strip()
            # 查找今天且包含"玉米"的报告
            if today in text and "玉米" in text:
                href = link.get_attribute('href')
                print(f"找到玉米报告: {text}")
                print(f"链接: {href}")
                corn_report_link = (link, href)
                break

        if not corn_report_link:
            print("今天没有找到玉米报告，查找昨天的...")
            yesterday = datetime.now()
            yesterday = yesterday.replace(day=yesterday.day - 1)
            yesterday_str = yesterday.strftime("（%Y年%m月%d日）")

            for link in all_links:
                text = link.inner_text().strip()
                if yesterday_str in text and "玉米" in text:
                    href = link.get_attribute('href')
                    print(f"找到玉米报告: {text}")
                    print(f"链接: {href}")
                    corn_report_link = (link, href)
                    break

        if corn_report_link:
            # 4. 点击玉米报告
            print("\n步骤4：点击玉米报告详情...")
            link, href = corn_report_link

            # 如果是相对链接，转换为绝对链接
            if href and not href.startswith('http'):
                href = "https://www.chinagrain.cn" + href

            page.goto(href)
            time.sleep(3)

            # 5. 分析详情页结构
            print("\n步骤5：分析详情页结构...")

            # 截图
            page.screenshot(path="corn_report_detail.png", full_page=True)
            print("已截图: corn_report_detail.png")

            # 提取标题
            print("\n=== 提取标题 ===")
            titles = page.query_selector_all('h1, h2, .title, #title')
            for i, title in enumerate(titles):
                text = title.inner_text().strip()
                if text:
                    print(f"标题{i+1}: {text}")

            # 提取元数据
            print("\n=== 提取元数据（时间、来源、作者等） ===")

            # 查找可能包含元数据的区域
            meta_elements = page.query_selector_all('.meta, .info, .author, .source, .time, .date')
            for elem in meta_elements:
                text = elem.inner_text().strip()
                if text and len(text) < 200:  # 过滤正文内容
                    print(f"元数据: {text}")

            # 查找所有带class的元素，分析可能的信息
            print("\n=== 分析所有class包含关键字的元素 ===")
            all_elements = page.query_selector_all('[class*="time"], [class*="date"], [class*="author"], [class*="source"], [class*="editor"], [class*="origin"]')
            for i, elem in enumerate(all_elements):
                text = elem.inner_text().strip()
                class_name = elem.get_attribute('class')
                if text:
                    print(f"  元素{i+1} (class={class_name}): {text}")

            # 提取正文
            print("\n=== 提取正文内容 ===")
            content_selectors = [
                '.content', '.article', '.main-content', '#content', '#article',
                '.article-content', '.news-content', '.report-content', '.detail-content'
            ]

            content_text = ""
            for selector in content_selectors:
                content = page.query_selector(selector)
                if content:
                    text = content.inner_text().strip()
                    if len(text) > 100:  # 确保是正文内容
                        content_text = text
                        print(f"找到正文区域 (选择器: {selector})")
                        print(f"正文长度: {len(text)} 字符")
                        print(f"正文前200字: {text[:200]}...")
                        break

            if not content_text:
                # 如果没找到，尝试获取整个body
                body_text = page.inner_text('body')
                print(f"未找到特定正文区域，使用body内容")
                print(f"内容长度: {len(body_text)} 字符")
                print(f"前200字: {body_text[:200]}...")

            # 查找表格（如果有价格数据）
            print("\n=== 查找表格数据 ===")
            tables = page.query_selector_all('table')
            print(f"找到 {len(tables)} 个表格")
            for i, table in enumerate(tables):
                rows = table.query_selector_all('tr')
                if len(rows) > 1:
                    print(f"表格{i+1}有 {len(rows)} 行")
                    # 显示第一行（表头）
                    first_row = rows[0].inner_text().strip()
                    print(f"  表头: {first_row}")

            # 获取页面HTML结构
            print("\n=== 获取页面HTML结构示例 ===")
            page_html = page.content()

            # 保存HTML到文件
            with open("corn_report_html.txt", "w", encoding="utf-8") as f:
                f.write(page_html)
            print("已保存HTML到: corn_report_html.txt")

            print("\n=== 分析完成 ===")
            print("请查看以下文件：")
            print("1. corn_report_detail.png - 页面截图")
            print("2. corn_report_html.txt - 完整HTML源码")

        else:
            print("没有找到玉米报告")

        print("\n按回车键关闭浏览器（或者等待10秒自动关闭）...")
        time.sleep(10)
        browser.close()

if __name__ == "__main__":
    explore_corn_report()
