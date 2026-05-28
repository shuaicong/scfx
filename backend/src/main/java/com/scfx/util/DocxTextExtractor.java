package com.scfx.util;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * .docx 纯文本提取工具（基于 ZIP + SAX，无需 Apache POI）。
 * docx 本质是 ZIP 压缩包，文本存放在 word/document.xml 的 {@code <w:t>} 元素中。
 *
 * 处理能力：
 * - 各版本 .docx（通过 OOXML 命名空间识别，不依赖 w: 前缀写法）
 * - 表格文本（表格中也是 w:t 元素）
 * - 大文档（SAX 流式解析 + 预分配 StringBuilder）
 * - 修订模式（跳过 w:del 中的删除文本，w:ins 正常提取）
 * - 特殊字符（HTML 实体 &amp; &lt; 等由 SAX 自动解码）
 * - 换行（w:br → \n）和制表符（w:tab → \t）
 */
public class DocxTextExtractor {

    private static final String WORD_NS = "http://schemas.openxmlformats.org/wordprocessingml/2006/main";

    public static String extract(String filePath) {
        try (ZipInputStream zis = new ZipInputStream(new FileInputStream(filePath))) {
            ZipEntry entry;
            while ((entry = zis.getNextEntry()) != null) {
                if ("word/document.xml".equals(entry.getName())) {
                    return parseDocument(zis);
                }
                zis.closeEntry();
            }
        } catch (Exception e) {
            throw new RuntimeException("提取 .docx 文本失败: " + filePath, e);
        }
        throw new RuntimeException(".docx 中未找到 word/document.xml: " + filePath);
    }

    private static String parseDocument(InputStream xml) throws Exception {
        SAXParserFactory factory = SAXParserFactory.newInstance();
        factory.setNamespaceAware(true);
        SAXParser parser = factory.newSAXParser();
        StringBuilder sb = new StringBuilder(65536);

        parser.parse(xml, new org.xml.sax.helpers.DefaultHandler() {
            private boolean inT = false;
            private boolean inDel = false;

            @Override
            public void startElement(String uri, String localName, String qName,
                                     org.xml.sax.Attributes attrs) {
                if (!WORD_NS.equals(uri)) return;

                if ("del".equals(localName)) {
                    inDel = true;
                    return;
                }
                if (inDel) return;

                if ("t".equals(localName)) {
                    inT = true;
                } else if ("br".equals(localName)) {
                    sb.append('\n');
                } else if ("tab".equals(localName)) {
                    sb.append('\t');
                }
            }

            @Override
            public void characters(char[] ch, int start, int length) {
                if (inT) {
                    sb.append(ch, start, length);
                }
            }

            @Override
            public void endElement(String uri, String localName, String qName) {
                if (!WORD_NS.equals(uri)) return;

                if ("del".equals(localName)) {
                    inDel = false;
                    return;
                }
                if (inDel) return;

                if ("t".equals(localName)) {
                    inT = false;
                } else if ("p".equals(localName)) {
                    sb.append('\n');
                }
            }
        });

        return cleanText(sb.toString());
    }

    /** 清洗：行尾空白 → 合并空行 → 首尾修剪 → 合并连续空格 */
    private static String cleanText(String text) {
        return text
            .replaceAll("[ \t]+\n", "\n")       // 行尾空白
            .replaceAll("\n{3,}", "\n\n")        // 连续空行最多两个
            .replaceAll("^[\n ]+", "")           // 开头空白
            .replaceAll("[\n ]+$", "")           // 结尾空白
            .replaceAll("[ \t]{2,}", " ");       // 连续空格合并为一个
    }
}
