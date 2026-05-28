from playwright.sync_api import sync_playwright
import time

# 先登录，再访问报告页面
LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
REPORT_URL = "https://www.chinagrain.cn/report/"

USERNAME = "33022"
PASSWORD = "qlp707"

def explore_report_page():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        page = context.new_page()

        # 1. 登录
        print("正在登录...")
        page.goto(LOGIN_URL)
        page.fill('#username', USERNAME)
        page.fill('#password', PASSWORD)
        page.click('input[type="submit"]')
        time.sleep(5)

        # 2. 访问报告页面
        print("访问报告页面...")
        page.goto(REPORT_URL)
        time.sleep(3)

        # 3. 截图查看页面结构
        page.screenshot(path="report_page.png", full_page=True)
        print("已截图: report_page.png")

        # 4. 分析页面元素
        print("\n=== 查找页面标题 ===")
        title = page.title()
        print(f"页面标题: {title}")

        print("\n=== 查找所有链接 ===")
        links = page.query_selector_all('a')
        hrefs = []
        for link in links[:10]:  # 只取前10个链接示例
            href = link.get_attribute('href')
            text = link.inner_text()
            if href and text:
                hrefs.append((text, href))

        for text, href in hrefs:
            print(f"  文本: {text}")
            print(f"  链接: {href}")
            print()

        # 5. 查找可能的报告列表
        print("\n=== 查找报告列表相关的元素 ===")

        # 查找包含"报告"的标题
        headers = page.query_selector_all('h1, h2, h3, h4')
        for i, header in enumerate(headers):
            text = header.inner_text()
            if text and len(text.strip()) > 0:
                print(f"  标题 {i+1}: {text}")

        # 查找列表项
        list_items = page.query_selector_all('li, .list-item, .item, tr')
        for i, item in enumerate(list_items[:5]):  # 只取前5个示例
            text = item.inner_text()
            if text and len(text.strip()) > 10:  # 过滤太短的文本
                print(f"  列表项 {i+1}: {text[:100]}...")

        # 6. 查找日期相关元素
        print("\n=== 查找日期相关元素 ===")
        date_elements = page.query_selector_all('[class*="date"], [id*="date"], .date, #date, time')
        for i, elem in enumerate(date_elements):
            text = elem.inner_text()
            if text and len(text.strip()) > 0:
                print(f"  日期元素 {i+1}: {text}")

        # 7. 等待用户查看截图
        print("\n已生成截图，请在浏览器中查看 report_page.png")
        print("按回车键继续分析详情页...")
        input()

        # 8. 尝试点击第一个可能的报告链接
        print("\n=== 尝试分析第一个报告详情 ===")

        # 查找所有链接，找可能的报告链接
        report_links = []
        for link in links:
            href = link.get_attribute('href')
            text = link.inner_text().lower()
            # 寻找可能的报告链接
            if href and 'report' in href.lower() or ('玉米' in text) or ('资讯' in text) or ('新闻' in text):
                report_links.append((link, href, text))

        if report_links:
            print("发现可能的报告链接:")
            for i, (link, href, text) in enumerate(report_links[:3]):
                print(f"  {i+1}. {text} -> {href}")

            # 点击第一个
            first_link, first_href, first_text = report_links[0]
            print(f"\n点击: {first_text}")
            first_link.click()
            time.sleep(3)

            # 截取详情页
            page.screenshot(path="report_detail.png", full_page=True)
            print("已截图: report_detail.png")

            # 分析详情页
            print("\n=== 分析详情页结构 ===")
            detail_text = page.inner_text('body')
            print(f"详情页内容长度: {len(detail_text)} 字符")

            # 提取可能的标题
            detail_headers = page.query_selector_all('h1, h2, h3, .title, #title')
            for header in detail_headers:
                text = header.inner_text()
                if text and len(text.strip()) > 0:
                    print(f"  页面标题: {text}")

            # 提取可能的正文
            print("\n=== 尝试识别正文区域 ===")
            main_content = page.query_selector_all('.content, .article, .main, #content, #article')
            for i, content in enumerate(main_content[:3]):
                text = content.inner_text()
                if text and len(text.strip()) > 200:
                    print(f"  内容区域 {i+1}: {text[:200]}...")

            print("\n请在浏览器中查看详情页截图")
        else:
            print("没有找到明显的报告链接")

        browser.close()

if __name__ == "__main__":
    explore_report_page()