"""粮信网采集器"""
import logging
import os
import time
from datetime import datetime
from typing import List, Dict, Optional

from playwright.sync_api import sync_playwright, Page

from collectorsdk.config import ReporterConfig
from collectorsdk.base import BaseCollector
from collectorsdk.dimensions import Source, Subject, CollectType, CollectObject

logger = logging.getLogger(__name__)


class LiangxinCollector(BaseCollector):
    """粮信网采集器

    用于采集粮信网玉米晨报

    使用方式：
        config = ReporterConfig(api_base="http://localhost:8080/api")
        collector = LiangxinCollector(
            config=config,
            task_id=1,
            username="xxx",
            password="xxx",
        )
        result = collector.run()  # 自动管理生命周期
    """

    LOGIN_URL = "https://my.chinagrain.cn/jinnong/a/login"
    REPORT_LIST_URL = "https://my.chinagrain.cn/jinnong/liangyou_news.htm"
    REPORT_DETAIL_URL = "https://my.chinagrain.cn/jinnong/liangyou_vipController.htm"

    def __init__(
        self,
        config: ReporterConfig,
        task_id: int,
        username: str,
        password: str,
        report_type: str = "morning",  # morning / evening
        execution_id: str = None,
        target_date: Optional[str] = None,
    ):
        """初始化粮信网采集器

        Args:
            config: 上报配置
            task_id: 任务ID
            username: 粮信网账号
            password: 粮信网密码
            report_type: 报告类型 morning=晨报 evening=日报
            target_date: 目标采集日期 (yyyy-MM-dd)，不传则默认今天
        """
        super().__init__(
            config=config,
            task_id=task_id,
            execution_id=execution_id,
            source=Source.LIANGXIN.value,
            subject=Subject.CORN.value,
            coll_type=CollectType.LOGIN_CRAWL.value,
            obj=CollectObject.DAILY_REPORT.value,
            remark=f"粮信网玉米{'晨报' if report_type == 'morning' else '日报'}采集",
        )
        self.username = username
        self.password = password
        self.report_type = report_type
        self.target_date = target_date
        self._page: Optional[Page] = None
        self._logged_in = False

    def _create_browser(self):
        """创建浏览器，优先复用已保存的登录状态"""
        self.playwright = sync_playwright().start()
        self.browser = self.playwright.chromium.launch(
            headless=True,
            args=['--no-sandbox', '--disable-dev-shm-usage']
        )
        # 尝试复用已保存的登录状态
        storage_path = "/tmp/liangxin_auth.json"
        if os.path.exists(storage_path):
            logger.info("发现已保存的登录状态，尝试复用")
            self.context = self.browser.new_context(
                viewport={'width': 1920, 'height': 1080},
                user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36',
                storage_state=storage_path,
            )
        else:
            self.context = self.browser.new_context(
                viewport={'width': 1920, 'height': 1080},
                user_agent='Mozilla/5.0 (Macintosh; Intel Mac OS X 10_15_7) AppleWebKit/537.36'
            )
        self._page = self.context.new_page()

    def _close_browser(self):
        """关闭浏览器"""
        if self.browser:
            self.browser.close()
        if self.playwright:
            self.playwright.stop()

    def _login(self) -> bool:
        """执行登录

        Returns:
            True=登录成功，False=登录失败
        """
        try:
            self._page.goto(self.LOGIN_URL)
            self._page.wait_for_timeout(3000)
            logger.info(f"登录页已加载: {self._page.url}")

            # 检查是否已登录（有有效 session 时登录页会重定向到后台首页）
            body_text = self._page.text_content("body") or ""
            if "您好" in body_text and "退出" in body_text:
                logger.info("检测到已有有效登录 session，跳过登录流程")
                self._logged_in = True
                return True

            # 填入账号密码
            self._page.fill("input[name=username]", self.username)
            self._page.fill("input[name=password]", self.password)

            # 勾选记住我（如果有）
            remember = self._page.query_selector("input[name=rememberMe]")
            if remember:
                self._page.evaluate(
                    'document.querySelector("input[name=rememberMe]").checked = true'
                )

            # 点击登录按钮
            self._page.click("input[type=submit]")
            self._page.wait_for_timeout(5000)
            logger.info(f"登录提交后URL: {self._page.url}")

            # Jeesite 登录成功后会重定向到 /a?login（后台首页）
            # 该页面会显示"您好, XXX"和"退出"链接，直接在此页面验证
            body_text = self._page.text_content("body") or ""
            logged_in_markers = ["您好", "退出"]
            found_marker = any(m in body_text for m in logged_in_markers)

            if found_marker:
                logger.info(f"登录验证成功")
                self._logged_in = True
                # 保存登录状态供后续复用
                try:
                    self.context.storage_state(path="/tmp/liangxin_auth.json")
                    logger.info("登录状态已保存")
                except Exception as e:
                    logger.warning(f"保存登录状态失败: {e}")
                return True
            else:
                # 如果 Jeesite 后台页面也没有标记，可能是登录失败停留在登录页
                logger.warning(f"登录验证失败，未检测到登录标记，URL: {self._page.url}")
                return False

        except Exception as e:
            logger.error(f"登录异常: {e}")
            return False

    def _get_report_list(self, date_str: str) -> List[Dict]:
        """获取指定日期的报告列表

        Args:
            date_str: 日期字符串，格式 YYYY-MM-DD

        Returns:
            报告列表 [{title, url, publish_time}]
        """
        reports = []

        # 构造筛选URL：按品种筛选（玉米1061）
        url = f"{self.REPORT_LIST_URL}?type=7008&producttype=1061&key="
        logger.info(f"正在获取报告列表: {url}")
        self._page.goto(url, wait_until="load", timeout=30000)
        self._page.wait_for_timeout(3000)
        logger.info(f"报告列表页加载完成，标题: {self._page.title()}")

        # 查找今日发布的报告
        # 日期格式可能有多种：(2026年5月14日) 或 (2026-05-14) 或 2026-05-14
        try:
            # 解析目标日期为年、月、日
            from datetime import datetime
            target_date = datetime.strptime(date_str, "%Y-%m-%d")
            target_year = str(target_date.year)
            target_month = str(target_date.month)
            target_day = str(target_date.day)

            # 遍历页面上的报告链接
            links = self._page.query_selector_all("a[href*='liangyou_vipController']")

            for link in links:
                href = link.get_attribute("href")
                text = link.text_content() or ""
                text_stripped = text.strip()

                # 判断是否是指定日期的报告
                # 匹配格式：(2026年5月14日) 或 (2026-05-14)
                date_formats = [
                    f"{target_year}年{target_month}月{target_day}日",  # 2026年5月14日
                    f"{target_year}-{target_month.zfill(2)}-{target_day.zfill(2)}",  # 2026-05-14
                ]

                for date_format in date_formats:
                    if date_format in text_stripped:
                        # 按报告类型过滤：morning → 晨报, evening → 日报
                        type_keyword = "晨报" if self.report_type == "morning" else "日报"
                        if type_keyword not in text_stripped:
                            logger.debug(f"跳过非{type_keyword}: {text_stripped}")
                            continue
                        # 确保 URL 使用 HTTPS（登录 cookie 可能带 Secure 标志）
                        if href.startswith("http://"):
                            href = "https://" + href[7:]
                        elif href.startswith("//"):
                            href = "https:" + href

                        # 提取真实发布时间：列表每个条目有 <span class="items-left-space">时间：YYYY-MM-DD HH:mm:ss</span>
                        publish_time = f"{date_str}T09:00:00"  # 默认兜底
                        try:
                            time_text = link.evaluate("""(el) => {
                                const li = el.closest('li');
                                if (!li) return '';
                                const span = li.querySelector('span.items-left-space');
                                return span ? span.textContent.trim() : '';
                            }""")
                            import re
                            time_match = re.search(r'时间：(\d{4}-\d{2}-\d{2}\s+\d{2}:\d{2}:\d{2})', time_text)
                            if time_match:
                                publish_time = time_match.group(1).replace(' ', 'T')
                                logger.info(f"提取到真实发布时间: {publish_time}")
                        except Exception as e:
                            logger.warning(f"提取发布时间失败: {e}")

                        reports.append({
                            "title": text_stripped,
                            "url": href,
                            "publish_time": publish_time,
                        })
                        logger.info(f"找到报告: {text_stripped}")
                        break

        except Exception as e:
            logger.error(f"解析报告列表异常: {e}")

        return reports

    def _get_report_content(self, url: str) -> Optional[Dict]:
        """获取报告正文

        Args:
            url: 报告详情页URL

        Returns:
            包含 text 和 html 的字典，失败返回None
        """
        try:
            # 确保使用 HTTPS（登录 cookie 可能带 Secure 标志，HTTP 下不会发送）
            if url.startswith("http://"):
                url = "https://" + url[7:]
                logger.info(f"已将 URL 转换为 HTTPS: {url}")

            logger.info(f"正在获取报告内容: {url}")
            self._page.goto(url, wait_until="load", timeout=30000)
            self._page.wait_for_timeout(3000)
            logger.info(f"页面加载完成，标题: {self._page.title()}, URL: {self._page.url}")

            # 检查是否弹出 VIP 登录遮罩（页面内嵌的登录表单，非导航栏"会员"文字）
            need_login = self._page.evaluate("""() => {
                const overlay = document.querySelector('.login-windows-vip');
                if (!overlay) return false;
                const style = window.getComputedStyle(overlay);
                return style.display !== 'none' && style.visibility !== 'hidden' && overlay.offsetParent !== null;
            }""")
            if need_login:
                logger.warning(f"详情页弹出VIP登录遮罩，登录后重试: {url}")
                try:
                    os.remove("/tmp/liangxin_auth.json")
                except Exception:
                    pass
                # 从详情页内嵌的登录表单直接登录（该表单提交后应刷新页面）
                self._page.fill(".login-windows-vip input[name=username]", self.username)
                self._page.fill(".login-windows-vip input[name=password]", self.password)
                self._page.click(".login-windows-vip input[type=submit]")
                self._page.wait_for_timeout(5000)
                # 保存新状态并重试
                try:
                    self.context.storage_state(path="/tmp/liangxin_auth.json")
                except Exception:
                    pass
                self._page.goto(url, wait_until="load", timeout=30000)
                self._page.wait_for_timeout(3000)
                # 再次检查
                still_locked = self._page.evaluate("""() => {
                    const overlay = document.querySelector('.login-windows-vip');
                    if (!overlay) return false;
                    const style = window.getComputedStyle(overlay);
                    return style.display !== 'none' && style.visibility !== 'hidden' && overlay.offsetParent !== null;
                }""")
                if still_locked:
                    logger.error(f"详情页登录后VIP遮罩仍未消失: {url}")
                    return None

            # 等待页面稳定（部分网站有延迟渲染）
            self._page.wait_for_timeout(3000)

            # 优先级1：尝试精确选择器匹配（常见新闻/报告页面的正文容器）
            selectors = [
                # 粮信网特定容器
                ".container.clearf",
                # 通用内容容器
                ".article-conte-infor",
                ".article-content",
                ".article-context",
                ".article-detail",
                ".article-text",
                ".article-body",
                ".news-detail",
                ".news-content",
                ".news-text",
                ".detail-content",
                ".detail-text",
                ".report-content",
                ".main-content",
                ".main-text",
                ".body-content",
                ".text-content",
                ".content-body",
                ".info-content",
                ".content",
                # 通用ID
                "#content",
                "#article",
                "#main",
                "#detail",
                "#news_content",
                "#main-content",
                # 语义标签
                "article",
                "main",
            ]

            content_elem = None
            used_selector = None
            for selector in selectors:
                try:
                    self._page.wait_for_selector(selector, state="visible", timeout=3000)
                    elem = self._page.query_selector(selector)
                    if elem:
                        text = elem.text_content() or ""
                        if text.strip():
                            content_elem = elem
                            used_selector = selector
                            break
                except Exception:
                    continue

            if content_elem:
                # 在浏览器中克隆节点并移除干扰元素，提取干净内容
                result = content_elem.evaluate("""(el) => {
                    const clone = el.cloneNode(true);
                    // 移除脚本、样式、iframe 等
                    clone.querySelectorAll('script, style, iframe, nav').forEach(n => n.remove());
                    // 移除广告和侧边栏
                    clone.querySelectorAll('.list-right-side, .sidebar, .right, .comments, .statement, #closeLeftad, [class*=ad-], [class*=advertisement]').forEach(n => n.remove());
                    // 移除 VIP 提示信息（已登录用户不需要）
                    clone.querySelectorAll('.list-left-side-n').forEach(n => {
                        const id = n.id || '';
                        if (id !== 'status3') n.remove();
                    });
                    // 移除面包屑导航
                    clone.querySelectorAll('.current-path').forEach(n => n.remove());
                    // 移除页脚
                    clone.querySelectorAll('.footer, .footer-box-top, .header').forEach(n => n.remove());
                    // 移除隐藏元素
                    clone.querySelectorAll('[style*="display:none"], [style*="display: none"], [style*="visibility:hidden"], [style*="visibility: hidden"]').forEach(n => n.remove());

                    // 提取表格
                    const tableMeta = [];
                    try {
                        const tables = clone.querySelectorAll('table');
                        let markerIndex = 0;
                        tables.forEach(table => {
                            // 跳过嵌套表格
                            let parent = table.parentElement;
                            let isNested = false;
                            while (parent && parent !== clone) {
                                if (parent.tagName === 'TABLE') {
                                    isNested = true;
                                    break;
                                }
                                parent = parent.parentElement;
                            }
                            if (isNested) return;

                            const rows = table.querySelectorAll('tr');
                            if (rows.length === 0) return;

                            // 解析行: 展开 colspan
                            const parseRow = (row) => {
                                const cells = row.querySelectorAll('th, td');
                                const result = [];
                                cells.forEach(cell => {
                                    const colspan = parseInt(cell.getAttribute('colspan')) || 1;
                                    const text = cell.textContent.trim();
                                    for (let j = 0; j < colspan; j++) {
                                        result.push(text);
                                    }
                                });
                                return result;
                            };

                            // 检测表头行: th 过半的首个行
                            let headerRow = null;
                            let headerRowIndex = -1;
                            for (let i = 0; i < rows.length; i++) {
                                const cells = rows[i].querySelectorAll('th, td');
                                let thCount = 0;
                                cells.forEach(c => { if (c.tagName === 'TH') thCount++; });
                                if (thCount > cells.length / 2) {
                                    headerRow = rows[i];
                                    headerRowIndex = i;
                                    break;
                                }
                            }

                            // 提取表头
                            let headers = [];
                            if (headerRow) {
                                headers = parseRow(headerRow);
                            } else {
                                headers = parseRow(rows[0]);
                            }

                            // 提取数据行
                            const startIdx = headerRowIndex >= 0 ? headerRowIndex + 1 : 1;
                            const dataRows = [];
                            for (let i = startIdx; i < rows.length; i++) {
                                dataRows.push(parseRow(rows[i]));
                            }

                            // 获取 caption（前一个同级别元素，非 TABLE/FIGURE/DIV）
                            let caption = '';
                            const prev = table.previousElementSibling;
                            if (prev && !['TABLE', 'FIGURE', 'DIV'].includes(prev.tagName)) {
                                caption = prev.textContent.trim();
                            }

                            tableMeta.push({
                                caption: caption,
                                headers: headers,
                                rows: dataRows,
                            });

                            // 构建 pipe 表格文本
                            const lines = [];
                            lines.push('<!--TABLE_MARKER_' + markerIndex + '-->');
                            lines.push('| ' + headers.join(' | ') + ' |');
                            lines.push('| ' + headers.map(function() { return '---'; }).join(' | ') + ' |');
                            dataRows.forEach(function(row) {
                                lines.push('| ' + row.join(' | ') + ' |');
                            });
                            lines.push('<!--TABLE_MARKER_END_' + markerIndex + '-->');
                            const pipeText = lines.join('\\n');

                            // 用 marker + pipe 文本替换该 table
                            const wrapper = document.createElement('div');
                            wrapper.textContent = pipeText;
                            table.parentNode.replaceChild(wrapper, table);
                            markerIndex++;
                        });
                    } catch(e) {
                        tableMeta = [];
                    }

                    // 提取文本和HTML
                    return {
                        text: clone.textContent.trim(),
                        html: clone.innerHTML.trim(),
                        tableMeta: tableMeta
                    };
                }""")
                stripped = (result.get("text") or "").strip() if result else ""
                if stripped:
                    logger.info(f"正文提取成功，长度 {len(stripped)} 字，选择器: {used_selector}")
                    # 清理HTML冗余空白 + 垃圾文本
                    import re
                    stripped = re.sub(r'[\t\r]+', '', stripped)
                    stripped = re.sub(r'^[\s\u3000]*\u5206\u4eab\u5230[\u3000\uff1a:].*$', '', stripped, flags=re.MULTILINE)
                    stripped = re.sub(r'&nbsp;', '', stripped)
                    stripped = '\n'.join(l.strip() for l in stripped.split('\n'))
                    stripped = re.sub(r'\n{3,}', '\n\n', stripped)
                    return {
                        "text": stripped,
                        "html": (result.get("html") or "").strip() if result else "",
                        "tableMeta": result.get("tableMeta", []) if result else []
                    }
                else:
                    logger.warning(f"正文容器存在但内容为空: {url}")

            # 优先级2：兜底策略——提取页面上所有 <p> 标签的文本（适用于大多数新闻页面）
            logger.info("精确选择器均未命中，尝试 <p> 标签兜底提取")
            try:
                p_elements = self._page.query_selector_all("p")
                paragraphs = []
                for p in p_elements:
                    text = p.text_content() or ""
                    stripped = text.strip()
                    # 过滤掉短文本（导航、广告等噪音）
                    if len(stripped) > 15:
                        paragraphs.append(stripped)

                if paragraphs:
                    combined = "\n\n".join(paragraphs)
                    logger.info(f"<p> 标签兜底提取成功，共 {len(paragraphs)} 段，{len(combined)} 字")
                    return {
                        "text": combined,
                        "html": combined.replace("\n\n", "</p><p>"),
                        "tableMeta": [],
                    }
                else:
                    logger.warning(f"<p> 标签兜底也未提取到有效内容: {url}")
            except Exception as e:
                logger.warning(f"<p> 标签兜底异常: {e}")

            # 优先级3：最终兜底——获取整个 body 文本
            logger.info("尝试整个 body 文本提取")
            try:
                body = self._page.query_selector("body")
                if body:
                    body_text = body.text_content() or ""
                    stripped = body_text.strip()
                    # 去掉常见的非内容区域（通过关键词粗略过滤）
                    import re
                    # 只保留超过一定长度的连续文本块
                    lines = [l.strip() for l in stripped.split("\n") if l.strip()]
                    meaningful = [l for l in lines if len(l) > 20]
                    if meaningful:
                        combined = "\n".join(meaningful)
                        logger.info(f"body 兜底提取成功，{len(meaningful)} 行，{len(combined)} 字")
                        return {
                            "text": combined,
                            "html": combined.replace("\n", "<br>"),
                        }
            except Exception as e:
                logger.warning(f"body 兜底异常: {e}")

            logger.warning(f"所有提取策略均失败: {url}")
            return None

        except Exception as e:
            logger.error(f"获取报告内容异常: {e}")
            return None

    def collect(self) -> int:
        """执行采集

        Returns:
            采集数量
        """
        count = 0
        # 目标日期：优先使用传入的 target_date，否则取今天
        if self.target_date and self.target_date.strip():
            run_date = self.target_date.strip()
        else:
            run_date = datetime.now().strftime("%Y-%m-%d")
        # 兜底强解析校验
        try:
            datetime.strptime(run_date, "%Y-%m-%d")
        except ValueError:
            logger.error(f"manual_execute_collect_date 目标日期格式非法: {run_date}，终止采集")
            raise ValueError(f"目标日期格式非法: {run_date}")

        logger.info(f"manual_execute_collect_date 粮信玉米日报采集目标日期：{run_date}, executionId={self._execution_id}")
        today = run_date

        try:
            # 创建浏览器并登录
            self._create_browser()
            t0 = time.time()
            if not self._login():
                raise Exception("登录失败")
            login_ms = int((time.time() - t0) * 1000)
            self.set_phase_time("login", login_ms)
            self.log_info(f"登录完成，耗时 {login_ms / 1000:.1f}s", phase="login", category="checkpoint")

            # 获取报告列表
            t1 = time.time()
            reports_meta = self._get_report_list(today)
            crawl_ms = int((time.time() - t1) * 1000)
            self.set_phase_time("crawl", crawl_ms)
            self.log_info(f"今日找到 {len(reports_meta)} 篇报告", phase="crawl", category="metric")

            t2 = time.time()
            # 收集所有报告数据
            for report in reports_meta:
                self.log_info(f"采集报告: {report['title']}", phase="crawl", category="data")

                content = self._get_report_content(report["url"])
                if content:
                    import json

                    table_meta_raw = content.get("tableMeta", [])
                    if not isinstance(table_meta_raw, list):
                        table_meta_raw = []

                    # 校验：TABLE_MARKER 数量必须与 tableMeta 长度一致
                    if table_meta_raw:
                        marker_count = content["text"].count("<!--TABLE_MARKER_END_")
                        if marker_count != len(table_meta_raw):
                            logger.warning(
                                f"table_meta 校验失败: markers={marker_count} != "
                                f"entries={len(table_meta_raw)}, 已清空降级"
                            )
                            table_meta_raw = []

                    self.submit_report(
                        title=report["title"],
                        source="liangxin",
                        url=report["url"],
                        variety="玉米",
                        report_type="晨报" if self.report_type == "morning" else "日报",
                        content=content["text"],
                        content_html=content["html"],
                        publish_time=report["publish_time"],
                        table_meta=json.dumps(table_meta_raw, ensure_ascii=False),
                    )
                    count += 1
                    self._success_count += 1
                else:
                    self.log_warn(f"报告内容为空: {report['title']}", phase="crawl", category="error")
                    self._error_count += 1

            parse_ms = int((time.time() - t2) * 1000)
            self.set_phase_time("parse", parse_ms)
            self._skip_count = len(reports_meta) - count - self._error_count

            self.log_info(f"采集完成，共 {count} 篇报告，耗时 {parse_ms / 1000:.1f}s", phase="report", category="checkpoint")

        except Exception as e:
            self.log_error(f"采集异常: {e}")
            raise
        finally:
            self._close_browser()

        return count