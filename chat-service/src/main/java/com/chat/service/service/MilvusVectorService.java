package com.chat.service.service;

import java.util.List;

/**
 * Milvus 向量存储与语义检索服务
 */
public interface MilvusVectorService {

    /**
     * 创建/加载Collection（应用启动时调用）
     */
    void initCollection();

    /**
     * 批量插入分块向量
     */
    void insertVectors(Long documentId, List<String> chunks, List<float[]> vectors);

    /**
     * 向量相似度搜索，返回最匹配的分块内容
     */
    List<SearchResult> search(float[] queryVector, int topK);

    /**
     * 按文档ID删除所有向量
     */
    void deleteByDocumentId(Long documentId);

    /**
     * 搜索结果封装
     */
    class SearchResult {
        private String content;
        private double score;
        public SearchResult(String content, double score) {
            this.content = content; this.score = score;
        }
        public String getContent() { return content; }
        public double getScore() { return score; }
    }
}
