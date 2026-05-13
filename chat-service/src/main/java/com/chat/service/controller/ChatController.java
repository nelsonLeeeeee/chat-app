package com.chat.service.controller;

import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;
import com.chat.common.result.Result;
import com.chat.service.service.ChatService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

/**
 * 聊天消息接口
 */
@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    /**
     * 创建会话
     */
    @PostMapping("/session")
    public Result<ChatSession> createSession(@RequestParam Long userId) {
        ChatSession session = chatService.createSession(userId);
        return Result.ok(session);
    }

    /**
     * 分配客服到会话
     */
    @PostMapping("/session/{sessionId}/assign")
    public Result<Void> assignAgent(@PathVariable Long sessionId,
                                     @RequestParam Long agentId) {
        chatService.assignAgent(sessionId, agentId);
        return Result.ok();
    }

    /**
     * 发送消息
     */
    @PostMapping("/message")
    public Result<ChatMessage> sendMessage(@RequestParam Long sessionId,
                                           @RequestParam Long senderId,
                                           @RequestParam String content,
                                           @RequestParam(required = false) String senderRole) {
        String role = (senderRole != null && !senderRole.isEmpty()) ? senderRole : "USER";
        ChatMessage message = chatService.sendMessage(sessionId, senderId, role, content);
        return Result.ok(message);
    }

    /**
     * 获取会话历史消息
     */
    @GetMapping("/messages/{sessionId}")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long sessionId) {
        List<ChatMessage> messages = chatService.getMessages(sessionId);
        return Result.ok(messages);
    }

    /**
     * 获取用户会话列表
     */
    @GetMapping("/session/list")
    public Result<List<ChatSession>> getSessions(@RequestParam Long userId) {
        List<ChatSession> sessions = chatService.getSessions(userId);
        return Result.ok(sessions);
    }

    /**
     * 获取客服会话列表
     */
    @GetMapping("/session/agent")
    public Result<List<ChatSession>> getAgentSessions(@RequestParam Long agentId) {
        List<ChatSession> sessions = chatService.getSessionsByAgent(agentId);
        return Result.ok(sessions);
    }
}
