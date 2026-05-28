from playwright.sync_api import sync_playwright
import time

# 你的账号密码
USERNAME = "33022"
PASSWORD = "qlp707"

# 粮信网官方登录页
LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"

def run_login():
    with sync_playwright() as p:
        # 启动浏览器
        browser = p.chromium.launch(headless=False)
        context = browser.new_context(
            user_agent="Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36"
        )
        page = context.new_page()

        # ==============================================
        # 第一步：打开登录页（严格按你给的URL）
        # ==============================================
        print("正在打开登录页：", LOGIN_URL)
        page.goto(LOGIN_URL)
        time.sleep(2)

        # ==============================================
        # 第二步：输入账号
        # ==============================================
        print("输入用户名...")
        page.fill('#username', USERNAME)
        time.sleep(0.5)

        # ==============================================
        # 第三步：输入密码
        # ==============================================
        print("输入密码...")
        page.fill('#password', PASSWORD)
        time.sleep(0.5)

        # ==============================================
        # 第四步：点击登录按钮
        # ==============================================
        print("点击登录...")
        page.click('input[type="submit"]')

        # 等待页面跳转
        time.sleep(5)

        # 检查登录结果
        current_url = page.url
        print(f"登录后URL: {current_url}")

        # 检查是否有错误提示
        error_divs = page.query_selector_all('.red-warn-p')
        if error_divs:
            for div in error_divs:
                text = div.inner_text()
                if text and text.strip():
                    print(f"错误提示: {text}")

        # 登录完成
        print("=== 登录流程执行完毕 ===")
        page.screenshot(path="login_result.png")
        print("已截图：login_result.png（可查看登录状态）")

        browser.close()

if __name__ == "__main__":
    run_login()