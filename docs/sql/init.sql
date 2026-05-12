-- 企业客服系统 — 数据库初始化脚本

CREATE DATABASE IF NOT EXISTS chat_system DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

USE chat_system;

-- 用户表
CREATE TABLE IF NOT EXISTS `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `username` VARCHAR(64) NOT NULL COMMENT '用户名',
    `password` VARCHAR(128) NOT NULL COMMENT '密码(加密)',
    `nickname` VARCHAR(64) DEFAULT NULL COMMENT '昵称',
    `role` VARCHAR(32) NOT NULL DEFAULT 'USER' COMMENT '角色: USER-普通用户, AGENT-客服, ADMIN-管理员, AI-智能客服',
    `status` TINYINT NOT NULL DEFAULT 1 COMMENT '状态: 0-禁用, 1-启用',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_username` (`username`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='系统用户表';

-- 聊天会话表
CREATE TABLE IF NOT EXISTS `chat_session` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `user_id` BIGINT NOT NULL COMMENT '用户ID',
    `agent_id` BIGINT DEFAULT NULL COMMENT '分配的客服ID',
    `agent_type` VARCHAR(16) DEFAULT NULL COMMENT '服务方类型: HUMAN-人工客服, AI-智能客服',
    `status` VARCHAR(16) NOT NULL DEFAULT 'WAITING' COMMENT '状态: WAITING-等待, ACTIVE-进行中, CLOSED-已关闭',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `close_time` DATETIME DEFAULT NULL COMMENT '关闭时间',
    PRIMARY KEY (`id`),
    INDEX `idx_user_id` (`user_id`),
    INDEX `idx_agent_id` (`agent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='聊天会话表';

-- 消息记录表
CREATE TABLE IF NOT EXISTS `chat_message` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '主键ID',
    `session_id` BIGINT NOT NULL COMMENT '会话ID',
    `sender_id` BIGINT NOT NULL COMMENT '发送者ID',
    `sender_role` VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '发送者角色: USER-用户, AGENT-客服, AI-智能客服',
    `content` TEXT NOT NULL COMMENT '消息内容',
    `msg_type` VARCHAR(16) NOT NULL DEFAULT 'TEXT' COMMENT '消息类型: TEXT-文本, IMAGE-图片',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '发送时间',
    PRIMARY KEY (`id`),
    INDEX `idx_session_id` (`session_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='消息记录表';

-- 种子数据：AI 智能客服系统用户
INSERT INTO sys_user (username, password, nickname, role, status)
VALUES ('ai_assistant', MD5('ai_system_internal_@#!'), '智能客服', 'AI', 1);

-- 已有数据库迁移语句（如果表已存在，单独执行以下 ALTER）
-- ALTER TABLE chat_message ADD COLUMN sender_role VARCHAR(16) NOT NULL DEFAULT 'USER' COMMENT '发送者角色: USER-用户, AGENT-客服, AI-智能客服' AFTER sender_id;
-- ALTER TABLE chat_session ADD COLUMN agent_type VARCHAR(16) DEFAULT NULL COMMENT '服务方类型: HUMAN-人工客服, AI-智能客服' AFTER agent_id;
-- INSERT INTO sys_user (username, password, nickname, role, status) VALUES ('ai_assistant', MD5('ai_system_internal_@#!'), '智能客服', 'AI', 1);
