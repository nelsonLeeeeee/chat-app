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
import java.util.List;

/**
 * 聊天服务实现，负责会话创建、消息收发与客服分配
 */
@Service
public class ChatServiceImpl extends ServiceImpl<ChatSessionMapper, ChatSession> implements ChatService {

    @Resource
    private ChatMessageMapper messageMapper; // 消息数据访问

    @Resource
    private AgentStatusService agentStatusService; // 客服状态管理

    @Resource
    private AIChatService aiChatService; // AI回复生成

    @Resource
    private UserService userService; // 用户信息查询

    /**
     * 创建会话（B2B模式每用户仅保留一个活跃会话），自动分配客服或AI
     */
    @Override
    @Transactional
    public ChatSession createSession(Long userId) {
        // B2B模式：每个用户只保留一个会话，如果已有活跃会话则直接返回
        LambdaQueryWrapper<ChatSession> existWrapper = new LambdaQueryWrapper<>();
        existWrapper.eq(ChatSession::getUserId, userId)
                    .eq(ChatSession::getStatus, "ACTIVE");
        ChatSession existing = getOne(existWrapper);
        if (existing != null) {
            populateNames(java.util.Collections.singletonList(existing));
            return existing;
        }

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

    /**
     * 手动为会话分配指定人工客服
     */
    @Override
    public void assignAgent(Long sessionId, Long agentId) {
        ChatSession session = getById(sessionId);
        if (session != null && "ACTIVE".equals(session.getStatus())) {
            session.setAgentId(agentId);
            session.setAgentType("HUMAN");
            updateById(session);
        }
    }

    /**
     * 发送消息并自动推断发送者角色
     */
    @Override
    public ChatMessage sendMessage(Long sessionId, Long senderId, String content) {
        SysUser sender = userService.getById(senderId);
        String senderRole = sender != null ? sender.getRole() : "USER";
        return sendMessage(sessionId, senderId, senderRole, content);
    }

    /**
     * 发送消息（显式指定角色），AI会话中非AI消息触发自动回复
     */
    @Override
    @Transactional
    public ChatMessage sendMessage(Long sessionId, Long senderId, String senderRole, String content) {
        ChatSession session = getById(sessionId);
        if (session == null) {
            throw new RuntimeException("会话不存在");
        }

        ChatMessage message = new ChatMessage();
        message.setSessionId(sessionId);
        message.setSenderId(senderId);
        message.setSenderRole(senderRole);
        message.setContent(content);
        message.setMsgType("TEXT");
        messageMapper.insert(message);

        if ("AI".equals(session.getAgentType()) && !"AI".equals(senderRole)) {
            String aiResponse = aiChatService.generateResponse(content, sessionId);
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

    /**
     * 按时间正序获取会话消息列表
     */
    @Override
    public List<ChatMessage> getMessages(Long sessionId) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
               .orderByAsc(ChatMessage::getCreateTime);
        return messageMapper.selectList(wrapper);
    }

    /**
     * 获取用户会话列表并填充用户名
     */
    @Override
    public List<ChatSession> getSessions(Long userId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getUserId, userId)
               .orderByDesc(ChatSession::getCreateTime);
        List<ChatSession> sessions = list(wrapper);
        populateNames(sessions);
        return sessions;
    }

    /**
     * 获取客服活跃会话列表并填充用户与客服名称
     */
    @Override
    public List<ChatSession> getSessionsByAgent(Long agentId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentId, agentId)
               .eq(ChatSession::getStatus, "ACTIVE")
               .orderByDesc(ChatSession::getCreateTime);
        List<ChatSession> sessions = list(wrapper);
        populateNames(sessions);
        return sessions;
    }

    /**
     * 批量填充会话的用户名和客服名
     */
    private void populateNames(List<ChatSession> sessions) {
        for (ChatSession s : sessions) {
            SysUser user = userService.getById(s.getUserId());
            if (user != null) {
                s.setUserName(user.getNickname() != null ? user.getNickname() : user.getUsername());
            }
            if ("AI".equals(s.getAgentType())) {
                s.setAgentName("智能客服");
            } else if (s.getAgentId() != null) {
                SysUser agent = userService.getById(s.getAgentId());
                if (agent != null) {
                    s.setAgentName(agent.getNickname() != null ? agent.getNickname() : agent.getUsername());
                }
            }
        }
    }
}
