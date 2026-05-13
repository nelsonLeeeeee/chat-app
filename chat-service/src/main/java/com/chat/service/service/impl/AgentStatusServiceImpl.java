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

/**
 * 客服状态服务实现，基于Redis管理在线状态及负载均衡调度
 */
@Service
public class AgentStatusServiceImpl implements AgentStatusService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate; // Redis操作

    @Resource
    private ChatSessionMapper sessionMapper; // 会话数据访问

    @Resource
    private ChatMessageMapper messageMapper; // 消息数据访问

    @Resource
    private AIChatService aiChatService; // AI接管服务

    /**
     * 客服上线并自动接管AI活跃会话
     */
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

    /**
     * 客服下线并将会话转接其他客服或AI接管
     */
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

    /**
     * 检查指定客服是否在线
     */
    @Override
    public boolean isOnline(Long agentId) {
        Boolean member = redisTemplate.opsForSet().isMember(RedisKeyUtil.onlineAgents(), agentId.toString());
        return Boolean.TRUE.equals(member);
    }

    /**
     * 判断是否至少有一名客服在线
     */
    @Override
    public boolean hasAnyOnlineAgent() {
        Long size = redisTemplate.opsForSet().size(RedisKeyUtil.onlineAgents());
        return size != null && size > 0;
    }

    /**
     * 负载均衡选取活跃会话数最少的在线客服
     */
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

    /**
     * 统计指定客服当前活跃会话数
     */
    private long countActiveSessions(Long agentId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentId, agentId)
               .eq(ChatSession::getStatus, "ACTIVE");
        return sessionMapper.selectCount(wrapper);
    }

    /**
     * 从Redis获取所有在线客服ID
     */
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

    /**
     * 查询所有AI负责的活跃会话
     */
    private List<ChatSession> findAIActiveSessions() {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentType, "AI")
               .eq(ChatSession::getStatus, "ACTIVE");
        return sessionMapper.selectList(wrapper);
    }

    /**
     * 查询指定客服的活跃会话列表
     */
    private List<ChatSession> findActiveSessionsByAgent(Long agentId) {
        LambdaQueryWrapper<ChatSession> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatSession::getAgentId, agentId)
               .eq(ChatSession::getStatus, "ACTIVE");
        return sessionMapper.selectList(wrapper);
    }

    /**
     * 插入系统提示消息（转接通知等）
     */
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
