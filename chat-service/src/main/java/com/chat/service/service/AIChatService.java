package com.chat.service.service;

public interface AIChatService {

    String generateResponse(String userMessage);

    String generateResponse(String userMessage, Long sessionId);

    Long getAiUserId();
}
