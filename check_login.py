from playwright.sync_api import sync_playwright
import time

USERNAME = "33022"
PASSWORD = "qlp707"
LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"

def run_login():
    with sync_playwright() as p:
        browser = p.chromium.launch(headless=False)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        page = context.new_page()

        print("正在打开登录页...")
        page.goto(LOGIN_URL)
        time.sleep(2)

        print("输入用户名...")
        page.fill('#username', USERNAME)
        time.sleep(0.5)

        print("输入密码...")
        page.fill('#password', PASSWORD)
        time.sleep(0.5)

        print("点击登录...")
        page.click('input[type="submit"]')

        # 等待页面响应
        time.sleep(5)

        # 检查是否还在登录页
        current_url = page.url
        print(f"\n当前URL: {current_url}")

        # 检查是否有错误提示
        error_divs = page.query_selector_all('.red-warn-p')
        if error_divs:
            print("\n发现错误提示:")
            for div in error_divs:
                text = div.inner_text()
                if text and text.strip():
                    print(f"  - {text}")

        # 截图
        page.screenshot(path="login_check.png")
        print("\n已截图: login_check.png")

        # 检查页面标题
        title = page.title()
        print(f"页面标题: {title}")

        browser.close()

if __name__ == "__main__":
    run_login()
