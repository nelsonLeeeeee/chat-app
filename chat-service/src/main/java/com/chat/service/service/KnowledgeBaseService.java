package com.chat.service.service;

import com.chat.common.entity.KnowledgeDocument;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface KnowledgeBaseService {

    KnowledgeDocument uploadPdf(MultipartFile file, Long uploaderId);

    List<KnowledgeDocument> listDocuments();

    void deleteDocument(Long documentId);
}
