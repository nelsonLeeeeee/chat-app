package com.chat.common.utils;

/**
 * Redis Key 生成工具
 */
public class RedisKeyUtil {

    private static final String PREFIX = "chat:";

    /** 在线用户集合 */
    public static String onlineUsers() {
        return PREFIX + "online:users";
    }

    /** 会话缓存 */
    public static String session(Long sessionId) {
        return PREFIX + "session:" + sessionId;
    }

    /** 用户令牌 */
    public static String userToken(Long userId) {
        return PREFIX + "token:" + userId;
    }
}
