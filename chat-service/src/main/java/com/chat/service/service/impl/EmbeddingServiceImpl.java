package com.chat.service.service.impl;

import cn.hutool.core.util.StrUtil;
import cn.hutool.http.HttpRequest;
import cn.hutool.http.HttpResponse;
import cn.hutool.json.JSONArray;
import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
import com.chat.service.service.EmbeddingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/**
 * DeepSeek Embedding API 实现，将文本转为语义向量
 */
@Service
public class EmbeddingServiceImpl implements EmbeddingService {

    private static final Logger log = LoggerFactory.getLogger(EmbeddingServiceImpl.class);

    @Value("${deepseek.api.key}")
    private String apiKey;

    @Value("${deepseek.embedding.url}")
    private String embeddingUrl;

    @Value("${deepseek.embedding.model}")
    private String embeddingModel;

    @Override
    public float[] embed(String text) {
        List<float[]> results = callEmbeddingApi(text);
        return results.isEmpty() ? new float[0] : results.get(0);
    }

    @Override
    public List<float[]> embedBatch(List<String> texts) {
        return callEmbeddingApi(texts.toArray(new String[0]));
    }

    /**
     * 调用 DeepSeek Embedding API
     */
    private List<float[]> callEmbeddingApi(String... inputs) {
        JSONObject body = new JSONObject();
        body.set("model", embeddingModel);
        body.set("input", inputs.length == 1 ? inputs[0] : inputs);

        try (HttpResponse response = HttpRequest.post(embeddingUrl)
                .header("Authorization", "Bearer " + apiKey)
                .header("Content-Type", "application/json")
                .body(body.toString())
                .timeout(60000)
                .execute()) {

            if (response.getStatus() != 200) {
                log.error("Embedding API 错误: status={}, body={}", response.getStatus(), response.body());
                throw new RuntimeException("Embedding API error: " + response.getStatus());
            }

            JSONObject result = JSONUtil.parseObj(response.body());
            JSONArray data = result.getJSONArray("data");
            List<float[]> vectors = new ArrayList<>();

            for (int i = 0; i < data.size(); i++) {
                JSONArray emb = data.getJSONObject(i).getJSONArray("embedding");
                float[] vec = new float[emb.size()];
                for (int j = 0; j < emb.size(); j++) {
                    vec[j] = emb.getFloat(j).floatValue();
                }
                vectors.add(vec);
            }
            return vectors;
        } catch (Exception e) {
            log.error("Embedding API 调用失败", e);
            throw new RuntimeException("Embedding API 调用失败: " + e.getMessage());
        }
    }

    @Override
    public double cosineSimilarity(float[] a, float[] b) {
        if (a.length != b.length) {
            throw new IllegalArgumentException("向量维度不一致: " + a.length + " vs " + b.length);
        }
        double dot = 0, normA = 0, normB = 0;
        for (int i = 0; i < a.length; i++) {
            dot += (double) a[i] * b[i];
            normA += (double) a[i] * a[i];
            normB += (double) b[i] * b[i];
        }
        if (normA == 0 || normB == 0) return 0;
        return dot / (Math.sqrt(normA) * Math.sqrt(normB));
    }
}
