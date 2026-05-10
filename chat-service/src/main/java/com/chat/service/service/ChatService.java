package com.chat.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;

import java.util.List;

public interface ChatService extends IService<ChatSession> {

    /**
     * 创建会话
     */
    ChatSession createSession(Long userId);

    /**
     * 分配客服
     */
    void assignAgent(Long sessionId, Long agentId);

    /**
     * 发送消息
     */
    ChatMessage sendMessage(Long sessionId, Long senderId, String content);

    /**
     * 获取会话消息列表
     */
    List<ChatMessage> getMessages(Long sessionId);

    /**
     * 关闭会话
     */
    void closeSession(Long sessionId);
}
