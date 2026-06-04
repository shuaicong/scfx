package com.scfx.service;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 文档内容安全过滤器 — 有效文本判断 + 敏感词过滤
 */
@Slf4j
@Component
public class ContentFilter {

    /** 最小有效字符数：低于此值视为无效文档 */
    private static final int MIN_MEANINGFUL_CHARS = 50;

    /** 敏感词正则列表，从配置加载 */
    private final List<Pattern> sensitivePatterns;

    public ContentFilter(@Value("${app.content-filter.sensitive-words:}") String words) {
        this.sensitivePatterns = words.isBlank() ? List.of()
            : Arrays.stream(words.split(","))
                .map(w -> Pattern.compile("(?i)" + Pattern.quote(w.trim())))
                .collect(Collectors.toList());
    }

    /**
     * 检查内容是否合规。
     * @return null = 通过，非 null = 违规原因
     */
    public String check(String content) {
        if (content == null || content.isBlank()) return null;

        // 1. 有效文本长度判断
        long meaningful = content.chars()
            .filter(c -> Character.isLetter(c) || Character.isDigit(c))
            .count();
        if (meaningful < MIN_MEANINGFUL_CHARS) {
            return "文档无可解析的有效文本内容（有效字符 < " + MIN_MEANINGFUL_CHARS + " 字）";
        }

        // 2. 敏感词匹配
        for (Pattern p : sensitivePatterns) {
            if (p.matcher(content).find()) {
                log.warn("文档包含违规内容，已拦截 | 匹配模式={}", p.pattern());
                return "文档包含违规内容，已拦截";
            }
        }

        return null; // 通过
    }

    /**
     * 统计有效字符数
     */
    public static long countMeaningfulChars(String text) {
        if (text == null) return 0;
        return text.chars()
            .filter(c -> Character.isLetter(c) || Character.isDigit(c))
            .count();
    }
}
