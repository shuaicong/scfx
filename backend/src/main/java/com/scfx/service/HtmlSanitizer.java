package com.scfx.service;

import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

@Component
public class HtmlSanitizer {

    // 移除 data-* 属性
    private static final Pattern DATA_ATTR = Pattern.compile("\\sdata-[\\w-]+=\"[^\"]*\"");
    // 移除 ProseMirror 专用 class
    private static final Pattern PM_CLASS = Pattern.compile("\\sclass=\"(ProseMirror[^\"]*|has-focus)\"");
    // 移除空段落
    private static final Pattern EMPTY_P = Pattern.compile("<p>\\s*</p>");
    // 移除空 span
    private static final Pattern EMPTY_SPAN = Pattern.compile("<span[^>]*>\\s*</span>");

    public String sanitize(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }

        String cleaned = html;
        cleaned = DATA_ATTR.matcher(cleaned).replaceAll("");
        cleaned = PM_CLASS.matcher(cleaned).replaceAll("");
        cleaned = EMPTY_P.matcher(cleaned).replaceAll("");
        cleaned = EMPTY_SPAN.matcher(cleaned).replaceAll("");

        return cleaned;
    }
}
