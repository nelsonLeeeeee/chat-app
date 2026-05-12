package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;
import com.chat.common.entity.SysUser;
import com.chat.service.mapper.ChatMessageMapper;
import com.chat.service.mapper.ChatSessionMapper;
import com.chat.service.service.AgentStatusService;
import com.chat.service.service.AIChatService;
import com.chat.service.service.ChatService;
import com.chat.service.service.UserService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatService {

    @Resource
    private ChatMessageMapper messageMapper;

    @Resource
    private AgentStatusService agentStatusService;

    @Resource
    private AIChatService aiChatService;

    @Resource
    private UserService userService;

    @Override
    @Transactional
    public ChatSession createSession(Long userId) {
        ChatSession session = new ChatSession();
        session.setUserId(userId);
        session.setStatus("ACTIVE");

        Long agentId = agentStatusService.pickAvailableAgent();
        if (agentId != null) {
            session.setAgentId(agentId);
            session.setAgentType("HUMAN");
        } else {
            Long aiUserId = aiChatService.getAiUserId();
            session.setAgentId(aiUserId);
            session.setAgentType("AI");
        }
        save(session);

        if ("AI".equals(session.getAgentType())) {
            String greeting = aiChatService.generateResponse("你好");
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(session.getId());
            aiMsg.setSenderId(aiChatService.getAiUserId());
            aiMsg.setSenderRole("AI");
            aiMsg.setContent(greeting);
            aiMsg.setMsgType("TEXT");
            messageMapper.insert(aiMsg);
        }

        return session;
    }

    @Override
    public void assignAgent(Long sessionId, Long agentId) {
        ChatSession session = getById(sessionId);
        if (session != null && "ACTIVE".equals(session.getStatus())) {
            session.setAgentId(agentId);
            session.setAgentType("HUMAN");
            updateById(session);
        }
    }

    @Override
    public ChatMessage sendMessage(Long sessionId, Long senderId, String content) {
        SysUser sender = userService.getById(senderId);
        String senderRole = sender != null ? sender.getRole() : "USER";
        return sendMessage(sessionId, senderId, senderRole, content);
    }

    @Override
    @Transactional
    public ChatMessage sendMessage(Long sessionId, Long senderId, String senderRole, String content) {
        ChatSession session = getById(sessionId);
        if (session == null || "CLOSED".equals(session.getStatus())) {
            throw new RuntimeException("会话不存在或已关闭");
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderRole(senderRole);
        message.setContent(content);
        message.setMsgType("TEXT");
        messageMapper.insert(message);

        if ("AI".equals(session.getAgentType()) && !"AI".equals(senderRole)) {
            String aiResponse = aiChatService.generateResponse(content);
            ChatMessage aiMsg = new ChatMessage();
            aiMsg.setSessionId(sessionId);
            aiMsg.setSenderId(aiChatService.getAiUserId());
            aiMsg.setSenderRole("AI");
            aiMsg.setContent(aiResponse);
            aiMsg.setMsgType("TEXT");
            messageMapper.insert(aiMsg);
        }

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
    public List<ChatSession> getSessions(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .orderByDesc(ChatSession::getCreateTime);
        return list(wrapper);
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
