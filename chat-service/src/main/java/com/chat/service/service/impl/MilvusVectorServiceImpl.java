package com.chat.service.service.impl;

import com.chat.service.service.MilvusVectorService;
import io.milvus.v2.client.ConnectConfig;
import io.milvus.v2.client.MilvusClientV2;
import io.milvus.v2.common.DataType;
import io.milvus.v2.common.IndexParam;
import io.milvus.v2.service.collection.request.*;
import io.milvus.v2.service.index.request.CreateIndexReq;
import io.milvus.v2.service.vector.request.InsertReq;
import io.milvus.v2.service.vector.request.SearchReq;
import io.milvus.v2.service.vector.request.DeleteReq;
import io.milvus.v2.service.vector.request.data.FloatVec;
import io.milvus.v2.service.vector.response.InsertResp;
import io.milvus.v2.service.vector.response.SearchResp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import com.google.gson.Gson;
import com.google.gson.JsonObject;
import org.springframework.stereotype.Service;

import javax.annotation.PostConstruct;
import javax.annotation.PreDestroy;
import java.util.*;

/**
 * Milvus 向量存储实现，管理Collection生命周期与向量增删查
 */
@Service
public class MilvusVectorServiceImpl implements MilvusVectorService {

    private static final Logger log = LoggerFactory.getLogger(MilvusVectorServiceImpl.class);

    @Value("${milvus.host}")
    private String host;

    @Value("${milvus.port}")
    private int port;

    @Value("${milvus.collection}")
    private String collectionName;

    @Value("${deepseek.embedding.dimension}")
    private int dimension;

    private MilvusClientV2 client;

    @PostConstruct
    public void init() {
        ConnectConfig config = ConnectConfig.builder()
                .uri("http://" + host + ":" + port)
                .build();
        client = new MilvusClientV2(config);
        initCollection();
    }

    @PreDestroy
    public void close() {
        if (client != null) {
            try { client.close(); } catch (Exception e) { log.warn("关闭Milvus连接失败", e); }
        }
    }

    @Override
    public void initCollection() {
        // 检查Collection是否存在
        try {
            client.describeCollection(DescribeCollectionReq.builder()
                    .collectionName(collectionName).build());
            log.info("Milvus Collection '{}' 已存在", collectionName);
            loadCollection();
            return;
        } catch (Exception ignored) {}

        // 创建Collection
        CreateCollectionReq.CollectionSchema schema = client.createSchema();
        schema.addField(AddFieldReq.builder()
                .fieldName("id").dataType(DataType.Int64).isPrimaryKey(true).autoID(true).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("document_id").dataType(DataType.Int64).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("chunk_index").dataType(DataType.Int32).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("content").dataType(DataType.VarChar).maxLength(65535).build());
        schema.addField(AddFieldReq.builder()
                .fieldName("vector").dataType(DataType.FloatVector).dimension(dimension).build());

        CreateCollectionReq req = CreateCollectionReq.builder()
                .collectionName(collectionName)
                .collectionSchema(schema)
                .build();
        client.createCollection(req);

        // 创建索引（AUTOINDEX 自动选择最优算法）
        IndexParam indexParam = IndexParam.builder()
                .fieldName("vector")
                .indexType(IndexParam.IndexType.AUTOINDEX)
                .metricType(IndexParam.MetricType.COSINE)
                .build();
        CreateIndexReq indexReq = CreateIndexReq.builder()
                .collectionName(collectionName)
                .indexParams(Collections.singletonList(indexParam))
                .build();
        client.createIndex(indexReq);

        loadCollection();
        log.info("Milvus Collection '{}' 创建完成，维度={}", collectionName, dimension);
    }

    private void loadCollection() {
        client.loadCollection(LoadCollectionReq.builder()
                .collectionName(collectionName).build());
    }

    @Override
    public void insertVectors(Long documentId, List<String> chunks, List<float[]> vectors) {
        Gson gson = new Gson();
        List<JsonObject> rows = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            JsonObject row = new JsonObject();
            row.addProperty("document_id", documentId);
            row.addProperty("chunk_index", i);
            row.addProperty("content", chunks.get(i));
            List<Float> vecList = new ArrayList<>();
            for (float v : vectors.get(i)) {
                vecList.add(v);
            }
            row.add("vector", gson.toJsonTree(vecList));
            rows.add(row);
        }

        InsertReq req = InsertReq.builder()
                .collectionName(collectionName)
                .data(rows)
                .build();
        InsertResp resp = client.insert(req);
        log.info("向Milvus插入{}条向量，documentId={}", resp.getInsertCnt(), documentId);
    }

    @Override
    public List<SearchResult> search(float[] queryVector, int topK) {
        SearchReq req = SearchReq.builder()
                .collectionName(collectionName)
                .data(Collections.singletonList(new FloatVec(queryVector)))
                .topK(topK)
                .outputFields(Arrays.asList("document_id", "chunk_index", "content"))
                .build();

        SearchResp resp = client.search(req);
        List<SearchResult> results = new ArrayList<>();

        for (List<SearchResp.SearchResult> resultList : resp.getSearchResults()) {
            for (SearchResp.SearchResult sr : resultList) {
                Map<String, Object> entity = sr.getEntity();
                String content = (String) entity.get("content");
                double score = sr.getScore();
                results.add(new SearchResult(content, score));
            }
        }
        return results;
    }

    @Override
    public void deleteByDocumentId(Long documentId) {
        DeleteReq req = DeleteReq.builder()
                .collectionName(collectionName)
                .filter("document_id == " + documentId)
                .build();
        client.delete(req);
        log.info("从Milvus删除文档{}的所有向量", documentId);
    }
}
