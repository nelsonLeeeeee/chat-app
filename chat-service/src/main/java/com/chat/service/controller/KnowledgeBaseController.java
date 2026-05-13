package com.chat.service.controller;

import com.chat.common.entity.KnowledgeDocument;
import com.chat.common.result.Result;
import com.chat.service.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

/**
 * 知识库管理接口
 */
@RestController
@RequestMapping("/knowledge")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

    /**
     * 上传 PDF 文档
     */
    @PostMapping("/upload")
    public Result<KnowledgeDocument> upload(@RequestParam("file") MultipartFile file,
                                            @RequestParam(required = false) Long uploaderId) {
        if (file.isEmpty()) {
            return Result.fail("文件不能为空");
        }
        String filename = file.getOriginalFilename();
        if (filename == null || !filename.toLowerCase().endsWith(".pdf")) {
            return Result.fail("仅支持PDF格式文件");
        }
        try {
            KnowledgeDocument doc = knowledgeBaseService.uploadPdf(file, uploaderId);
            return Result.ok(doc);
        } catch (RuntimeException e) {
            return Result.fail(e.getMessage());
        }
    }

    /**
     * 列出所有文档
     */
    @GetMapping("/list")
    public Result<List<KnowledgeDocument>> list() {
        return Result.ok(knowledgeBaseService.listDocuments());
    }

    /**
     * 删除文档
     */
    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return Result.ok();
    }
}
