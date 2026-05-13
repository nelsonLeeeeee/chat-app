package com.chat.common.entity;

import java.time.LocalDateTime;

/**
 * 聊天消息实体
 */
public class ChatMessage {

    private Long id;               // 主键ID
    private Long sessionId;        // 会话ID
    private Long senderId;         // 发送者ID
    private String content;        // 消息内容
    private String senderRole;     // 发送者角色
    private String msgType;        // 消息类型
    private LocalDateTime createTime;  // 创建时间

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
