package com.chat.service.service;

import java.util.Set;

/**
 * 客服状态服务接口，定义客服上下线与任务调度契约
 */
public interface AgentStatusService {

    /**
     * 客服上线，触发AI会话自动转接
     */
    void goOnline(Long agentId);

    /**
     * 客服下线，会话转接其他客服或AI接管
     */
    void goOffline(Long agentId);

    /**
     * 判断客服是否在线
     */
    boolean isOnline(Long agentId);

    /**
     * 是否有至少一名客服在线
     */
    boolean hasAnyOnlineAgent();

    /**
     * 选取当前负载最低的在线客服
     */
    Long pickAvailableAgent();

    /**
     * 获取所有在线客服ID集合
     */
    Set<Long> getOnlineAgentIds();
}
