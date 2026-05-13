package com.chat.service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * 聊天服务启动类
 */
@SpringBootApplication(scanBasePackages = "com.chat")
@EnableDiscoveryClient
public class ChatServiceApplication {

    /**
     * Spring Boot 应用入口
     */
    public static void main(String[] args) {
        SpringApplication.run(ChatServiceApplication.class, args);
    }
}
