package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chat.common.entity.KnowledgeChunk;
import com.chat.common.entity.KnowledgeDocument;
import com.chat.service.mapper.KnowledgeChunkMapper;
import com.chat.service.mapper.KnowledgeDocumentMapper;
import com.chat.service.service.KnowledgeBaseService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.List;

@Service
public class KnowledgeBaseServiceImpl implements KnowledgeBaseService {

    private static final int CHUNK_SIZE = 500;
    private static final int CHUNK_OVERLAP = 50;

    @Resource
    private KnowledgeDocumentMapper documentMapper;

    @Resource
    private KnowledgeChunkMapper chunkMapper;

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

        KnowledgeDocument doc = new KnowledgeDocument();
        doc.setFileName(file.getOriginalFilename());
        doc.setFileSize(file.getSize());
        doc.setContent(text);
        doc.setChunkCount(chunks.size());
        doc.setUploaderId(uploaderId);
        documentMapper.insert(doc);

        int idx = 0;
        for (String chunk : chunks) {
            KnowledgeChunk kc = new KnowledgeChunk();
            kc.setDocumentId(doc.getId());
            kc.setChunkIndex(idx++);
            kc.setContent(chunk);
            chunkMapper.insert(kc);
        }

        return doc;
    }

    @Override
    public List<KnowledgeDocument> listDocuments() {
        LambdaQueryWrapper<KnowledgeDocument> wrapper = new LambdaQueryWrapper<>();
        wrapper.orderByDesc(KnowledgeDocument::getCreateTime);
        return documentMapper.selectList(wrapper);
    }

    @Override
    @Transactional
    public void deleteDocument(Long documentId) {
        LambdaQueryWrapper<KnowledgeChunk> chunkWrapper = new LambdaQueryWrapper<>();
        chunkWrapper.eq(KnowledgeChunk::getDocumentId, documentId);
        chunkMapper.delete(chunkWrapper);
        documentMapper.deleteById(documentId);
    }

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
