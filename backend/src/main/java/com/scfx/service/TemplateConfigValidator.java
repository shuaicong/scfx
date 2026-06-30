package com.scfx.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Slf4j
@Component
public class TemplateConfigValidator {

    private static final Pattern PLACEHOLDER_PATTERN = Pattern.compile("\\{\\{([A-Z_]+):?([^}]*)\\}\\}");
    private static final ObjectMapper objectMapper = new ObjectMapper();

    public List<String> validate(String html, String configJson) {
        List<String> warnings = new ArrayList<>();
        if (html == null || configJson == null) {
            return warnings;
        }

        // 提取 HTML 中的占位符
        List<String> htmlPlaceholders = new ArrayList<>();
        Matcher m = PLACEHOLDER_PATTERN.matcher(html);
        while (m.find()) {
            htmlPlaceholders.add(m.group(1));
        }

        // 校验配置规则
        try {
            JsonNode config = objectMapper.readTree(configJson);
            if (config.has("price_data")) {
                JsonNode tables = config.get("price_data").get("tables");
                if (tables != null && tables.isArray() && !htmlPlaceholders.contains("PRICE_TABLE")) {
                    warnings.add("配置定义了 PRICE_TABLE 但模板中无 {{PRICE_TABLE}} 占位符");
                }
            }
            if (config.has("knowledge_search") && !htmlPlaceholders.contains("KNOWLEDGE_SUMMARY")) {
                warnings.add("配置定义了 knowledge_search 但模板中无 {{KNOWLEDGE_SUMMARY}} 占位符");
            }
        } catch (Exception e) {
            log.warn("解析 generation_config JSON 失败: {}", e.getMessage());
        }

        if (!warnings.isEmpty()) {
            log.warn("模板配置不匹配: {}", warnings);
        }
        return warnings;
    }
}
