package com.chat.service.service;

import com.baomidou.mybatisplus.extension.service.IService;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;

import java.util.List;

/**
 * 聊天服务接口，定义会话管理与消息收发契约
 */
public interface ChatService extends IService<ChatSession> {

    /**
     * 为用户创建会话（B2B模式每用户仅一个活跃会话）
     */
    ChatSession createSession(Long userId);

    /**
     * 为会话分配人工客服
     */
    void assignAgent(Long sessionId, Long agentId);

    /**
     * 发送消息（自动推断发送者角色）
     */
    ChatMessage sendMessage(Long sessionId, Long senderId, String content);

    /**
     * 发送消息（显式指定角色），AI会话会触发自动回复
     */
    ChatMessage sendMessage(Long sessionId, Long senderId, String senderRole, String content);

    /**
     * 获取会话历史消息（按时间正序）
     */
    List<ChatMessage> getMessages(Long sessionId);

    /**
     * 获取用户的会话列表（按创建时间倒序）
     */
    List<ChatSession> getSessions(Long userId);

    /**
     * 获取客服负责的会话列表
     */
    List<ChatSession> getSessionsByAgent(Long agentId);
}
