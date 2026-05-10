package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;
import com.chat.service.mapper.ChatMessageMapper;
import com.chat.service.mapper.ChatSessionMapper;
import com.chat.service.service.ChatService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatService {

    @Resource
    private ChatMessageMapper messageMapper;

    @Override
    @Transactional
    public ChatSession createSession(Long userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setStatus("WAITING");
        save(session);
        return session;
    }

    @Override
    public void assignAgent(Long sessionId, Long agentId) {
        ChatSession session = getById(sessionId);
        if (session != null && "WAITING".equals(session.getStatus())) {
            session.setAgentId(agentId);
            session.setStatus("ACTIVE");
            updateById(session);
        }
    }

    @Override
    @Transactional
    public ChatMessage sendMessage(Long sessionId, Long senderId, String content) {
        ChatSession session = getById(sessionId);
        if (session == null || "CLOSED".equals(session.getStatus())) {
            throw new RuntimeException("会话不存在或已关闭");
        }
        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setContent(content);
        message.setMsgType("TEXT");
        messageMapper.insert(message);
        return message;
    }

    @Override
    public List<ChatMessage> getMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
               .orderByAsc(ChatMessage::getCreateTime);
        return messageMapper.selectList(wrapper);
    }

    @Override
    public void closeSession(Long sessionId) {
        ChatSession session = getById(sessionId);
        if (session != null) {
            session.setStatus("CLOSED");
            session.setCloseTime(LocalDateTime.now());
            updateById(session);
        }
    }
}
