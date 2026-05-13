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
     * 将文本批量转换为向量
     * @param texts 文本列表
     * @return 向量结果列表
     */
    default java.util.List<VectorResult> embedBatch(java.util.List<String> texts) {
        return texts.stream().map(this::embed).collect(java.util.stream.Collectors.toList());
    }
}