package com.chat.service.service;

/**
 * AI聊天服务接口，定义DeepSeek大模型回复生成契约
 */
public interface AIChatService {

    /**
     * 生成问候语回复（无历史上下文）
     */
    String generateResponse(String userMessage);

    /**
     * 生成RAG增强回复（带知识库检索与会话历史）
     */
    String generateResponse(String userMessage, Long sessionId);

    /**
     * 获取AI助手系统用户ID
     */
    Long getAiUserId();
}
