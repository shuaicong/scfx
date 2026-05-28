from playwright.sync_api import sync_playwright
import time

LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"

with sync_playwright() as p:
    browser = p.chromium.launch(headless=False)
    page = browser.new_page()
    page.goto(LOGIN_URL)
    time.sleep(3)

    # 截图保存
    page.screenshot(path="debug_page.png", full_page=True)
    print("已截图: debug_page.png")

    # 查找所有按钮
    print("\n=== 查找所有按钮 ===")
    buttons = page.query_selector_all('button')
    for i, btn in enumerate(buttons):
        print(f"\n按钮 {i+1}:")
        print(f"  Text: {btn.inner_text()}")
        print(f"  HTML: {btn.evaluate('el => el.outerHTML')}")

    # 查找登录相关的元素
    print("\n=== 查找登录表单 ===")
    forms = page.query_selector_all('form')
    for i, form in enumerate(forms):
        print(f"\n表单 {i+1}: {form.evaluate('el => el.outerHTML')}")

    # 查找input元素
    print("\n=== 查找输入框 ===")
    inputs = page.query_selector_all('input')
    for i, inp in enumerate(inputs):
        print(f"\n输入框 {i+1}:")
        print(f"  Type: {inp.get_attribute('type')}")
        print(f"  Name: {inp.get_attribute('name')}")
        print(f"  ID: {inp.get_attribute('id')}")
        print(f"  Placeholder: {inp.get_attribute('placeholder')}")

    print("\n按回车关闭浏览器...")
    input()

    browser.close()
