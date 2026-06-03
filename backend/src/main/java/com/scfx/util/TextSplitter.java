package com.scfx.util;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

import java.util.ArrayList;
import java.util.List;

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
    @RequiredArgsConstructor
    public static class Chunk {
        private final String text;
        private final int index;
        private final int startOffset;
        private final int endOffset;
        private final boolean summary;  // true = 首切片，代表全文语义
        private final int estimatedTokens;

        public boolean isSummary() { return summary; }
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
     *
     * @param content 原始文本
     * @return 切片列表（&lt;500 字或空文本返回空列表）
     */
    public static List<Chunk> split(String content) {
        if (content == null || content.isEmpty()) return List.of();
        if (!shouldSplit(content)) return List.of();

        // 1. 按双换行切分段（结构化文本优先）
        List<String> paragraphs = splitByParagraph(content);

        // 2. 将段落合并/拆分为符合 chunkSize 的片段
        List<Chunk> chunks = buildChunks(paragraphs, content);

        // 3. 合并所有不足 minChunk 的碎片（尾片 + 中间碎片统一向前合并）
        chunks = mergeSmallChunks(chunks);

        // 4. 强制上限
        chunks = enforceMaxChunks(chunks);

        // 5. 重新计算 index 和 summary 标记
        for (int i = 0; i < chunks.size(); i++) {
            Chunk original = chunks.get(i);
            chunks.set(i, new Chunk(
                original.getText(),
                i,
                original.getStartOffset(),
                original.getEndOffset(),
                i == 0,
                original.getEstimatedTokens()
            ));
        }

        return chunks;
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
