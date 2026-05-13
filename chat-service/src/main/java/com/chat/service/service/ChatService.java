package com.chat.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;

import java.util.List;

public interface ChatService extends IService<ChatSession> {

    ChatSession createSession(Long userId);

    void assignAgent(Long sessionId, Long agentId);

    ChatMessage sendMessage(Long sessionId, Long senderId, String content);

    ChatMessage sendMessage(Long sessionId, Long senderId, String senderRole, String content);

    List<ChatMessage> getMessages(Long sessionId);

    List<ChatSession> getSessions(Long userId);

    List<ChatSession> getSessionsByAgent(Long agentId);
}
