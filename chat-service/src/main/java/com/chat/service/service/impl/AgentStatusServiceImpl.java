package com.chat.service.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chat.common.entity.ChatSession;
import com.chat.common.utils.RedisKeyUtil;
import com.chat.service.mapper.ChatSessionMapper;
import com.chat.service.service.AgentStatusService;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.Collections;
import java.util.Comparator;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class AgentStatusServiceImpl implements AgentStatusService {

    @Resource
    private RedisTemplate<String, Object> redisTemplate;

    @Resource
    private ChatSessionMapper sessionMapper;

    @Override
    public void goOnline(Long agentId) {
        redisTemplate.opsForSet().add(RedisKeyUtil.onlineAgents(), agentId.toString());
    }

    @Override
    public void goOffline(Long agentId) {
        redisTemplate.opsForSet().remove(RedisKeyUtil.onlineAgents(), agentId.toString());
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
}
