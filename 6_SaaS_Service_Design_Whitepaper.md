# 自然语言搜图 SaaS 服务设计白皮书 (SaaS Architecture & Product Design)

## 1. 概述与核心理念

若要将现有的“基于大语言与多模态模型的自然语言搜图系统”转化为一个可商业化运作的 SaaS（Software as a Service）服务，**“极简接入”**与**“开箱即用”**是决定生死的关键。

传统安防厂商或个人开发者往往缺乏 AI 模型调优、高并发消息队列维护以及复杂向量数据库运维的能力。我们的目标是：提供一组极简的 RESTful API，让任何 IPC 厂商、智能家居 App 开发商、甚至是个人极客，只需**几行代码**就能让他们的静态图片库拥有世界领先的“自然语言语义搜索”能力。

---

## 2. 北极星指标：One-Click API (极简接入体验)
SaaS 的客户不需要知道什么是 VLM，什么是 RAG，什么是 Elasticsearch 的打分机制。他们只需要两个动作：

### 2.1 动作一：图片“喂”给 SaaS (Data Ingestion API)
客户的摄像头产生告警图片后，只需无脑将其推给我们的 SaaS 网关。
- **Endpoint**: `POST /api/v1/events/ingest`
- **Payload**:
  ```json
  {
    "device_id": "user_camera_001",
    "timestamp": 1715423800,
    "image_url": "https://client-oss.example.com/img1.jpg",
    "metadata": { "location": "front_door" }
  }
  ```
- **SaaS 内部黑盒处理**：我们内部自动挂载 VLM 进行多维特征解析（如找人、找包裹、生成 `scene_caption`），自动做 Embedding，自动双写入隔离的弹性租户 Elasticsearch 索引中。

### 2.2 动作二：向 SaaS 提问 (Natural Language Search API)
客户在其 App 端提供一个类似 Google 的搜索框，用户输入不管多么隐晦复杂的自然语言，直接透传给我们。
- **Endpoint**: `POST /api/v1/search/query`
- **Payload**:
  ```json
  {
    "query": "昨天下午有谁拿走了我的快递贴纸？",
    "user_timezone": "Asia/Shanghai",
    "device_ids": ["user_camera_001"]
  }
  ```
- **SaaS 内部黑盒处理**：我们内部自动调起大语言模型进行意图理解（提取时间、地点映射、翻译实体词、合成 `search_intent_caption` 向量），并在 ES 中进行千万级数据的混合检索与重排列。
- **Response**: 返回高度匹配的原始 `image_url` 与 `event_id`。

---

## 3. SaaS 多租户技术架构 (Multi-Tenancy)
要实现商业化，就必须在一套物理集群上隔离成千上万个不同客户的数据，确保数据绝对安全且成本极低。

### 3.1 逻辑池隔离策略 (Logical Separation)
- 为每个注册的 SaaS 客户（Tenant）生成全局唯一的 `tenant_id` 和 API Key。
- 在底层的 Elasticsearch 中，不为每个客户建一个独立的索引（这会耗尽集群分片资源），而是在同一个大共享索引中强行加入 `tenant_id` 的路由（Routing Key）与强过滤字段。
- **数据安全防线**：所有针对底库的查询 DSL，在底层生成时都必须以强制的 `"filter": { "term": { "tenant_id": "xxx" } }` 包裹，从物理引擎层面彻底断绝 A 租户搜出 B 租户家庭照片的可能。

### 3.2 计费与限流模型 (Billing & Rate Limiting)
引入商业化的 API 网关（如 Kong, APISIX）。
- **图片处理扣费 (Tokens)**：调用 `/ingest` 接口是高成本动作（包含 VLM 显卡算力开销和 ES 的写入成本），应当计作 1 个 `Ingest Point`。
- **搜索请求扣费 (Tokens)**：调用 `/query` 也是高成本动作（包含 LLM 语言算力和 Rerank 重排算力），应当计作 1 个 `Search Point`。
- **用量看板与强熔断**：免费版每个月赠送 1,000 点。当 Token 耗尽或瞬时 QPS 突破阈值时，网关直接返回 `HTTP 429 Too Many Requests`。

---

## 4. B端客户控制台设计 (SaaS Dashboard)
提供一个极简优美的 Web 管理后台，供开发者和企业客户使用：

1. **密钥管理 (API Keys)**：创建与吊销可用于生产环境与测试环境的 Token。
2. **用量监控 (Analytics)**：展示每天成功入库了多少告警图，前端用户发起了多少次自然语言搜索（成功回调与失败重试）。这能直观看到这套 AI 对其业务的活跃度。
3. **数据大屏与保留策略 (TTL Manager)**：允许企业客户滑动条设置“云端索引留存期”（例如 7天、30天），系统据此定时执行 ES 数据清理（ILM 策略），平衡客户的使用成本。
4. **沙盒调试 (API Sandbox)**：右侧提供一个可视化的黑框，客户可以用 CURL 填入真实图片地址立刻看懂这个服务在做什么，这是开发者采纳工具最具转化率的“尤里卡时刻 (Eureka Moment)”。

---

## 5. 总结：做安防界的 "Algolia"
就像 Algolia 把枯燥复杂的全文搜索变成了电商网站的一句前端代码。我们的 SaaS 愿景是：**让世界上所有的 IPC 厂商不需要拥有一名 AI 工程师，也能在一天内为其 App 上线令人惊叹的自然语言“找包裹、查事件”能力。** 他们专心做硬件和云存，我们承包所有的多模态 AI 算力脏活累活。
