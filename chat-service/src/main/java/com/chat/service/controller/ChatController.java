package com.chat.service.controller;

import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.ChatSession;
import com.chat.common.result.Result;
import com.chat.service.service.ChatService;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import java.util.List;

@RestController
@RequestMapping("/chat")
public class ChatController {

    @Resource
    private ChatService chatService;

    @PostMapping("/session")
    public Result<ChatSession> createSession(@RequestParam Long userId) {
        ChatSession session = chatService.createSession(userId);
        return Result.ok(session);
    }

    @PostMapping("/session/{sessionId}/assign")
    public Result<Void> assignAgent(@PathVariable Long sessionId,
                                     @RequestParam Long agentId) {
        chatService.assignAgent(sessionId, agentId);
        return Result.ok();
    }

    @PostMapping("/message")
    public Result<ChatMessage> sendMessage(@RequestParam Long sessionId,
                                           @RequestParam Long senderId,
                                           @RequestParam String content) {
        ChatMessage message = chatService.sendMessage(sessionId, senderId, content);
        return Result.ok(message);
    }

    @GetMapping("/messages/{sessionId}")
    public Result<List<ChatMessage>> getMessages(@PathVariable Long sessionId) {
        List<ChatMessage> messages = chatService.getMessages(sessionId);
        return Result.ok(messages);
    }

    @PostMapping("/session/{sessionId}/close")
    public Result<Void> closeSession(@PathVariable Long sessionId) {
        chatService.closeSession(sessionId);
        return Result.ok();
    }
}
