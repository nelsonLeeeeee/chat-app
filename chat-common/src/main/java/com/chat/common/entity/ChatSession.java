package com.chat.common.entity;

import com.baomidou.mybatisplus.annotation.TableField;

import java.time.LocalDateTime;

/**
 * 聊天会话实体
 */
public class ChatSession {

    private Long id;               // 主键ID
    private Long userId;           // 用户ID
    private Long agentId;          // 客服ID
    private String agentType;      // 客服类型
    private String status;         // 会话状态
    private LocalDateTime createTime;  // 创建时间
    private LocalDateTime closeTime;   // 关闭时间
    @TableField(exist = false)
    private String userName;       // 用户名（非表字段）
    @TableField(exist = false)
    private String agentName;      // 客服名（非表字段）

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long getUserId() { return userId; }
    public void setUserId(Long userId) { this.userId = userId; }
    public Long getAgentId() { return agentId; }
    public void setAgentId(Long agentId) { this.agentId = agentId; }
    public String getAgentType() { return agentType; }
    public void setAgentType(String agentType) { this.agentType = agentType; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public LocalDateTime getCreateTime() { return createTime; }
    public void setCreateTime(LocalDateTime createTime) { this.createTime = createTime; }
    public LocalDateTime getCloseTime() { return closeTime; }
    public void setCloseTime(LocalDateTime closeTime) { this.closeTime = closeTime; }
    public String getUserName() { return userName; }
    public void setUserName(String userName) { this.userName = userName; }
    public String getAgentName() { return agentName; }
    public void setAgentName(String agentName) { this.agentName = agentName; }
}
