package com.chat.common.utils;

/**
 * Redis Key 生成工具
 */
public class RedisKeyUtil {

    private static final String PREFIX = "chat:";   // Key前缀

    /**
     * 在线用户集合Key
     */
    public static String onlineUsers() {
        return PREFIX + "online:users";
    }

    /**
     * 在线客服集合Key
     */
    public static String onlineAgents() {
        return PREFIX + "online:agents";
    }

    /**
     * 会话缓存Key
     */
    public static String session(Long sessionId) {
        return PREFIX + "session:" + sessionId;
    }

    /**
     * 用户令牌Key
     */
    public static String userToken(Long userId) {
        return PREFIX + "token:" + userId;
    }
}
