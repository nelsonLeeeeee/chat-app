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
import com.chat.service.service.EmbeddingService;
import com.chat.service.service.MilvusVectorService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.annotation.Resource;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

/**
 * AI聊天服务实现，调用DeepSeek大模型生成RAG增强回复
 */
@Service
public class AIChatServiceImpl implements AIChatService {

    private static final Logger log = LoggerFactory.getLogger(AIChatServiceImpl.class);

    /** 系统提示词模板，注入知识库上下文 */
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
    private String apiKey; // DeepSeek API密钥

    @Value("${deepseek.api.url}")
    private String apiUrl; // DeepSeek API地址

    @Value("${deepseek.api.model}")
    private String model; // 模型名称

    @Resource
    private UserMapper userMapper; // 用户数据访问

    @Resource
    private KnowledgeChunkMapper knowledgeChunkMapper; // 知识库检索

    @Resource
    private EmbeddingService embeddingService; // 向量嵌入

    @Resource
    private MilvusVectorService milvusVectorService; // Milvus向量检索

    @Resource
    private ChatMessageMapper chatMessageMapper; // 历史消息查询

    private volatile Long cachedAiUserId; // 缓存AI助手用户ID

    /**
     * 双检锁懒加载获取AI助手用户ID
     */
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

    /**
     * 生成简短问候语（无历史上下文）
     */
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

    /**
     * RAG增强回复：向量语义检索+历史上下文+DeepSeek生成
     */
    @Override
    public String generateResponse(String userMessage, Long sessionId) {
        try {
            // 1. 语义检索知识库
            List<KnowledgeChunk> chunks = searchRelevantChunks(userMessage);
            String context = "";
            if (!chunks.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                for (int i = 0; i < chunks.size(); i++) {
                    KnowledgeChunk c = chunks.get(i);
                    sb.append("[片段").append(i + 1).append("] ")
                            .append(c.getContent()).append("\n\n");
                }
                context = sb.toString();
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

    /**
     * RAG检索+重排：Milvus粗排召回Top20 → DeepSeek精排 → Top5
     */
    private List<KnowledgeChunk> searchRelevantChunks(String userMessage) {
        // 1. Milvus向量粗排召回 Top20
        try {
            float[] queryVector = embeddingService.embed(userMessage);
            if (queryVector.length > 0) {
                List<MilvusVectorService.SearchResult> results = milvusVectorService.search(queryVector, 20);
                if (results != null && !results.isEmpty()) {
                    // 2. DeepSeek API 精排
                    List<KnowledgeChunk> ranked = deepseekRerank(userMessage, results);
                    if (ranked != null && !ranked.isEmpty()) {
                        return ranked;
                    }
                    // DeepSeek重排失败，回退到向量粗排Top5
                    log.warn("DeepSeek重排失败，使用Milvus粗排Top5");
                    return results.stream()
                            .limit(5)
                            .map(r -> {
                                KnowledgeChunk kc = new KnowledgeChunk();
                                kc.setContent(r.getContent());
                                return kc;
                            })
                            .collect(Collectors.toList());
                }
            }
        } catch (Exception e) {
            log.warn("Milvus向量检索失败，回退到关键词检索: {}", e.getMessage());
        }

        // 回退：关键词 LIKE 检索
        List<String> keywords = extractKeywords(userMessage);
        if (!keywords.isEmpty()) {
            return knowledgeChunkMapper.searchChunks(keywords, 5);
        }
        return new ArrayList<>();
    }

    /**
     * DeepSeek重排：将候选片段与用户问题送入大模型，由其选出最相关的Top5
     */
    private List<KnowledgeChunk> deepseekRerank(String userMessage, List<MilvusVectorService.SearchResult> candidates) {
        if (candidates.isEmpty()) return null;
        int topN = Math.min(20, candidates.size());

        // 构建重排提示词
        StringBuilder sb = new StringBuilder();
        sb.append("请根据用户问题，从以下候选知识片段中选出最相关的5个，按相关性降序排列。\n");
        sb.append("只返回片段编号（用逗号分隔），不要返回其他内容。\n\n");
        sb.append("=== 用户问题 ===\n").append(userMessage).append("\n\n");
        sb.append("=== 候选片段 ===\n");
        for (int i = 0; i < topN; i++) {
            String content = candidates.get(i).getContent();
            if (content.length() > 300) {
                content = content.substring(0, 300) + "...";
            }
            sb.append("[").append(i + 1).append("] ").append(content).append("\n\n");
        }

        try {
            List<JSONObject> messages = new ArrayList<>();
            messages.add(new JSONObject().set("role", "user").set("content", sb.toString()));
            String response = callDeepSeek(messages);
            if (StrUtil.isBlank(response)) return null;

            // 解析 DeepSeek 返回的编号列表
            String[] parts = response.trim().split("[^0-9]+");
            List<KnowledgeChunk> ranked = new ArrayList<>();
            for (String p : parts) {
                if (StrUtil.isBlank(p)) continue;
                try {
                    int idx = Integer.parseInt(p.trim()) - 1;
                    if (idx >= 0 && idx < candidates.size()) {
                        KnowledgeChunk kc = new KnowledgeChunk();
                        kc.setContent(candidates.get(idx).getContent());
                        ranked.add(kc);
                        if (ranked.size() >= 5) break;
                    }
                } catch (NumberFormatException ignored) {}
            }

            if (!ranked.isEmpty()) {
                log.info("DeepSeek重排完成，粗排{}条 → 精排{}条", candidates.size(), ranked.size());
                return ranked;
            }
        } catch (Exception e) {
            log.warn("DeepSeek重排 API 调用失败: {}", e.getMessage());
        }
        return null;
    }

    /**
     * 调用DeepSeek API发送消息列表并解析回复
     */
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

    /**
     * 从用户文本提取关键词用于知识库检索
     */
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

    /**
     * 加载最近N条会话历史（返回时间正序）
     */
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

    /**
     * 将系统角色转为DeepSeek API的角色标识
     */
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
