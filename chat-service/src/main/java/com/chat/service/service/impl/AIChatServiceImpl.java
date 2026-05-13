package com.chat.service.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.chat.common.entity.ChatMessage;
import com.chat.common.entity.KnowledgeChunk;
import com.chat.common.entity.SysUser;
import com.chat.service.mapper.ChatMessageMapper;
import com.chat.service.mapper.KnowledgeChunkMapper;
import com.chat.service.mapper.UserMapper;
import com.chat.service.service.AIChatService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

@Service
public class AIChatServiceImpl implements AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatServiceImpl.class);

    private static final String SYSTEM_PROMPT = "你是企业B2B客服助手，在一个企业级商务平台上为企业客户提供专业服务。\n\n"
            + "=== 回答准则 ===\n"
            + "1. 使用专业、简洁、礼貌的语气\n"
            + "2. 优先基于\"知识库参考内容\"回答，如果知识库有相关内容请明确引用\n"
            + "3. 如果知识库内容与问题无关，可基于通用知识回答，但要说明\"根据通用知识\"\n"
            + "4. 如果完全不知道答案，诚实说明并建议联系人工客服\n"
            + "5. 不要编造任何信息，特别是价格、合同条款等敏感内容\n"
            + "6. 回答要条理清晰，必要时分点列出\n\n"
            + "=== 知识库参考内容 ===\n"
            + "{context}\n\n"
            + "请根据以上知识库内容回答用户问题。";

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.api.url}")
    private String apiUrl;

    @Value("${deepseek.api.model}")
    private String model;

    @Resource
    private UserMapper userMapper;

    @Resource
    private KnowledgeChunkMapper knowledgeChunkMapper;

    @Resource
    private ChatMessageMapper chatMessageMapper;

    private volatile Long cachedAiUserId;

    @Override
    public Long getAiUserId() {
        if (cachedAiUserId == null) {
            synchronized (this) {
                if (cachedAiUserId == null) {
                    SysUser ai = userMapper.selectOne(
                            new LambdaQueryWrapper<SysUser>()
                                    .eq(SysUser::getUsername, "ai_assistant"));
                    if (ai == null) {
                        throw new RuntimeException("AI助手用户未初始化，请先执行数据库迁移脚本");
                    }
                    cachedAiUserId = ai.getId();
                }
            }
        }
        return cachedAiUserId;
    }

    @Override
    public String generateResponse(String userMessage) {
        try {
            List<JSONObject> messages = new ArrayList<>();
            messages.add(new JSONObject().set("role", "system").set("content",
                    "你是企业B2B客服助手。请用专业友好的方式打招呼，简单介绍自己是智能客服助手，并表示愿意帮助解答问题。回答控制在两句话以内。"));
            messages.add(new JSONObject().set("role", "user").set("content", userMessage));
            return callDeepSeek(messages);
        } catch (Exception e) {
            log.error("DeepSeek API 生成问候语失败", e);
            return "您好！我是智能客服助手，请问有什么可以帮您？";
        }
    }

    @Override
    public String generateResponse(String userMessage, Long sessionId) {
        try {
            // 1. 提取关键词并检索知识库
            List<String> keywords = extractKeywords(userMessage);
            String context = "";
            if (!keywords.isEmpty()) {
                List<KnowledgeChunk> chunks = knowledgeChunkMapper.searchChunks(keywords, 5);
                if (!chunks.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (int i = 0; i < chunks.size(); i++) {
                        KnowledgeChunk c = chunks.get(i);
                        sb.append("[片段").append(i + 1).append("] ")
                                .append(c.getContent()).append("\n\n");
                    }
                    context = sb.toString();
                }
            }

            // 2. 构建消息列表
            List<JSONObject> messages = new ArrayList<>();

            String systemPrompt = SYSTEM_PROMPT.replace("{context}",
                    context.isEmpty() ? "（暂无相关知识库内容，请基于通用知识回答并说明）" : context);
            messages.add(new JSONObject().set("role", "system").set("content", systemPrompt));

            // 3. 加载对话历史（最近10条）
            List<ChatMessage> history = loadRecentHistory(sessionId, 10);
            for (ChatMessage m : history) {
                String role = convertRole(m.getSenderRole());
                if (role != null) {
                    messages.add(new JSONObject().set("role", role).set("content", m.getContent()));
                }
            }

            // 4. 当前用户消息
            messages.add(new JSONObject().set("role", "user").set("content", userMessage));

            return callDeepSeek(messages);
        } catch (Exception e) {
            log.error("DeepSeek RAG 调用失败, sessionId={}", sessionId, e);
            return "抱歉，我暂时无法处理您的问题，请稍后再试或输入\"人工客服\"转接人工服务。";
        }
    }

    private String callDeepSeek(List<JSONObject> messages) {
        JSONObject body = new JSONObject();
        body.set("model", model);
        body.set("messages", messages);
        body.set("temperature", 0.7);
        body.set("max_tokens", 1000);

        String requestBody = body.toString();

        try (HttpResponse response = HttpRequest.post(apiUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(requestBody)
                .timeout(30000)
                .execute()) {

            if (response.getStatus() != 200) {
                log.error("DeepSeek API 返回错误状态码: {}, body: {}", response.getStatus(), response.body());
                throw new RuntimeException("DeepSeek API error: " + response.getStatus());
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray choices = result.getJSONArray("choices");
            if (choices == null || choices.isEmpty()) {
                throw new RuntimeException("DeepSeek API 返回结果为空");
            }

            String content = choices.getJSONObject(0).getJSONObject("message").getStr("content");
            if (StrUtil.isBlank(content)) {
                throw new RuntimeException("DeepSeek API 返回内容为空");
            }

            return content.trim();
        }
    }

    private List<String> extractKeywords(String text) {
        if (StrUtil.isBlank(text)) return new ArrayList<>();
        String result = text.replaceAll("[^\\u4e00-\\u9fa5a-zA-Z0-9]+", " ");
        return Arrays.stream(result.trim().split("\\s+"))
                .map(String::trim)
                .filter(s -> s.length() >= 2)
                .distinct()
                .limit(10)
                .collect(Collectors.toList());
    }

    private List<ChatMessage> loadRecentHistory(Long sessionId, int limit) {
        LambdaQueryWrapper<ChatMessage> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ChatMessage::getSessionId, sessionId)
               .orderByDesc(ChatMessage::getCreateTime)
               .last("LIMIT " + limit);
        List<ChatMessage> messages = chatMessageMapper.selectList(wrapper);
        // 反转回时间正序
        List<ChatMessage> result = new ArrayList<>();
        for (int i = messages.size() - 1; i >= 0; i--) {
            result.add(messages.get(i));
        }
        return result;
    }

    private String convertRole(String senderRole) {
        if ("USER".equals(senderRole) || "AGENT".equals(senderRole)) {
            return "user";
        }
        if ("AI".equals(senderRole)) {
            return "assistant";
        }
        return null;
    }
}
