package com.chat.service.service;

import com.chat.common.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * 知识库服务接口，定义PDF文档上传与管理契约
 */
public interface KnowledgeBaseService {

    /**
     * 上传PDF文档并自动分块入库
     */
    KnowledgeDocument uploadPdf(MultipartFile file, Long uploaderId);

    /**
     * 列出所有知识库文档
     */
    List<KnowledgeDocument> listDocuments();

    /**
     * 删除文档及其所有分块
     */
    void deleteDocument(Long documentId);
}
