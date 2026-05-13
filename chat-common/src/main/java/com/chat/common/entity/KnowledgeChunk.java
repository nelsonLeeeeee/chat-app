package com.chat.common.entity;

import java.time.LocalDateTime;

/**
 * 知识库分块实体
 */
public class KnowledgeChunk {

    private Long id;               // 主键ID
    private Long documentId;       // 所属文档ID
    private Integer chunkIndex;    // 分块序号
    private String content;        // 分块内容
    private LocalDateTime createTime;  // 创建时间

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getDocumentId() { return documentId; }
    public void setDocumentId(Long documentId) { this.documentId = documentId; }
    public Integer getChunkIndex() { return chunkIndex; }
    public void setChunkIndex(Integer chunkIndex) { this.chunkIndex = chunkIndex; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
