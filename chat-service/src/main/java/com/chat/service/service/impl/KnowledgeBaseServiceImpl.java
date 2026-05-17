package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chat.common.entity.KnowledgeChunk;
import com.chat.common.entity.KnowledgeDocument;
import com.chat.service.mapper.KnowledgeChunkMapper;
import com.chat.service.mapper.KnowledgeDocumentMapper;
import com.chat.service.service.EmbeddingService;
import com.chat.service.service.KnowledgeBaseService;
import com.chat.service.service.MilvusVectorService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

/**
 * 知识库服务实现，负责PDF解析、文本分块与向量化存储（Milvus）
 */
@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final Logger log = LoggerFactory.getLogger(KnowledgeBaseServiceImpl.class);
    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    @Resource
    private KnowledgeDocumentMapper documentMapper;

    @Resource
    private KnowledgeChunkMapper chunkMapper;

    @Resource
    private EmbeddingService embeddingService;

    @Resource
    private MilvusVectorService milvusVectorService;

    /**
     * 解析PDF文本，分块后生成向量存入Milvus
     */
    @Override
    @Transactional
    public KnowledgeDocument uploadPdf(MultipartFile file, Long uploaderId) {
        String text;
        try (PDDocument pdf = PDDocument.load(file.getInputStream())) {
            PDFTextStripper stripper = new PDFTextStripper();
            text = stripper.getText(pdf);
        } catch (Exception e) {
            throw new RuntimeException("PDF文件解析失败: " + e.getMessage());
        }

        if (text == null || text.trim().length() < 10) {
            throw new RuntimeException("PDF文件中未检测到可提取的文字内容，可能为扫描件或图片型PDF");
        }

        List<String> chunks = chunkText(text);

        // 先插入文档获取ID
        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileSize(file.getSize());
        doc.setContent(text);
        doc.setChunkCount(chunks.size());
        doc.setUploaderId(uploaderId);
        documentMapper.insert(doc);

        // 批量生成向量并存入Milvus
        List<float[]> vectors;
        try {
            vectors = embeddingService.embedBatch(chunks);
        } catch (Exception e) {
            log.error("向量生成失败，降级使用零向量", e);
            vectors = new ArrayList<>();
            for (int i = 0; i < chunks.size(); i++) {
                vectors.add(new float[0]);
            }
        }
        try {
            milvusVectorService.insertVectors(doc.getId(), chunks, vectors);
        } catch (Exception e) {
            log.error("Milvus写入失败, documentId={}", doc.getId(), e);
        }

        int idx = 0;
        for (String chunk : chunks) {
            KnowledgeChunk kc = new KnowledgeChunk();
            kc.setDocumentId(doc.getId());
            kc.setChunkIndex(idx);
            kc.setContent(chunk);
            chunkMapper.insert(kc);
            idx++;
        }

        return doc;
    }

    /**
     * 按创建时间倒序列出所有文档
     */
    @Override
    public List<KnowledgeDocument> listDocuments() {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(KnowledgeDocument::getCreateTime);
        return documentMapper.selectList(wrapper);
    }

    /**
     * 级联删除文档、MySQL分块及Milvus向量
     */
    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        LambdaQueryWrapper<KnowledgeChunk> chunkWrapper = new LambdaQueryWrapper<>();
        chunkWrapper.eq(KnowledgeChunk::getDocumentId, documentId);
        chunkMapper.delete(chunkWrapper);
        documentMapper.deleteById(documentId);
        try {
            milvusVectorService.deleteByDocumentId(documentId);
        } catch (Exception e) {
            log.error("删除Milvus向量失败, documentId={}", documentId, e);
        }
    }

    /**
     * 按句子边界分块文本，块长500字符含50字符重叠
     */
    private List<String> chunkText(String text) {
        List<String> chunks = new ArrayList<>();
        int start = 0;
        while (start < text.length()) {
            int end = Math.min(start + CHUNK_SIZE, text.length());
            if (end >= text.length()) {
                chunks.add(text.substring(start).trim());
                break;
            }
            int breakPoint = -1;
            for (int i = end; i > start + CHUNK_SIZE / 2; i--) {
                char c = text.charAt(i);
                if (c == '。' || c == '！' || c == '？' || c == '\n'
                        || c == '.' || c == '!' || c == '?') {
                    breakPoint = i;
                    break;
                }
            }
            if (breakPoint > 0) {
                end = breakPoint + 1;
            }
            chunks.add(text.substring(start, end).trim());
            start = end - CHUNK_OVERLAP;
        }
        return chunks;
    }
}
