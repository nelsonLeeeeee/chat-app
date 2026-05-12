package com.chat.service.service;

import java.util.Set;

public interface AgentStatusService {

    void goOnline(Long agentId);

    void goOffline(Long agentId);

    boolean isOnline(Long agentId);

    boolean hasAnyOnlineAgent();

    Long pickAvailableAgent();

    Set<Long> getOnlineAgentIds();
}
