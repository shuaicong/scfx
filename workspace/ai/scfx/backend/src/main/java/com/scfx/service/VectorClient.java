package com.scfx.service;

/**
 * 向量库客户端接口
 * 支持多种实现：OpenAI、MILVUS、Qdrant 等
 */
public interface VectorClient {

    /**
     * 向量嵌入结果
     */
    class VectorResult {
        private String vectorId;
        private float[] vector;

        public VectorResult() {}
        public VectorResult(String vectorId) { this.vectorId = vectorId; }
        public VectorResult(String vectorId, float[] vector) {
            this.vectorId = vectorId;
            this.vector = vector;
        }

        public String getVectorId() { return vectorId; }
        public void setVectorId(String vectorId) { this.vectorId = vectorId; }
        public float[] getVector() { return vector; }
        public void setVector(float[] vector) { this.vector = vector; }
    }

    /**
     * 将文本转换为向量
     * @param text 文本内容
     * @return 向量结果，包含 ID 和向量数据
     */
    VectorResult embed(String text);

    /**
     * 向量化文本（含HTML内容，可能包含图片）
     * @param text 纯文本内容
     * @param contentHtml HTML格式内容（可能含img标签）
     */
    default VectorResult embed(String text, String contentHtml) {
        return embed(text);
    }

    /**
     * 向量化文本（含HTML内容），指定知识库ID
     */
    default VectorResult embed(String text, String contentHtml, Long knowledgeId) {
        return embed(text, contentHtml);
    }

    /**
     * 将文本批量转换为向量
     * @param texts 文本列表
     * @return 向量结果列表
     */
    default java.util.List<VectorResult> embedBatch(java.util.List<String> texts) {
        return texts.stream().map(this::embed).collect(java.util.stream.Collectors.toList());
    }

    /**
     * 为可视化生成向量，与 embed() 不同的模型/维度
     * 例如 embed() 用 BGE-M3 1024d 做检索，embedVisualization() 用 DashScope 768d 做可视化
     */
    default VectorResult embedVisualization(String text, String contentHtml, Long knowledgeId) {
        return embed(text, contentHtml, knowledgeId);
    }
}