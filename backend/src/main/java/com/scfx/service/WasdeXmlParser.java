package com.scfx.service;

import com.scfx.entity.WasdeData;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/**
 * WASDE/CONAB XML 解析器。
 * <p>
 * 使用 JDK DOM 解析器，支持 WASDE 和 CONAB 两种来源的 XML 结构。
 * 针对不同 sourceType 可扩展不同的 XPath/Node 映射规则。
 */
@Slf4j
@Component
public class WasdeXmlParser {

    /**
     * 解析 XML 输入流，提取结构化行数据。
     *
     * @param sourceType 数据来源（wasde / conab）
     * @param reportKey  报告标识
     * @param reportDate 报告日期
     * @param xmlStream  XML 输入流
     * @return 解析后的 WasdeData 列表
     */
    public List<WasdeData> parse(String sourceType, String reportKey,
                                  LocalDate reportDate, InputStream xmlStream) {
        try {
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            // 禁用外部实体加载，防止 XXE 攻击
            factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true);
            factory.setFeature("http://xml.org/sax/features/external-general-entities", false);
            factory.setFeature("http://xml.org/sax/features/external-parameter-entities", false);
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document doc = builder.parse(xmlStream);

            String normalizedType = sourceType.toLowerCase();

            if ("wasde".equals(normalizedType)) {
                return parseWasde(doc, sourceType, reportKey, reportDate);
            } else if ("conab".equals(normalizedType)) {
                return parseConab(doc, sourceType, reportKey, reportDate);
            } else {
                // 默认通用解析：尝试各节点路径
                List<WasdeData> result = parseGeneric(doc, sourceType, reportKey, reportDate);
                if (result.isEmpty()) {
                    log.warn("未找到可识别的数据行节点，sourceType={}, reportKey={}", sourceType, reportKey);
                }
                return result;
            }
        } catch (Exception e) {
            log.error("XML 解析失败, sourceType={}, reportKey={}", sourceType, reportKey, e);
            throw new RuntimeException("XML 解析失败: " + e.getMessage(), e);
        }
    }

    // ==================== WASDE 解析 ====================

    /**
     * 解析 WASDE 格式的 XML。
     * 支持常见的两种结构：
     * 1) &lt;DataLines&gt;&lt;Item&gt;...&lt;/Item&gt;&lt;/DataLines&gt;
     * 2) &lt;Data&gt;&lt;Row&gt;...&lt;/Row&gt;&lt;/Data&gt;
     */
    private List<WasdeData> parseWasde(Document doc, String sourceType,
                                        String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();

        // 尝试 DataLines/Item 路径
        NodeList items = doc.getElementsByTagName("Item");
        if (items.getLength() == 0) {
            items = doc.getElementsByTagName("Row");
        }
        if (items.getLength() == 0) {
            items = doc.getElementsByTagName("DataLine");
        }

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            WasdeData data = buildDataFromElement(item, sourceType, reportKey, reportDate);
            if (data != null) {
                list.add(data);
            }
        }

        // 如果按行节点未解析到数据，尝试从根节点寻找 Commodity 子标签
        if (list.isEmpty()) {
            list.addAll(parseFlatStructure(doc, sourceType, reportKey, reportDate));
        }

        log.debug("WASDE 解析完成, reportKey={}, 行数={}", reportKey, list.size());
        return list;
    }

    // ==================== CONAB 解析 ====================

    /**
     * 解析 CONAB 格式的 XML（巴西国家商品供应公司）。
     * 结构通常为 &lt;Dados&gt;&lt;Item&gt;...&lt;/Item&gt;&lt;/Dados&gt;
     */
    private List<WasdeData> parseConab(Document doc, String sourceType,
                                        String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();

        NodeList items = doc.getElementsByTagName("Item");
        if (items.getLength() == 0) {
            items = doc.getElementsByTagName("Produto");
        }

        for (int i = 0; i < items.getLength(); i++) {
            Element item = (Element) items.item(i);
            WasdeData data = buildDataFromElement(item, sourceType, reportKey, reportDate);
            if (data != null) {
                list.add(data);
            }
        }

        if (list.isEmpty()) {
            list.addAll(parseFlatStructure(doc, sourceType, reportKey, reportDate));
        }

        log.debug("CONAB 解析完成, reportKey={}, 行数={}", reportKey, list.size());
        return list;
    }

    // ==================== 通用解析 ====================

    /**
     * 通用解析：遍历所有一级子节点，尝试按标签名映射字段。
     */
    private List<WasdeData> parseGeneric(Document doc, String sourceType,
                                          String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();

        // 尝试多种可能的行容器节点
        String[] containerTags = {"Item", "Row", "DataLine", "Line", "Record", "Produto"};
        for (String tag : containerTags) {
            NodeList nodes = doc.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                for (int i = 0; i < nodes.getLength(); i++) {
                    WasdeData data = buildDataFromElement((Element) nodes.item(i),
                            sourceType, reportKey, reportDate);
                    if (data != null) {
                        list.add(data);
                    }
                }
                if (!list.isEmpty()) {
                    return list;
                }
            }
        }

        return list;
    }

    /**
     * 解析扁平结构：数据节点直接挂在根下，而非嵌套在行容器中。
     */
    private List<WasdeData> parseFlatStructure(Document doc, String sourceType,
                                                String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();
        NodeList commodities = doc.getElementsByTagName("Commodity");
        if (commodities.getLength() == 0) {
            commodities = doc.getElementsByTagName("Produto");
        }

        for (int i = 0; i < commodities.getLength(); i++) {
            Element commodityEl = (Element) commodities.item(i);
            // 尝试找上级行元素
            Element rowEl = (Element) commodityEl.getParentNode();
            WasdeData data = buildDataFromElement(rowEl, sourceType, reportKey, reportDate);
            if (data != null && data.getCommodity() == null) {
                data.setCommodity(commodityEl.getTextContent());
            }
            if (data != null) {
                list.add(data);
            }
        }

        return list;
    }

    // ==================== 字段提取 ====================

    /**
     * 从 XML 元素中提取字段并构建 WasdeData。
     */
    private WasdeData buildDataFromElement(Element element, String sourceType,
                                            String reportKey, LocalDate reportDate) {
        if (element == null) return null;

        WasdeData data = new WasdeData();
        data.setSourceType(sourceType);
        data.setReportKey(reportKey);
        data.setReportDate(reportDate);

        data.setCommodity(getElementText(element, "Commodity", "commodity", "Produto", "produto"));
        data.setCountry(getElementText(element, "Country", "country", "Pais", "pais"));
        data.setAttribute(getElementText(element, "Attribute", "attribute", "Atributo", "atributo"));
        data.setYearMarketing(getElementText(element, "YearMarketing", "year_marketing",
                "Safra", "safra", "Year", "year"));
        data.setUnit(getElementText(element, "Unit", "unit", "Unidade", "unidade"));

        String valueStr = getElementText(element, "Value", "value", "Valor", "valor");
        if (valueStr != null && !valueStr.isEmpty()) {
            try {
                data.setValue(new BigDecimal(valueStr.trim()));
            } catch (NumberFormatException e) {
                log.warn("数值解析失败, value={}, reportKey={}", valueStr, reportKey);
                data.setValue(null);
            }
        }

        // 校验必要字段
        if (data.getCommodity() == null && data.getAttribute() == null) {
            return null;
        }

        return data;
    }

    /**
     * 从元素中按多个候选标签名提取文本内容，返回第一个非空值。
     */
    private String getElementText(Element parent, String... candidateTags) {
        for (String tag : candidateTags) {
            NodeList nodes = parent.getElementsByTagName(tag);
            if (nodes.getLength() > 0) {
                String text = nodes.item(0).getTextContent();
                if (text != null && !text.trim().isEmpty()) {
                    return text.trim();
                }
            }
        }
        return null;
    }
}
