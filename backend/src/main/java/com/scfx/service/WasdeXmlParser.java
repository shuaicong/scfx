package com.scfx.service;

import com.scfx.entity.WasdeData;
import com.scfx.enums.CommodityEnum;
import com.scfx.enums.AttributeEnum;
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

        // 如果按行节点未解析到数据，尝试 sr08 子报告格式
        if (list.isEmpty()) {
            list.addAll(parseWasdeSr08(doc, sourceType, reportKey, reportDate));
        }

        // 如果 sr08 也未解析到，尝试扁平结构
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
     * 解析 WASDE XML 的 sr08/sr09 子报告格式（Crystal Reports 输出）。
     *
     * WASDE XML 结构示例:
     * <m1_commodity_group commodity1="CORN">
     *   <m1_year_group market_year1="2024/25">
     *     <m1_attribute_group>
     *       <s3 attribute1="Output">
     *         <s4><Cell cell_value1="15115" /></s4>
     *       </s3>
     *     </m1_attribute_group>
     *   </m1_year_group>
     * </m1_commodity_group>
     */
    private List<WasdeData> parseWasdeSr08(Document doc, String sourceType,
                                            String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();

        // 1) 解析 sr08 m1_commodity_group 格式（全球总量）
        list.addAll(parseM1CommodityGroup(doc, sourceType, reportKey, reportDate));

        // 2) 解析 table1/commodity_group 格式（美豆/玉米等具体品种）
        list.addAll(parseTableCommodityGroup(doc, sourceType, reportKey, reportDate));

        log.info("WASDE sr08 解析完成, reportKey={}, 行数={}", reportKey, list.size());
        return list;
    }

    /** 解析 sr08 m1_commodity_group 格式（全球总量） */
    private List<WasdeData> parseM1CommodityGroup(Document doc, String sourceType,
                                                    String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();
        NodeList commodityGroups = doc.getElementsByTagName("m1_commodity_group");
        for (int c = 0; c < commodityGroups.getLength(); c++) {
            Element commodityEl = (Element) commodityGroups.item(c);
            String commodityName = commodityEl.getAttribute("commodity1");
            if (commodityName == null || commodityName.isEmpty()) continue;
            if (!isCoreCommodity(commodityName)) continue;

            // 遍历市场年（直接搜索当前 commodity 内的 m1_year_group）
            NodeList yearGroups = commodityEl.getElementsByTagName("m1_year_group");
            for (int y = 0; y < yearGroups.getLength(); y++) {
                Element yearEl = (Element) yearGroups.item(y);
                String marketYear = yearEl.getAttribute("market_year1");

                // 遍历属性
                NodeList attrGroups = yearEl.getElementsByTagName("m1_attribute_group");
                for (int a = 0; a < attrGroups.getLength(); a++) {
                    Element attrGroup = (Element) attrGroups.item(a);

                    // 提取 attribute1 值
                    NodeList s3List = attrGroup.getElementsByTagName("s3");
                    if (s3List.getLength() == 0) continue;
                    Element s3 = (Element) s3List.item(0);
                    String attrName = s3.getAttribute("attribute1");
                    if (attrName == null || attrName.isEmpty()) continue;

                    // 提取 cell_value1
                    NodeList cellList = s3.getElementsByTagName("Cell");
                    if (cellList.getLength() == 0) continue;
                    Element cell = (Element) cellList.item(0);
                    String valueStr = cell.getAttribute("cell_value1");
                    if (valueStr == null || valueStr.trim().isEmpty()) continue;

                    // 映射指标
                    String mappedAttr = mapWasdeAttribute(attrName);
                    if (mappedAttr == null) continue;

                    WasdeData data = new WasdeData();
                    data.setReportKey(reportKey);
                    data.setSourceType(sourceType);
                    data.setCommodity(normalizeCommodity(commodityName));
                    data.setAttribute(mappedAttr);
                    try {
                        data.setValue(new BigDecimal(valueStr.trim()));
                    } catch (NumberFormatException e) {
                        log.warn("数值格式异常: commodity={}, attr={}, value='{}'",
                                commodityName, attrName, valueStr);
                        continue;
                    }
                    data.setReportDate(reportDate);
                    list.add(data);
                }
            }
        }

        log.info("M1 解析完成, reportKey={}, 行数={}", reportKey, list.size());
        return list;
    }

    /** 解析 table1/commodity_group 格式（具体品种统计） */
    private List<WasdeData> parseTableCommodityGroup(Document doc, String sourceType,
                                                      String reportKey, LocalDate reportDate) {
        List<WasdeData> list = new ArrayList<>();
        NodeList commodityGroups = doc.getElementsByTagName("commodity_group");
        for (int c = 0; c < commodityGroups.getLength(); c++) {
            Element commodityEl = (Element) commodityGroups.item(c);
            String commodityName = commodityEl.getAttribute("commodity1");
            if (commodityName == null || commodityName.isEmpty()) continue;
            if (!isCoreCommodity(commodityName)) continue;

            NodeList attrGroups = commodityEl.getElementsByTagName("attribute_group");
            for (int a = 0; a < attrGroups.getLength(); a++) {
                Element attrEl = (Element) attrGroups.item(a);
                String attrName = attrEl.getAttribute("attribute");
                if (attrName == null || attrName.isEmpty()) continue;

                // 映射指标
                String mappedAttr = mapWasdeAttribute(attrName);
                if (mappedAttr == null) continue;

                // 提取 cell_value 或 root_mean_square_error1
                String valueStr = attrEl.getAttribute("cell_value");
                if (valueStr == null || valueStr.isEmpty()) {
                    valueStr = attrEl.getAttribute("root_mean_square_error1");
                }
                if (valueStr == null || valueStr.trim().isEmpty()) continue;

                WasdeData data = new WasdeData();
                data.setReportKey(reportKey);
                data.setSourceType(sourceType);
                data.setCommodity(normalizeCommodity(commodityName));
                data.setAttribute(mappedAttr);
                try {
                    data.setValue(new BigDecimal(valueStr.trim()));
                } catch (NumberFormatException e) {
                    log.warn("数值格式异常: commodity={}, attr={}, value='{}'",
                            commodityName, attrName, valueStr);
                    continue;
                }
                data.setReportDate(reportDate);
                list.add(data);
            }
        }
        log.info("Table 解析完成, reportKey={}, 行数={}", reportKey, list.size());
        return list;
    }

    /** 是否核心品种 */
    private boolean isCoreCommodity(String name) {
        String upper = name.toUpperCase().trim();
        return "CORN".equals(upper)
            || "WHEAT".equals(upper)
            || "SOYBEANS".equals(upper)
            || "RICE".equals(upper)
            || "COARSE GRAINS 5/".equals(upper);
    }

    /** 归一化品种名 */
    private String normalizeCommodity(String raw) {
        String upper = raw.toUpperCase().trim();
        if ("CORN".equals(upper)) return CommodityEnum.CORN.getCode();
        if ("WHEAT".equals(upper)) return CommodityEnum.WHEAT.getCode();
        if ("SOYBEANS".equals(upper)) return CommodityEnum.SOYBEANS.getCode();
        if ("RICE, MILLED".equals(upper)) return CommodityEnum.RICE.getCode();
        if ("COARSE GRAINS 5/".equals(upper)) return "COARSE_GRAINS";
        return upper;
    }

    /** 映射 WASDE 属性名到标准指标枚举 */
    private String mapWasdeAttribute(String raw) {
        String trimmed = raw.trim().toUpperCase()
            .replaceAll("\\s+", " ")
            .replaceAll("\\d+/", "").trim();
        if (trimmed.contains("OUTPUT") || trimmed.contains("PRODUCTION")) {
            return AttributeEnum.PRODUCTION.getCode();
        }
        if (trimmed.contains("IMPORT")) {
            return AttributeEnum.IMPORTS.getCode();
        }
        if (trimmed.contains("EXPORT") || trimmed.contains("TRADE")) {
            return AttributeEnum.EXPORTS.getCode();
        }
        if (trimmed.contains("ENDING STOCK") || trimmed.contains("ENDING STOCKS")) {
            return AttributeEnum.ENDING_STOCK.getCode();
        }
        return null;
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
