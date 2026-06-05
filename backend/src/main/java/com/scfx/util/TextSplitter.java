package com.scfx.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * 中文文本分层分割器。
 * <p>
 * 分割优先级（语义优先，不破坏句子）：
 * 1. 按章节 / 标题 / 双换行（\n\n）分割 → 保住业务结构
 * 2. 超长段落按句子（。？！\n）截断 → 保住句子完整
 * 3. 单句超 chunkSize → 硬切（极小概率，仅发生在无标点长文本）
 * <p>
 * 合并规则：
 * - 任何切片 &lt; minChunk → 向前合并到上一个切片（尾片 + 中间碎片统一处理）
 * - 首切片永不向前合并（首片承担全文语义入口）
 * - 最大切片数 10，超限尾片强制合并
 * <p>
 * Overlap 边界约束：
 * - 普通切片 overlap=64 tokens，首切片减半为 32 tokens
 * - overlap 允许跨段落/章节（这是 overlap 的设计目的——保持上下文连续性）
 * - 跨章节 overlap 不会引入语义错误：章节衔接处本就是上下文相关的
 * <p>
 * Token 估算：1 token ≈ 1.5 中文字符（适配 BGE-M3 / XLM-RoBERTa）
 */
public class TextSplitter {

    /** 目标切片大小：512 tokens ≈ 768 字符 */
    public static final int CHUNK_SIZE_CHARS = 768;

    /** 普通切片重叠：64 tokens ≈ 96 字符 */
    public static final int OVERLAP_CHARS = 96;

    /** 首切片重叠：32 tokens ≈ 48 字符（首切片承担全局语义，减少冗余） */
    public static final int FIRST_OVERLAP_CHARS = 48;

    /** 最小切片：100 tokens ≈ 150 字符，不足则向前合并 */
    public static final int MIN_CHUNK_CHARS = 150;

    /** 触发切片的内容长度阈值：≥500 字符 */
    public static final int TRIGGER_THRESHOLD_CHARS = 500;

    /** 单文档最大切片数，超限强制合并尾片 */
    public static final int MAX_CHUNKS = 10;

    /**
     * 切片结果
     */
    @Getter
    public static class Chunk {
        private final String text;
        private final int index;
        private final int startOffset;
        private final int endOffset;
        private final boolean summary;  // true = 首切片，代表全文语义
        private final int estimatedTokens;
        private final String chunkType; // "text" | "table"

        public Chunk(String text, int index, int startOffset, int endOffset,
                     boolean summary, int estimatedTokens) {
            this(text, index, startOffset, endOffset, summary, estimatedTokens, "text");
        }

        public Chunk(String text, int index, int startOffset, int endOffset,
                     boolean summary, int estimatedTokens, String chunkType) {
            this.text = text;
            this.index = index;
            this.startOffset = startOffset;
            this.endOffset = endOffset;
            this.summary = summary;
            this.estimatedTokens = estimatedTokens;
            this.chunkType = chunkType;
        }

        public boolean isSummary() { return summary; }
    }

    /**
     * 文本段 / 表格段，用于 splitSegments 的中间结果。
     */
    @Getter
    @RequiredArgsConstructor
    public static class Segment {
        private final String type;    // "text" | "table"
        private final String content; // 段原文
        private final int startOffset;
        private final int endOffset;
    }

    /**
     * 判断是否需要对 content 执行切片。
     * 阈值 500±30 字符容错区间：470-530 范围按切片处理。
     */
    public static boolean shouldSplit(String content) {
        if (content == null) return false;
        int len = content.length();
        return len >= TRIGGER_THRESHOLD_CHARS - 30;
    }

    /**
     * 将内容分割为切片列表。
     * <p>
     * 优先检测表格标记（&lt;!--TABLE_MARKER_N--&gt;），表格作为原子块永不拆分。
     * 无标记时降级为纯文本分割（段落→句子→短语四级兜底）。
     *
     * @param content 原始文本
     * @return 切片列表（&lt;500 字或空文本返回空列表）
     */
    public static List<Chunk> split(String content) {
        if (content == null || content.isEmpty()) return List.of();
        if (!shouldSplit(content)) return List.of();

        // 1. 分离文本段和表格段
        List<Segment> segments = splitSegments(content);
        List<Chunk> allChunks = new ArrayList<>();

        // 2. 逐段处理
        int lastOffset = 0;
        for (Segment seg : segments) {
            if ("table".equals(seg.getType())) {
                // 表格作为原子块，永不拆分
                String rewritten = rewriteTable(seg.getContent());
                int estTokens = charsToTokens(rewritten.length());
                allChunks.add(new Chunk(
                    rewritten, 0,
                    seg.getStartOffset(), seg.getEndOffset(),
                    false, estTokens, "table"
                ));
            } else {
                // 文本段走现有分层分割逻辑
                allChunks.addAll(splitTextContent(seg.getContent(), content));
            }
            lastOffset = seg.getEndOffset();
        }

        // 3. 合并所有不足 minChunk 的碎片
        allChunks = mergeSmallChunks(allChunks);

        // 4. 强制上限
        allChunks = enforceMaxChunks(allChunks);

        // 5. 重新计算 index 和 summary 标记
        for (int i = 0; i < allChunks.size(); i++) {
            Chunk original = allChunks.get(i);
            allChunks.set(i, new Chunk(
                original.getText(), i,
                original.getStartOffset(), original.getEndOffset(),
                i == 0, original.getEstimatedTokens(),
                original.getChunkType()
            ));
        }

        return allChunks;
    }

    /**
     * 将 token 估算值转换为字符数。
     */
    public static int tokensToChars(int tokens) {
        return (int) (tokens * 1.5);
    }

    /**
     * 将字符数估算为 token 数。
     */
    public static int charsToTokens(int chars) {
        return (int) (chars / 1.5);
    }

    // ======================== 表格检测 & 语义改写 ========================

    /**
     * 分离文本段和表格段（表格为原子块，永不拆分）。
     * <p>
     * 优先通过 &lt;!--TABLE_MARKER_N--&gt; 标记识别表格（确定性匹配），
     * 无标记时降级为启发式 pipe 行检测（兼容旧数据）。
     *
     * @param content 原始文本
     * @return 段列表，每段为 "text" 或 "table"
     */
    public static List<Segment> splitSegments(String content) {
        if (content == null || content.isEmpty()) return List.of();
        if (content.contains("<!--TABLE_MARKER_")) {
            return splitByMarkers(content);
        }
        return splitByHeuristic(content);
    }

    /**
     * 通过 &lt;!--TABLE_MARKER_N--&gt; 标记精确识别表格边界。
     */
    private static List<Segment> splitByMarkers(String content) {
        List<Segment> segments = new ArrayList<>();
        StringBuilder currentText = new StringBuilder();
        int textStart = 0;
        int pos = 0;

        while (pos < content.length()) {
            int markerIdx = content.indexOf("<!--TABLE_MARKER_", pos);
            if (markerIdx < 0) {
                currentText.append(content.substring(pos));
                if (currentText.length() > 0) {
                    segments.add(new Segment("text", currentText.toString(), textStart, content.length()));
                }
                break;
            }

            // 标记前的文本
            if (markerIdx > pos) {
                currentText.append(content.substring(pos, markerIdx));
            }
            if (currentText.length() > 0) {
                segments.add(new Segment("text", currentText.toString(), textStart, markerIdx));
                currentText = new StringBuilder();
            }

            // 查找结束标记 <!--TABLE_MARKER_END_N-->
            int endIdx = content.indexOf("<!--TABLE_MARKER_END_", markerIdx);
            if (endIdx < 0) {
                // 未闭合标记，剩余内容作为表格
                segments.add(new Segment("table", content.substring(markerIdx), markerIdx, content.length()));
                break;
            }

            // 定位 --> 结尾
            int endMarkerEnd = content.indexOf("-->", endIdx);
            endMarkerEnd = (endMarkerEnd >= 0) ? endMarkerEnd + 3 : endIdx;

            segments.add(new Segment("table", content.substring(markerIdx, endMarkerEnd), markerIdx, endMarkerEnd));
            pos = endMarkerEnd;
            textStart = pos;
        }

        return segments;
    }

    /**
     * 启发式检测：扫描 pipe 行（|）连续模式识别表格（兼容旧数据无 marker）。
     * 连续 >=3 行 pipe 行视为表格，否则作为普通文本。
     */
    private static List<Segment> splitByHeuristic(String content) {
        List<Segment> segments = new ArrayList<>();
        String[] lines = content.split("\n", -1);
        StringBuilder currentText = new StringBuilder();
        int textStart = 0;
        int cursor = 0; // 当前字符偏移（按行走）

        for (int i = 0; i < lines.length; i++) {
            String line = lines[i];
            String trimmed = line.trim();
            int lineStart = findOffset(content, line, cursor);
            int lineEnd = lineStart + line.length();

            if (trimmed.startsWith("|") && trimmed.endsWith("|")) {
                // 提交之前的文本段
                if (currentText.length() > 0) {
                    int segStart = findOffset(content, currentText.toString().replaceAll("\n$", ""), textStart);
                    segments.add(new Segment("text",
                        currentText.toString().replaceAll("\n$", ""),
                        segStart, segStart + currentText.toString().replaceAll("\n$", "").length()));
                    currentText = new StringBuilder();
                }

                // 收集连续 pipe/空行
                StringBuilder pipeBuf = new StringBuilder();
                int pipeStart = lineStart;
                while (i < lines.length) {
                    String t = lines[i].trim();
                    if ((t.startsWith("|") && t.endsWith("|")) || t.isEmpty()) {
                        pipeBuf.append(lines[i]).append("\n");
                        cursor = findOffset(content, lines[i], cursor) + lines[i].length() + 1;
                        i++;
                    } else {
                        break;
                    }
                }
                // 调整过度 i++
                i--;
                String pipeStr = pipeBuf.toString().replaceAll("\n+$", "");
                String[] pipeLines = pipeStr.split("\n");

                if (pipeLines.length >= 3) {
                    segments.add(new Segment("table", pipeStr, pipeStart, pipeStart + pipeStr.length()));
                } else {
                    currentText.append(pipeStr);
                    textStart = pipeStart;
                }
            } else {
                if (currentText.length() == 0) textStart = lineStart;
                currentText.append(line).append("\n");
                cursor = lineEnd + 1; // +1 for \n
            }
        }

        // 最后一段文本
        if (currentText.length() > 0) {
            String text = currentText.toString().replaceAll("\n$", "");
            segments.add(new Segment("text", text, textStart, textStart + text.length()));
        }

        return segments;
    }

    /**
     * 将 pipe 表格改写为语义化自然语言描述（与 Python semantic_rewrite_table 逻辑一致）。
     * <p>
     * &le;20 行：全量展开，前缀【表格数据】
     * &gt;20 行：前 15 行详细 + 数值列统计摘要
     *
     * @param tableText pipe 表格文本（含 | 行）
     * @return 语义化改写后的自然语言文本
     */
    public static String rewriteTable(String tableText) {
        // 过滤出 pipe 行
        String[] lines = tableText.split("\n");
        List<String> pipeLines = new ArrayList<>();
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.startsWith("|")) {
                pipeLines.add(trimmed);
            }
        }
        if (pipeLines.size() < 3) return tableText;

        // 解析表头
        String headerLine = pipeLines.get(0);
        String[] headers = headerLine.substring(1, headerLine.length() - 1).split("\\|");
        for (int i = 0; i < headers.length; i++) {
            headers[i] = headers[i].trim();
        }

        // 解析数据行（跳过分隔行 index=1）
        List<String> parts = new ArrayList<>();
        for (int i = 2; i < pipeLines.size(); i++) {
            String line = pipeLines.get(i);
            String[] cells = line.substring(1, line.length() - 1).split("\\|");
            StringBuilder row = new StringBuilder();
            for (int j = 0; j < Math.min(cells.length, headers.length); j++) {
                String cell = cells[j].trim();
                if (!cell.isEmpty()) {
                    if (row.length() > 0) row.append("；");
                    row.append(headers[j]).append("：").append(cell);
                }
            }
            if (row.length() > 0) parts.add(row.toString());
        }

        if (parts.isEmpty()) return tableText;

        int MAX_FULL_ROWS = 20;
        if (parts.size() <= MAX_FULL_ROWS) {
            return "【表格数据】" + String.join("；", parts);
        }

        // 超大表格：前 15 行详细 + 统计摘要
        String detail = String.join("；", parts.subList(0, Math.min(15, parts.size())));

        // 数值列统计
        StringBuilder stats = new StringBuilder();
        for (int colIdx = 0; colIdx < headers.length; colIdx++) {
            List<String> colVals = new ArrayList<>();
            for (int i = 2; i < pipeLines.size(); i++) {
                String line = pipeLines.get(i);
                String[] cells = line.substring(1, line.length() - 1).split("\\|");
                if (cells.length > colIdx && !cells[colIdx].trim().isEmpty()) {
                    colVals.add(cells[colIdx].trim());
                }
            }
            if (colVals.size() < 2) continue;

            List<Double> allNums = new ArrayList<>();
            for (String v : colVals) {
                allNums.addAll(parseNums(v));
            }
            if (allNums.size() >= 4) {
                double min = Collections.min(allNums);
                double max = Collections.max(allNums);
                if (stats.length() > 0) stats.append("；");
                stats.append(headers[colIdx]).append("：")
                     .append(formatNum(min)).append("~").append(formatNum(max));
            }
        }

        return "【表格数据/共" + parts.size() + "行】" + detail + "；...\n【统计摘要】" + stats;
    }

    /**
     * 解析单元格中的数值，支持整数、小数、逗号分隔数字。
     */
    private static List<Double> parseNums(String val) {
        List<Double> nums = new ArrayList<>();
        Matcher m = Pattern.compile("\\d+\\.?\\d*").matcher(val.replace(",", ""));
        while (m.find()) {
            nums.add(Double.parseDouble(m.group()));
        }
        return nums;
    }

    /**
     * 数值格式化：整数无小数，小数保留两位。
     */
    private static String formatNum(double val) {
        if (val == Math.floor(val) && !Double.isInfinite(val)) {
            return String.valueOf((long) val);
        }
        return String.format("%.2f", val);
    }

    // ======================== 文本分段逻辑 ========================

    /**
     * 对纯文本段（不含表格）执行分层分割。
     * 复用现有的段落→句子→短语四级分割逻辑。
     */
    private static List<Chunk> splitTextContent(String text, String fullContent) {
        List<String> paragraphs = splitByParagraph(text);
        return buildChunks(paragraphs, fullContent);
    }

    // ======================== 私有方法 ========================

    /**
     * 按双换行分割段落。
     * 如果没有多段则退化为整段（不走 \\n 拆分）。
     */
    private static List<String> splitByParagraph(String content) {
        List<String> paragraphs = new ArrayList<>();
        String[] parts = content.split("\\n\\s*\\n|\\r\\n\\s*\\r\\n");
        for (String part : parts) {
            part = part.trim();
            if (!part.isEmpty()) {
                paragraphs.add(part);
            }
        }
        if (paragraphs.size() <= 1) {
            paragraphs.clear();
            paragraphs.add(content.trim());
        }
        return paragraphs;
    }

    /**
     * 根据段落列表构建符合 chunkSize 的切片。
     * 段落完整性优先：尽量整段合并，仅超长段落执行句级切割。
     */
    private static List<Chunk> buildChunks(List<String> paragraphs, String fullContent) {
        List<Chunk> result = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int currentStart = -1;

        for (String para : paragraphs) {
            if (current.length() == 0) {
                currentStart = findOffset(fullContent, para, currentStart + 1);
            }

            // 当前缓冲区 + 新段落超限 → 先提交当前缓冲区
            if (current.length() + para.length() > CHUNK_SIZE_CHARS && current.length() > 0) {
                flushBuffer(current, currentStart, result);
                // 保留 overlap
                currentStart = applyOverlap(current, result);
            }

            if (para.length() > CHUNK_SIZE_CHARS) {
                // 超长段落：先提交缓冲，再逐句拆分
                if (current.length() > 0) {
                    flushBuffer(current, currentStart, result);
                    current = new StringBuilder();
                }
                splitLongParagraph(para, fullContent, result);
                currentStart = findOffset(fullContent, para, 0);
            } else {
                current.append(para);
            }
        }

        // 提交缓冲区剩余
        if (current.length() > 0) {
            flushBuffer(current, currentStart, result);
        }

        return result;
    }

    /**
     * 将当前缓冲区提交为一个切片。
     */
    private static void flushBuffer(StringBuilder buf, int bufStart, List<Chunk> result) {
        if (buf.length() == 0) return;
        boolean isFirst = result.isEmpty();
        int estTokens = charsToTokens(buf.length());
        result.add(new Chunk(
            buf.toString(),
            result.size(),
            bufStart,
            bufStart + buf.length(),
            isFirst,
            estTokens
        ));
    }

    /**
     * 保留 overlap 文本，更新起始偏移。
     * @return 新的起始偏移
     */
    private static int applyOverlap(StringBuilder buf, List<Chunk> result) {
        if (result.isEmpty()) return 0;
        Chunk last = result.get(result.size() - 1);
        boolean isFirst = result.size() == 1;
        int overlap = isFirst ? FIRST_OVERLAP_CHARS : OVERLAP_CHARS;
        int overlapLen = Math.min(overlap, buf.length());
        String overlapText = buf.substring(buf.length() - overlapLen);
        buf.setLength(0);
        buf.append(overlapText);
        return last.getEndOffset() - overlapLen;
    }

    /**
     * 将超长段落按句子切分为 chunkSize 大小的片段。
     * <p>
     * 句子分割符：句号(。) 问号(？) 感叹号(！) 换行(\n)
     * 降级兜底：单句仍超 chunkSize → 在句内按空格/逗号再切
     */
    private static void splitLongParagraph(String para, String fullContent, List<Chunk> result) {
        // 按句子分割
        String[] sentences = para.split("(?<=[。？！\n])\\s*");
        StringBuilder buf = new StringBuilder();
        int bufStart = sentences.length > 0 ? findOffset(fullContent, sentences[0], 0) : 0;

        for (String sentence : sentences) {
            // 单句超 chunkSize（无标点长文本兜底）
            if (sentence.length() >= CHUNK_SIZE_CHARS) {
                // 先提交当前缓冲区
                if (buf.length() > 0) {
                    flushBuffer(buf, bufStart, result);
                    bufStart = applyOverlap(buf, result);
                }
                // 按空格/逗号再切分单句
                splitOversizedSentence(sentence, fullContent, result);
                bufStart = findOffset(fullContent, sentence, bufStart);
                continue;
            }

            // 正常句子：追加到缓冲区
            if (buf.length() + sentence.length() > CHUNK_SIZE_CHARS && buf.length() > 0) {
                flushBuffer(buf, bufStart, result);
                bufStart = applyOverlap(buf, result);
            }
            // 首次追加到空缓冲区时重新计算偏移
            if (buf.length() == 0) {
                bufStart = findOffset(fullContent, sentence, bufStart);
            }
            buf.append(sentence);
        }

        if (buf.length() > 0) {
            flushBuffer(buf, bufStart, result);
        }
    }

    /**
     * 单句超 chunkSize（无句号/问号/感叹号的长文本），按逗号/空格/分号二次切割。
     */
    private static void splitOversizedSentence(String sentence, String fullContent, List<Chunk> result) {
        // 按英文逗号、中文逗号、空格、分号、冒号分割
        String[] parts = sentence.split("(?<=[，,;；:\\s])\\s*");
        StringBuilder buf = new StringBuilder();
        int bufStart = findOffset(fullContent, parts[0], 0);

        for (String part : parts) {
            if (buf.length() + part.length() > CHUNK_SIZE_CHARS && buf.length() > 0) {
                flushBuffer(buf, bufStart, result);
                bufStart = applyOverlap(buf, result);
            }
            if (buf.length() == 0) {
                bufStart = findOffset(fullContent, part, bufStart);
            }
            buf.append(part);
        }
        if (buf.length() > 0) {
            flushBuffer(buf, bufStart, result);
        }
    }

    /**
     * 合并所有不足 minChunk 的切片。
     * <p>
     * 规则：
     * - 尾片不足 → 向前合并到上一个切片
     * - 中间碎片不足 → 向前吸收到上一个切片
     * - 首切片永不合并（保持全文语义入口完整）
     * - 多轮合并防止碎片堆积：从后向前扫描，直到所有切片（除首片外）均达标
     */
    private static List<Chunk> mergeSmallChunks(List<Chunk> chunks) {
        if (chunks.size() < 2) return chunks;

        List<Chunk> result = new ArrayList<>(chunks);
        boolean merged;
        do {
            merged = false;
            // 从后向前扫描，避免索引偏移
            for (int i = result.size() - 1; i > 0; i--) {
                Chunk current = result.get(i);
                if (current.getText().length() < MIN_CHUNK_CHARS) {
                    // 向前合并到 result[i-1]
                    Chunk prev = result.get(i - 1);
                    String mergedText = prev.getText() + current.getText();
                    result.set(i - 1, new Chunk(
                        mergedText,
                        prev.getIndex(),
                        prev.getStartOffset(),
                        current.getEndOffset(),
                        prev.isSummary(),
                        charsToTokens(mergedText.length())
                    ));
                    result.remove(i);
                    merged = true;
                    break;  // 重新扫描（索引已变）
                }
            }
        } while (merged);

        return result;
    }

    /**
     * 强制上限：超过 MAX_CHUNKS 片时，合并剩余内容到第 MAX_CHUNKS 片。
     * 保留前 MAX_CHUNKS-1 片不变，剩余全部合并为最后一笔。
     */
    private static List<Chunk> enforceMaxChunks(List<Chunk> chunks) {
        if (chunks.size() <= MAX_CHUNKS) return chunks;

        List<Chunk> result = new ArrayList<>(chunks.subList(0, MAX_CHUNKS - 1));

        StringBuilder merged = new StringBuilder();
        int mergedStart = chunks.get(MAX_CHUNKS - 1).getStartOffset();
        for (int i = MAX_CHUNKS - 1; i < chunks.size(); i++) {
            merged.append(chunks.get(i).getText());
        }
        int mergedEnd = chunks.get(chunks.size() - 1).getEndOffset();

        result.add(new Chunk(
            merged.toString(),
            result.size(),
            mergedStart,
            mergedEnd,
            false,
            charsToTokens(merged.length())
        ));
        return result;
    }

    /**
     * 在原文中查找子串的起始偏移位置。
     * 从 fromIndex 开始搜索，避免重复匹配相同内容。
     */
    private static int findOffset(String fullContent, String sub, int fromIndex) {
        int idx = fullContent.indexOf(sub, Math.max(0, fromIndex));
        return idx >= 0 ? idx : Math.min(fromIndex, fullContent.length());
    }
}
