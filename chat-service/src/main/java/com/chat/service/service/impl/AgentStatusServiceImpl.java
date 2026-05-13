package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;
import com.chat.common.utils.RedisKeyUtil;
import com.chat.service.mapper.ChatMessageMapper;
import com.chat.service.mapper.ChatSessionMapper;
import com.chat.service.service.AIChatService;
import com.chat.service.service.AgentStatusService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentStatusServiceImpl implements AgentStatusService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ChatSessionMapper sessionMapper;

    @Resource
    private ChatMessageMapper messageMapper;

    @Resource
    private AIChatService aiChatService;

    @Override
    public void goOnline(Long agentId) {
        redisTemplate.opsForSet().add(RedisKeyUtil.onlineAgents(), agentId.toString());
        // 将正在进行的 AI 会话转接给人工客服
        List<ChatSession> aiSessions = findAIActiveSessions();
        for (ChatSession s : aiSessions) {
            Long bestAgent = pickAvailableAgent();
            if (bestAgent != null) {
                s.setAgentId(bestAgent);
                s.setAgentType("HUMAN");
                sessionMapper.updateById(s);
                insertSystemMsg(s.getId(), "人工客服已上线，已为您转接人工客服");
            }
        }
    }

    @Override
    public void goOffline(Long agentId) {
        // 先查出该客服当前负责的活跃会话，再移除在线状态
        List<ChatSession> mySessions = findActiveSessionsByAgent(agentId);
        redisTemplate.opsForSet().remove(RedisKeyUtil.onlineAgents(), agentId.toString());

        for (ChatSession s : mySessions) {
            Long bestAgent = pickAvailableAgent();
            if (bestAgent != null) {
                s.setAgentId(bestAgent);
                s.setAgentType("HUMAN");
                sessionMapper.updateById(s);
                insertSystemMsg(s.getId(), "已为您转接其他人工客服");
            } else {
                s.setAgentId(aiChatService.getAiUserId());
                s.setAgentType("AI");
                sessionMapper.updateById(s);
                insertSystemMsg(s.getId(), "人工客服已下线，已为您接入智能客服");
            }
        }
    }

    @Override
    public boolean isOnline(Long agentId) {
        Boolean member = redisTemplate.opsForSet().isMember(RedisKeyUtil.onlineAgents(), agentId.toString());
        return Boolean.TRUE.equals(member);
    }

    @Override
    public boolean hasAnyOnlineAgent() {
        Long size = redisTemplate.opsForSet().size(RedisKeyUtil.onlineAgents());
        return size != null && size > 0;
    }

    @Override
    public Long pickAvailableAgent() {
        Set<Long> onlineIds = getOnlineAgentIds();
        if (onlineIds.isEmpty()) {
            return null;
        }
        // 选取当前活跃会话数最少的客服
        return onlineIds.stream()
                .min(Comparator.comparingLong(this::countActiveSessions))
                .orElse(null);
    }

    private long countActiveSessions(Long agentId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentId, agentId)
               .eq(ChatSession::getStatus, "ACTIVE");
        return sessionMapper.selectCount(wrapper);
    }

    @Override
    public Set<Long> getOnlineAgentIds() {
        Set<Object> members = redisTemplate.opsForSet().members(RedisKeyUtil.onlineAgents());
        if (members == null || members.isEmpty()) {
            return Collections.emptySet();
        }
        return members.stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .collect(Collectors.toSet());
    }

    private List<ChatSession> findAIActiveSessions() {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentType, "AI")
               .eq(ChatSession::getStatus, "ACTIVE");
        return sessionMapper.selectList(wrapper);
    }

    private List<ChatSession> findActiveSessionsByAgent(Long agentId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentId, agentId)
               .eq(ChatSession::getStatus, "ACTIVE");
        return sessionMapper.selectList(wrapper);
    }

    private void insertSystemMsg(Long sessionId, String content) {
        ChatMessage msg = new ChatMessage();
        msg.setSessionId(sessionId);
        msg.setSenderId(0L);
        msg.setSenderRole("SYSTEM");
        msg.setContent(content);
        msg.setMsgType("SYSTEM");
        messageMapper.insert(msg);
    }
}
