package com.chat.common.entity;

import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
public class ChatMessage {

    private Long id;
    private Long sessionId;
    private Long senderId;
    private String content;
    private String senderRole;
    private String msgType;
    private LocalDateTime createTime;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getSessionId() { return sessionId; }
    public void setSessionId(Long sessionId) { this.sessionId = sessionId; }
    public Long getSenderId() { return senderId; }
    public void setSenderId(Long senderId) { this.senderId = senderId; }
    public String getContent() { return content; }
    public void setContent(String content) { this.content = content; }
    public String getMsgType() { return msgType; }
    public String getSenderRole() { return senderRole; }
    public void setSenderRole(String senderRole) { this.senderRole = senderRole; }
    public void setMsgType(String msgType) { this.msgType = msgType; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
}
