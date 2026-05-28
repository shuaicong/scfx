#!/usr/bin/env python3
"""
粮信网登录测试 - 使用Playwright
目标：分析登录机制和VIP报告页面结构
"""

import os
import sys
import time
from datetime import datetime

try:
    from playwright.sync_api import sync_playwright
except ImportError:
    print("错误：需要安装 playwright")
    print("安装命令：pip install playwright")
    print("然后执行：python3 -m playwright install chromium")
    sys.exit(1)

# 账号配置
USERNAME = "33022"
PASSWORD = "qlp707"

# 测试URL
LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
VIP_REPORT_URL = "https://my.chinagrain.cn/jinnong/liangyou_vipController.htm?newsid=5744881"
REPORT_LIST_URL = "https://my.chinagrain.cn/jinnong/liangyou_news.htm"


def save_html(content, filename, description):
    """保存页面源码到文件"""
    filepath = f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/{filename}"
    with open(filepath, 'w', encoding='utf-8') as f:
        f.write(f"<!-- {description} -->\n")
        f.write(f"<!-- 采集时间: {datetime.now().isoformat()} -->\n\n\n")
        f.write(content)
    print(f"[+] 已保存页面源码: {filepath}")
    return filepath


def analyze_login_page(page):
    """分析登录页面结构"""
    print("\n" + "="*60)
    print("分析登录页面...")
    print("="*60)

    page.goto(LOGIN_URL)
    page.wait_for_timeout(10000)
    time.sleep(2)

    # 保存登录页面源码
    save_html(page.content(), "liangxin_login_page.html", "粮信网登录页面")

    # 分析表单结构
    print("\n[登录表单分析]")

    # 查找所有input元素
    inputs = page.query_selector_all("input")
    print(f"  - 发现 {len(inputs)} 个 input 元素")
    for inp in inputs:
        name = inp.get_attribute("name")
        inp_type = inp.get_attribute("type")
        id_ = inp.get_attribute("id")
        placeholder = inp.get_attribute("placeholder")
        if name or id_:
            print(f"    * <input type='{inp_type}' name='{name}' id='{id_}' placeholder='{placeholder}'>")

    # 查找form元素
    forms = page.query_selector_all("form")
    print(f"  - 发现 {len(forms)} 个 form 元素")
    for form in forms:
        action = form.get_attribute("action")
        method = form.get_attribute("method")
        print(f"    * form action='{action}' method='{method}'")

    # 查找按钮
    buttons = page.query_selector_all("button")
    print(f"  - 发现 {len(buttons)} 个按钮")
    for btn in buttons:
        text = btn.text_content() or ""
        btn_type = btn.get_attribute("type")
        print(f"    * button type='{btn_type}' text='{text.strip()}'")

    # 截图保存
    page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_login_page.png")


def perform_login(page):
    """执行登录"""
    print("\n" + "="*60)
    print("执行登录...")
    print("="*60)

    page.goto(LOGIN_URL)
    page.wait_for_timeout(3000)

    try:
        # 等待用户名输入框出现
        username_input = page.wait_for_selector("input[name=username]", timeout=5000)
        password_input = page.wait_for_selector("input[name=password]", timeout=5000)

        print(f"[+] 用户名输入框定位成功")

        # 输入账号密码
        username_input.fill(USERNAME)
        print(f"[+] 已输入用户名: {USERNAME}")

        password_input.fill(PASSWORD)
        print(f"[+] 已输入密码: {'*'*len(PASSWORD)}")

        # 检查是否有验证码输入框
        validate_code_input = None
        try:
            validate_code_input = page.wait_for_selector("input[name=validateCode]", timeout=2000)
            print(f"[+] 发现验证码输入框")

            # 查找验证码图片
            validate_img = page.query_selector("img[src*=validateCode], img[src*=captcha], img[src*=code]")
            if validate_img:
                print(f"[+] 找到验证码图片")

            # 截图保存验证码
            page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_login_captcha.png")

            # 如果需要验证码但我们无法自动识别，跳过登录
            if validate_code_input:
                print("[!] 需要验证码，但无法自动识别，跳过登录")
                save_html(page.content(), "liangxin_login_needs_captcha.html", "粮信网需要验证码")
                return False
        except:
            print(f"[*] 未发现验证码输入框")

        # 点击登录按钮
        submit_btn = page.wait_for_selector("input[type=submit]", timeout=5000)
        submit_btn.click()
        print("[+] 已点击登录按钮")

        # 等待登录结果
        page.wait_for_timeout(5000)

        # 检查是否登录成功
        current_url = page.url
        print(f"[*] 当前URL: {current_url}")

        if "login" not in current_url.lower() and "a?login" not in current_url:
            print("[+] 登录成功！")
            save_html(page.content(), "liangxin_after_login.html", "粮信网登录后页面")
            page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_after_login.png")
            return True
        else:
            print("[!] 登录失败，跳转到登录页面")
            # 检查错误信息
            error_elements = page.query_selector_all(".red-warn")
            for elem in error_elements:
                if elem.is_visible():
                    text = elem.text_content()
                    if text and text.strip():
                        print(f"  错误提示: {text.strip()}")

            save_html(page.content(), "liangxin_login_result.html", "粮信网登录结果页面")
            page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_login_result.png")
            return False

    except Exception as e:
        print(f"[!] 登录过程出错: {e}")
        save_html(page.content(), "liangxin_login_error.html", "粮信网登录错误页面")
        page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_login_error.png")
        import traceback
        traceback.print_exc()
        return False


def analyze_vip_report(page):
    """分析VIP报告页面"""
    print("\n" + "="*60)
    print("分析VIP报告页面...")
    print("="*60)

    page.goto(VIP_REPORT_URL)
    page.wait_for_timeout(10000)
    time.sleep(5)

    # 保存页面源码
    save_html(page.content(), "liangxin_vip_report.html", "粮信网VIP报告页面")

    # 截图
    page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_vip_report.png")

    # 分析页面结构
    print("\n[VIP报告页面分析]")

    # 标题
    try:
        title = page.title()
        print(f"  - 页面标题: {title}")
    except:
        print("  - 无法获取页面标题")

    # 报告正文
    try:
        # 尝试查找报告正文容器
        article = page.query_selector("article, .article, .news-detail, .report-content, .content")
        if article:
            print(f"\n[报告正文]")
            print(f"  - 元素标签: {article.tag_name}")
            text = article.text_content()
            print(f"  - 文本长度: {len(text) if text else 0}")
            print(f"  - 前300字预览:\n{text[:300] if text else '无文本内容'}...")
        else:
            print("  - 无法定位报告正文容器")
    except Exception as e:
        print(f"  - 无法定位报告正文: {e}")

    # 关键信息提取
    print("\n[关键信息提取]")

    # 标题
    title_elem = page.query_selector("h1, h2, .title, .article-title, .news-title")
    if title_elem:
        text = title_elem.text_content()
        if text:
            print(f"  - 标题: {text.strip()}")

    # 时间
    time_elem = page.query_selector(".time, .date, .publish-time, .info-time, time")
    if time_elem:
        text = time_elem.text_content()
        if text and ":" in text:
            print(f"  - 时间: {text.strip()}")

    # 作者
    author_elem = page.query_selector(".author, .editor, [class*=author]")
    if author_elem:
        text = author_elem.text_content()
        if text:
            print(f"  - 作者/编辑: {text.strip()}")


def analyze_report_list(page):
    """分析报告列表页面"""
    print("\n" + "="*60)
    print("分析报告列表页面...")
    print("="*60)

    page.goto(REPORT_LIST_URL)
    page.wait_for_timeout(10000)
    time.sleep(5)

    # 保存页面源码
    save_html(page.content(), "liangxin_report_list.html", "粮信网报告列表页面")

    # 截图
    page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_report_list.png")

    # 查找报告条目
    reports = page.query_selector_all(".report-list a, .news-list a, .list-item a, a[href*=report], .article-list a")
    print(f"  - 发现 {len(reports)} 个报告链接")

    for i, r in enumerate(reports[:10]):
        href = r.get_attribute("href")
        text = r.text_content()
        if href:
            print(f"    {i+1}. {text[:50] if text else 'N/A'}... -> {href[:80]}...")


def main():
    """主函数"""
    print("="*60)
    print("粮信网技术分析脚本 (Playwright版)")
    print("="*60)

    with sync_playwright() as p:
        try:
            browser = p.chromium.launch(
                headless=False,  # 显示浏览器以便调试
                args=['--no-sandbox', '--disable-dev-shm-usage']
            )
            context = browser.new_context(
                viewport={'width': 1920, 'height': 1080},
                user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
            )
            page = context.new_page()

            print("[+] Chromium浏览器启动成功")

            # 步骤1：访问登录页面
            print("\n[步骤1] 访问登录页面...")
            page.goto(LOGIN_URL)
            page.wait_for_timeout(2000)

            # 步骤2：尝试登录
            print("\n[步骤2] 执行登录...")
            try:
                username_input = page.wait_for_selector("input[name=username]", timeout=5000)
                password_input = page.wait_for_selector("input[name=password]", timeout=5000)
                username_input.fill(USERNAME)
                password_input.fill(PASSWORD)
                page.wait_for_selector("input[type=submit]", timeout=5000).click()
                page.wait_for_timeout(3000)
                print(f"[*] 登录后URL: {page.url}")
            except Exception as e:
                print(f"[!] 登录过程出错: {e}")

            # 步骤3：访问VIP报告页面
            print("\n[步骤3] 访问VIP报告页面...")
            page.goto(VIP_REPORT_URL)
            page.wait_for_timeout(5000)

            # 保存页面源码
            save_html(page.content(), "liangxin_vip_report.html", "粮信网VIP报告页面")

            # 截图
            page.screenshot(path=f"/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_vip_report.png")

            print("\n[VIP报告页面分析]")
            title = page.title()
            print(f"  - 页面标题: {title}")

            # 查找正文内容
            article = page.query_selector(".article-conte-infor")
            if article:
                text = article.text_content()
                if text and text.strip():
                    print(f"  - 文章内容长度: {len(text)}")
                    print(f"  - 内容预览:\n{text[:800]}...")
                else:
                    print("  - article-conte-infor存在但内容为空")
            else:
                print("  - 未找到article-conte-infor元素")
                # 检查所有可见内容
                body = page.query_selector("body")
                if body:
                    text = body.text_content()
                    print(f"  - body内容前500字:\n{text[:500]}...")

            # 提取关键信息
            print("\n[关键信息提取]")
            title_elem = page.query_selector("h1, h2, .article-title")
            if title_elem:
                print(f"  - 标题: {title_elem.text_content().strip()}")

            # 保存cookie状态以便后续使用
            storage = context.storage_state()
            storage_path = "/Users/hucong/workspace/ai/scfx/docs/superpowers/specs/liangxin_storage_state.json"
            import json
            with open(storage_path, 'w') as f:
                json.dump(storage, f)
            print(f"\n[+] 已保存登录状态到: {storage_path}")

            print("\n" + "="*60)
            print("分析完成！")
            print("="*60)

            browser.close()

        except Exception as e:
            print(f"[!] 发生错误: {e}")
            import traceback
            traceback.print_exc()


if __name__ == "__main__":
    main()