package com.chat.service.controller;

import com.chat.common.entity.KnowledgeDocument;
import com.chat.common.result.Result;
import com.chat.service.service.KnowledgeBaseService;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/knowledge")
public class KnowledgeBaseController {

    @Resource
    private KnowledgeBaseService knowledgeBaseService;

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

    @GetMapping("/list")
    public Result<List<KnowledgeDocument>> list() {
        return Result.ok(knowledgeBaseService.listDocuments());
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        knowledgeBaseService.deleteDocument(id);
        return Result.ok();
    }
}
