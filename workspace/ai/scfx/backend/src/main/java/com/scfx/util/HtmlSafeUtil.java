package com.scfx.util;

import org.jsoup.Jsoup;
import org.jsoup.safety.Safelist;

/**
 * HTML 清洗工具类（XSS 防护）
 * 用于 contentHtml 入库前的白名单过滤
 */
public class HtmlSafeUtil {

    // 白名单：保留文档排版标签，禁止 script、onclick、iframe 等
    private static final Safelist KB_SAFE_LIST = Safelist.relaxed()
            .addTags("h1", "h2", "h3", "h4", "h5", "h6", "table", "tr", "td", "th")
            .addAttributes("img", "src")
            .removeAttributes(":all", "onclick", "onload", "onerror", "onmouseover",
                    "onfocus", "onblur", "onsubmit", "onchange",
                    "style", "class", "id");

    /**
     * 清洗 HTML，仅保留白名单内的标签和属性
     */
    public static String clean(String html) {
        if (html == null || html.isBlank()) return "";
        return Jsoup.clean(html, KB_SAFE_LIST);
    }
}
