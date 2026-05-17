package com.chat.service.service;

import java.util.List;

/**
 * 向量嵌入服务，调用 DeepSeek Embedding API
 */
public interface EmbeddingService {

    /**
     * 单文本转向量
     */
    float[] embed(String text);

    /**
     * 批量文本转向量
     */
    List<float[]> embedBatch(List<String> texts);

    /**
     * 余弦相似度计算
     */
    double cosineSimilarity(float[] a, float[] b);
}
