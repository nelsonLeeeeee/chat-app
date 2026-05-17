# AI 客服 RAG（检索增强生成）设计说明

## 1. 整体架构

```
用户提问 → Embedding API(问题向量化) → Milvus 向量相似度搜索 → 拼接上下文
                                                                    ↓
用户 ← DeepSeek 生成回复 ← 系统提示词 + 知识上下文 + 对话历史
```

三阶段流水线，检索端采用 **Milvus 向量语义匹配 + MySQL LIKE 关键词回退**双通道：**摄入（Ingestion）→ 检索（Retrieval）→ 生成（Generation）**。

---

## 2. 知识摄入 — PDF 上传、分块与向量化

### 2.1 上传入口

`KnowledgeBaseController` → `KnowledgeBaseServiceImpl.uploadPdf(MultipartFile, uploaderId)`

- 仅允许 `.pdf` 后缀
- 上传上限：10MB（`spring.servlet.multipart.max-file-size`）

### 2.2 PDF 文本提取

使用 Apache PDFBox 2.0.29（兼容 Java 8）：

```java
PDDocument pdf = PDDocument.load(file.getInputStream());
PDFTextStripper stripper = new PDFTextStripper();
text = stripper.getText(pdf);
```

校验：提取文本不足 10 字符时拒绝，提示"可能为扫描件或图片型PDF"。

### 2.3 文本分块策略

`chunkText(text)` 方法：

| 参数 | 值 | 说明 |
|------|-----|------|
| 块大小 | 500 字符 | 每块约对应一页A4纸的段落 |
| 重叠量 | 50 字符 | 相邻块重叠，防止检索时语义断裂 |
| 断点优先级 | 句号/问号/感叹号/换行 | 优先在自然句末切断 |

- 末块不足 500 字符时直接取剩余文本
- 每块 `trim()` 去除首尾空白
- `chunk_index` 从 0 自增编号

### 2.4 分块向量化与存储

`EmbeddingServiceImpl` 调用 DeepSeek Embedding API 将每块文本转为语义向量，然后存入 Milvus：

```java
// 1. 先插入文档获取ID
documentMapper.insert(doc);

// 2. 批量生成向量（一次API调用处理所有分块）
List<float[]> vectors = embeddingService.embedBatch(chunks);

// 3. 向量写入Milvus
milvusVectorService.insertVectors(doc.getId(), chunks, vectors);

// 4. 文本分块写入MySQL（不含向量，仅作文本备份和LIKE回退）
chunkMapper.insert(kc);
```

- 嵌入 API：`POST https://api.deepseek.com/v1/embeddings`
- 嵌入模型：`deepseek-embedding-v1`
- 向量维度：1536
- 向量存储：Milvus（主），MySQL knowledge_chunk（文本备份）

### 2.5 数据库存储

**MySQL — knowledge_document**：文档元信息（文件名、大小、全文、分块数、上传者）

**MySQL — knowledge_chunk**：分块文本备份（content 字段），用于 LIKE 关键词回退检索，**不再存储 vector 字段**。

**Milvus — knowledge_chunks Collection**：

| 字段 | 类型 | 说明 |
|------|------|------|
| id | Int64 (PK, autoID) | 主键自增 |
| document_id | Int64 | 所属文档ID（关联 MySQL） |
| chunk_index | Int32 | 分块序号 |
| content | VarChar(65535) | 分块文本（冗余存储，便于返回） |
| vector | FloatVector(1536) | 语义向量 |

- 索引：AUTOINDEX（自动选择最优算法）
- 度量：COSINE（余弦相似度）

---

## 3. 知识检索 — 召回（Milvus）+ 重排（DeepSeek）+ MySQL LIKE 回退

### 3.1 两阶段检索管线

`AIChatServiceImpl.searchRelevantChunks(userMessage)`：

```
┌─────────────────────────────────────────────────────────┐
│ 阶段1：粗排召回（Milvus COSINE）                          │
│   问题向量 → Milvus.search(topK=20) → 20条候选           │
│                                                         │
│ 阶段2：精排重排（DeepSeek API）                           │
│   20条候选 + 用户问题 → DeepSeek 重排 → Top 5             │
│                                                         │
│ 降级策略：                                               │
│   DeepSeek重排失败 → 用Milvus粗排Top5直接返回             │
│   Milvus检索失败 → 回退MySQL LIKE关键词检索               │
└─────────────────────────────────────────────────────────┘
```

### 3.2 阶段1：Milvus 粗排召回

调用 `MilvusVectorService.search(queryVector, topK=20)`，使用 COSINE 度量在向量数据库内完成：

```
cos(a, b) = (a · b) / (|a| × |b|)
```

取值范围 [-1, 1]，1 表示方向完全一致。召回 20 条候选而非 5 条，确保高召回率（尽可能不漏掉相关片段）。

### 3.3 阶段2：DeepSeek API 精排重排

`AIChatServiceImpl.deepseekRerank(userMessage, candidates)`：

将 20 条候选片段与用户问题送入 DeepSeek 大模型，由模型判断语义相关性并选 Top 5：

```
请根据用户问题，从以下候选知识片段中选出最相关的5个，按相关性降序排列。
只返回片段编号（用逗号分隔），不要返回其他内容。

=== 用户问题 ===
{userMessage}

=== 候选片段 ===
[1] {chunk1前300字}
[2] {chunk2前300字}
...
[20] {chunk20前300字}
```

- 每条候选截取前 300 字符（控制 Token 消耗）
- 解析 DeepSeek 返回的编号列表，按序取对应片段
- DeepSeek 精排失败 → 自动降级为 Milvus 粗排 Top 5

### 3.4 为何需要两阶段检索

| 场景 | 单阶段粗排（仅向量） | 两阶段（粗排+精排） |
|------|:---:|:---:|
| 问题"退货流程"，文档含"退货申请步骤" | ✅ 向量命中 | ✅ 重排后排序更准 |
| 问题"退货流程"，文档含"发货流程" | ⚠️ 表面相关，混入Top5 | ✅ DeepSeek识别不相关，筛掉 |
| 问题"传感器价格"，文档含"传感器单价350元" | ✅ | ✅ |
| 问题"产品保修期限"，文档碎片散落多段 | ⚠️ 可能漏掉最佳片段 | ✅ DeepSeek全局视角选最优 |

### 3.5 关键词提取（回退用）

`AIChatServiceImpl.extractKeywords(text)`：

1. 正则 `[^一-龥a-zA-Z0-9]+` 替换非中英文/数字字符为空格
2. 按空白分词
3. 过滤：长度 ≥ 2 个字符
4. 去重
5. 截取前 10 个

### 3.6 关键词 LIKE 检索（回退）

`KnowledgeChunkMapper.searchChunks(keywords, limit=5)`：

```sql
SELECT kc.*, COUNT(*) AS match_count
FROM knowledge_chunk kc
WHERE kc.content LIKE '%关键词1%' OR kc.content LIKE '%关键词2%' ...
GROUP BY kc.id
ORDER BY match_count DESC
LIMIT 5
```

---

## 4. 知识增强生成 — DeepSeek 调用

### 4.1 触发场景

`ChatServiceImpl.sendMessage()` 中判断：仅当会话 `agentType = "AI"` 且发送者不是 AI 自身时触发 RAG 回复。

- **普通用户发消息** → AI 检索知识库并回复
- **人工客服发消息** → 不触发 AI（客服自行回复）

### 4.2 消息构建流程

`AIChatServiceImpl.generateResponse(userMessage, sessionId)`：

```
1. Milvus向量语义检索知识库 → 拼接上下文文本
2. 构建 System Prompt（注入知识上下文）
3. 加载最近 10 条对话历史 → 角色映射
4. 追加当前用户消息
5. 调用 DeepSeek Chat API
6. 解析返回内容
```

### 4.3 系统提示词模板

```
你是企业B2B客服助手，在一个企业级商务平台上为企业客户提供专业服务。

=== 回答准则 ===
1. 使用专业、简洁、礼貌的语气
2. 优先基于"知识库参考内容"回答，如果知识库有相关内容请明确引用
3. 如果知识库内容与问题无关，可基于通用知识回答，但要说明"根据通用知识"
4. 如果完全不知道答案，诚实说明并建议联系人工客服
5. 不要编造任何信息，特别是价格、合同条款等敏感内容
6. 回答要条理清晰，必要时分点列出

=== 知识库参考内容 ===
{context}

请根据以上知识库内容回答用户问题。
```

`{context}` 替换逻辑：
- 检索到分块 → 拼接为 `[片段1] xxx\n\n[片段2] xxx ...`
- 未检索到 → 替换为 `（暂无相关知识库内容，请基于通用知识回答并说明）`

### 4.4 对话历史注入

`loadRecentHistory(sessionId, 10)`：从数据库取最近 10 条消息，反转后按时间正序排列。

角色映射 `convertRole()`：

| 系统角色 | DeepSeek 角色 |
|----------|---------------|
| USER | user |
| AGENT | user |
| AI | assistant |
| SYSTEM | 跳过（不发送） |

### 4.5 DeepSeek Chat API 调用

`callDeepSeek(messages)`：

| 参数 | 值 |
|------|-----|
| URL | `https://api.deepseek.com/v1/chat/completions` |
| 模型 | `deepseek-chat` |
| temperature | 0.7 |
| max_tokens | 1000 |
| 超时 | 30 秒 |

### 4.6 降级策略

| 异常场景 | 处理方式 |
|----------|----------|
| DeepSeek 重排失败 | 降级为 Milvus 粗排 Top 5 直接返回 |
| Milvus 检索失败 | 自动回退 MySQL LIKE 关键词检索 |
| Embedding API 调用失败 | 自动回退 MySQL LIKE 关键词检索 |
| Milvus 返回空结果 | 回退 MySQL LIKE 关键词检索 |
| DeepSeek Chat API 调用失败 | 返回友好提示并建议转人工 |
| 知识库无匹配 | 提示词注入"暂无相关知识库内容" |
| PDF 无文字（扫描件） | 上传时直接拒绝 |
| PDF 解析异常 | 包装为 RuntimeException 返回给前端 |
| Milvus 写入失败 | 日志记录错误，分块文本仍存入 MySQL 备份 |

### 4.7 问候语（非 RAG）

`generateResponse(String userMessage)` 无 sessionId 参数时，使用简短的系统提示词生成 2 句以内的问候语，不做知识库检索，保证响应速度。

---

## 5. 配置清单

`chat-service/src/main/resources/application.yml`：

```yaml
deepseek:
  api:
    key: sk-xxx            # API 密钥（Chat + Embedding 共用）
    url: https://api.deepseek.com/v1/chat/completions
    model: deepseek-chat
  embedding:
    url: https://api.deepseek.com/v1/embeddings
    model: deepseek-embedding-v1
    dimension: 1536

milvus:
  host: localhost
  port: 19530
  collection: knowledge_chunks

spring:
  servlet:
    multipart:
      max-file-size: 10MB
      max-request-size: 10MB
```

---

## 6. 关键类一览

| 类 | 职责 |
|----|------|
| `MilvusVectorService` / `MilvusVectorServiceImpl` | Milvus Collection 管理（建表/索引/加载）、向量增删查 |
| `EmbeddingService` / `EmbeddingServiceImpl` | DeepSeek Embedding API 调用，文本→向量转换 |
| `KnowledgeBaseServiceImpl` | PDF 解析、文本分块、向量化后写入 Milvus + MySQL 备份 |
| `KnowledgeChunkMapper` | MySQL 关键词 LIKE 检索 SQL（回退用） |
| `AIChatServiceImpl` | 两阶段检索（Milvus粗排+DeepSeek精排）+ LIKE回退、上下文构建、Chat调用 |
| `ChatServiceImpl` | 消息路由，判断是否触发 AI 回复 |
| `AgentStatusServiceImpl` | 客服上下线时 AI↔人工会话转接 |

---

## 7. 数据流示意

```
客服上传PDF
  │
  ▼
PDFBox 提取文本 ──► chunkText 分块 ──► Embedding API 向量化
                                          │
                          ┌───────────────┘
                          ▼
                 knowledge_document (MySQL 文档元信息)
                 Milvus insertVectors (向量 + 文本)
                 knowledge_chunk (MySQL 文本备份)

用户发消息
  │
  ▼
Embedding API (问题转向量)
  │
  ▼
Milvus COSINE 粗排召回 Top20 ──► DeepSeek API 精排重排 Top5 ──► 拼接上下文
       │  失败则回退               │  失败降级用粗排Top5
       └──► extractKeywords        │
              │                    │
              ▼                    │
        MySQL LIKE 检索            │
                                   │
  loadRecentHistory ──► convertRole ──► 构建 messages[]
                                   │
                                   ▼
                            DeepSeek Chat API
                                   │
                                   ▼
                              解析返回
                                   │
                                   ▼
                              回复用户
```
