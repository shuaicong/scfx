#!/usr/bin/env python3
"""
粮信网登录测试 - 技术分析脚本
目标：分析登录机制和VIP报告页面结构
"""

import os
import sys
import time
from datetime import datetime

# 尝试导入selenium，如果未安装则提示
try:
    from selenium import webdriver
    from selenium.webdriver.common.by import By
    from selenium.webdriver.chrome.service import Service
    from selenium.webdriver.chrome.options import Options
    from selenium.webdriver.support.ui import WebDriverWait
    from selenium.webdriver.support import expected_conditions as EC
except ImportError:
    print("错误：需要安装 selenium")
    print("安装命令：pip install selenium")
    sys.exit(1)

# 账号配置
USERNAME = "33022"
PASSWORD = "qlp707"

# 测试URL
LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
VIP_REPORT_URL = "https://my.chinagrain.cn/jinnong/liangyou_vipController.htm?newsid=5744881"
REPORT_LIST_URL = "https://www.chinagrain.cn/report/"


def create_driver():
    """创建Chrome浏览器驱动"""
    options = Options()
    options.add_argument('--headless')  # 无头模式
    options.add_argument('--no-sandbox')
    options.add_argument('--disable-dev-shm-usage')
    options.add_argument('--disable-gpu')
    options.add_argument('--window-size=1920,1080')
    options.add_argument('--user-agent=Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36')

    # 使用Homebrew安装的chromedriver
    service = Service("/usr/local/bin/chromedriver")
    driver = webdriver.Chrome(service=service, options=options)
    driver.set_page_load_timeout(30)
    return driver


def save_html(driver, filename, description):
    """保存页面源码到文件"""
    filepath = f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/{filename}"
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(f"<!-- {description} -->\n")
        f.write(f"<!-- 采集时间: {datetime.now().isoformat()} -->\n")
        f.write(f"<!-- URL: {driver.current_url} -->\n\n\n")
        f.write(driver.page_source)
    print(f"[+] 已保存页面源码: {filepath}")
    return filepath


def analyze_login_page(driver):
    """分析登录页面结构"""
    print("\n" + "="*60)
    print("分析登录页面...")
    print("="*60)

    driver.get(LOGIN_URL)
    time.sleep(3)

    # 保存登录页面源码
    save_html(driver, "liangxin_login_page.html", "粮信网登录页面")

    # 分析表单结构
    print("\n[登录表单分析]")

    # 查找所有input元素
    inputs = driver.find_elements(By.CSS_SELECTOR, "input")
    print(f"  - 发现 {len(inputs)} 个 input 元素")
    for inp in inputs:
        name = inp.get_attribute("name")
        inp_type = inp.get_attribute("type")
        id_ = inp.get_attribute("id")
        placeholder = inp.get_attribute("placeholder")
        if name or id_:
            print(f"    * <input type='{inp_type}' name='{name}' id='{id_}' placeholder='{placeholder}'>")

    # 查找form元素
    forms = driver.find_elements(By.CSS_SELECTOR, "form")
    print(f"  - 发现 {len(forms)} 个 form 元素")
    for form in forms:
        action = form.get_attribute("action")
        method = form.get_attribute("method")
        print(f"    * form action='{action}' method='{method}'")

    # 查找按钮
    buttons = driver.find_elements(By.CSS_SELECTOR, "button, input[type='submit']")
    print(f"  - 发现 {len(buttons)} 个按钮")
    for btn in buttons:
        text = btn.text or btn.get_attribute("value") or ""
        btn_type = btn.get_attribute("type")
        print(f"    * button type='{btn_type}' text='{text}'")


def perform_login(driver):
    """执行登录"""
    print("\n" + "="*60)
    print("执行登录...")
    print("="*60)

    driver.get(LOGIN_URL)
    time.sleep(2)

    try:
        # 等待页面加载
        WebDriverWait(driver, 10).until(
            EC.presence_of_element_located((By.CSS_SELECTOR, "input[name*='user'], input[name*='name'], input[name*='username']"))
        )

        # 清除可能存在的自动填充
        driver.execute_script("""
            document.querySelectorAll('input').forEach(input => {
                input.setAttribute('autocomplete', 'off');
            });
        """)

        # 尝试多种选择器定位用户名和密码输入框
        username_selectors = [
            "input[name*='user']",
            "input[name*='name']",
            "input[name*='username']",
            "input[id*='user']",
            "input[id*='name']",
            "#username",
            "#user",
            "input[type='text']"
        ]

        password_selectors = [
            "input[name*='pass']",
            "input[type='password']",
            "#password",
            "#pass"
        ]

        username_input = None
        password_input = None

        for selector in username_selectors:
            try:
                username_input = driver.find_element(By.CSS_SELECTOR, selector)
                print(f"[+] 用户名输入框定位成功: {selector}")
                break
            except:
                continue

        for selector in password_selectors:
            try:
                password_input = driver.find_element(By.CSS_SELECTOR, selector)
                print(f"[+] 密码输入框定位成功: {selector}")
                break
            except:
                continue

        if not username_input or not password_input:
            print("[!] 无法定位用户名或密码输入框")
            # 保存页面以便手动分析
            save_html(driver, "liangxin_login_page_debug.html", "登录页面调试")
            return False

        # 输入账号密码
        username_input.clear()
        username_input.send_keys(USERNAME)
        print(f"[+] 已输入用户名: {USERNAME}")

        password_input.clear()
        password_input.send_keys(PASSWORD)
        print(f"[+] 已输入密码: {'*'*len(PASSWORD)}")

        # 查找并点击登录按钮
        submit_selectors = [
            "button[type='submit']",
            "input[type='submit']",
            "button",
            "#loginBtn",
            ".login-btn",
            "button[class*='login']"
        ]

        submit_btn = None
        for selector in submit_selectors:
            try:
                submit_btn = driver.find_element(By.CSS_SELECTOR, selector)
                print(f"[+] 登录按钮定位成功: {selector}")
                break
            except:
                continue

        if submit_btn:
            submit_btn.click()
            print("[+] 已点击登录按钮")
        else:
            # 尝试按Enter键提交
            password_input.send_keys("\n")
            print("[+] 已按Enter键提交")

        # 等待登录结果
        time.sleep(5)

        # 检查是否登录成功
        current_url = driver.current_url
        print(f"[*] 当前URL: {current_url}")

        if "login" not in current_url.lower():
            print("[+] 登录成功！")
            save_html(driver, "liangxin_after_login.html", "粮信网登录后页面")
            return True
        else:
            print("[!] 可能未跳转，保存当前页面分析")
            save_html(driver, "liangxin_login_result.html", "粮信网登录结果页面")
            return False

    except Exception as e:
        print(f"[!] 登录过程出错: {e}")
        save_html(driver, "liangxin_login_error.html", "粮信网登录错误页面")
        return False


def analyze_vip_report(driver):
    """分析VIP报告页面"""
    print("\n" + "="*60)
    print("分析VIP报告页面...")
    print("="*60)

    driver.get(VIP_REPORT_URL)
    time.sleep(5)

    # 保存页面源码
    save_html(driver, "liangxin_vip_report.html", "粮信网VIP报告页面")

    # 分析页面结构
    print("\n[VIP报告页面分析]")

    # 标题
    try:
        title = driver.title
        print(f"  - 页面标题: {title}")
    except:
        print("  - 无法获取页面标题")

    # 主要内容区
    content_selectors = [
        ".content",
        ".article-content",
        ".news-content",
        "#content",
        ".detail-content",
        "article",
        ".main-content"
    ]

    for selector in content_selectors:
        try:
            elem = driver.find_element(By.CSS_SELECTOR, selector)
            text_len = len(elem.text) if elem.text else 0
            print(f"  - 内容区域 ({selector}): 文本长度 {text_len}")
        except:
            pass

    # 报告正文
    try:
        # 尝试查找报告正文容器
        article = driver.find_element(By.CSS_SELECTOR, "article, .article, .news-detail, .report-content")
        print(f"\n[报告正文]")
        print(f"  - 元素标签: {article.tag_name}")
        print(f"  - 文本长度: {len(article.text) if article.text else 0}")
        print(f"  - 前200字预览:\n{article.text[:200] if article.text else '无文本内容'}...")
    except Exception as e:
        print(f"  - 无法定位报告正文: {e}")

    # 保存完整HTML以便后续分析
    html = driver.page_source

    # 尝试提取关键信息
    print("\n[关键信息提取]")

    # 标题
    title_elem = driver.find_elements(By.CSS_SELECTOR, "h1, h2, .title, .article-title, .news-title")
    for t in title_elem:
        if t.text:
            print(f"  - 标题: {t.text}")
            break

    # 时间
    time_elem = driver.find_elements(By.CSS_SELECTOR, ".time, .date, .publish-time, .info-time, time, [class*='time']")
    for t in time_elem:
        if t.text and ":" in t.text:
            print(f"  - 时间: {t.text}")
            break

    # 作者
    author_elem = driver.find_elements(By.CSS_SELECTOR, ".author, .editor, [class*='author']")
    for a in author_elem:
        if a.text:
            print(f"  - 作者/编辑: {a.text}")
            break


def analyze_report_list(driver):
    """分析报告列表页面"""
    print("\n" + "="*60)
    print("分析报告列表页面...")
    print("="*60)

    driver.get(REPORT_LIST_URL)
    time.sleep(5)

    # 保存页面源码
    save_html(driver, "liangxin_report_list.html", "粮信网报告列表页面")

    # 查找报告条目
    report_selectors = [
        ".report-list a",
        ".news-list a",
        ".list-item a",
        "a[href*='report']",
        ".article-list a"
    ]

    reports = []
    for selector in report_selectors:
        try:
            elems = driver.find_elements(By.CSS_SELECTOR, selector)
            if elems:
                print(f"  - 选择器 {selector} 找到 {len(elems)} 个链接")
                for e in elems[:5]:  # 只显示前5个
                    href = e.get_attribute("href")
                    text = e.text
                    if href and text:
                        reports.append({"href": href, "text": text})
        except:
            pass

    # 分析报告URL模式
    print("\n[报告URL模式分析]")
    vip_patterns = []
    public_patterns = []

    for r in reports[:20]:
        href = r.gethref
        if "my.chinagrain" in href or "jinnong" in href:
            vip_patterns.append(href)
        elif "chinagrain.cn/report" in href:
            public_patterns.append(href)

    if vip_patterns:
        print(f"  - VIP报告模式: {vip_patterns[0][:80]}...")
    if public_patterns:
        print(f"  - 公开报告模式: {public_patterns[0][:80]}...")


def main():
    """主函数"""
    print("="*60)
    print("粮信网技术分析脚本")
    print(f"账号: {USERNAME}")
    print("="*60)

    driver = None
    try:
        driver = create_driver()
        print("[+] Chrome浏览器启动成功")

        # 1. 分析登录页面
        analyze_login_page(driver)

        # 2. 执行登录
        if perform_login(driver):
            # 3. 分析VIP报告页面
            analyze_vip_report(driver)

            # 4. 分析报告列表
            analyze_report_list(driver)

        print("\n" + "="*60)
        print("分析完成！")
        print("="*60)
        print("\n已生成以下文件供分析:")
        print("  - liangxin_login_page.html (登录页面)")
        print("  - liangxin_after_login.html (登录后页面)")
        print("  - liangxin_vip_report.html (VIP报告页面)")
        print("  - liangxin_report_list.html (报告列表页面)")

        # 保持浏览器打开以便人工检查
        input("\n按Enter键退出...")

    except Exception as e:
        print(f"[!] 发生错误: {e}")
        import traceback
        traceback.print_exc()
    finally:
        if driver:
            driver.quit()
            print("[+] 浏览器已关闭")


if __name__ == "__main__":
    main()