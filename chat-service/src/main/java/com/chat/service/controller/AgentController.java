package com.chat.service.controller;

import com.chat.common.result.Result;
import com.chat.service.service.AgentStatusService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/agent")
public class AgentController {

    @Resource
    private AgentStatusService agentStatusService;

    @PostMapping("/online")
    public Result<Void> goOnline(@RequestParam Long agentId) {
        agentStatusService.goOnline(agentId);
        return Result.ok();
    }

    @PostMapping("/offline")
    public Result<Void> goOffline(@RequestParam Long agentId) {
        agentStatusService.goOffline(agentId);
        return Result.ok();
    }

    @GetMapping("/status")
    public Result<Map<String, Object>> status() {
        boolean hasOnline = agentStatusService.hasAnyOnlineAgent();
        int count = agentStatusService.getOnlineAgentIds().size();
        Map<String, Object> data = new HashMap<>();
        data.put("hasOnline", hasOnline);
        data.put("count", count);
        return Result.ok(data);
    }
}
