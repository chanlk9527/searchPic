# SearchPic SaaS - 核心任务开发看板 (Task Board)

欢迎来到 SearchPic 真实落地工程开发看板！整个架构将被拆解为以下阶段，并以此列表进行滚动进度追踪。

---

## 阶段一：项目骨架与中间件集装箱化 (Phase 1: Base Infrastructure)
*目标：把所有的应用地基打好，数据库和搜搜引擎就绪，无任何业务代码。*

- [x] **初始化 Spring Boot 工程**：包含 `pom.xml` 和 `application.yml` 的搭建。
- [x] **基础设施 Docker 化**：完成 MySQL, Zookeeper, Kafka, Elasticsearch 8.x 的一键部署文件 `docker-compose.yml` 编写与调试。
- [x] **建立通用响应与拦截机制**：完成 `Result<T>` 统一返回类和 `GlobalExceptionHandler` 全局异常处理机制的编码。
- [x] **打通底层数据驱动**：
    - 创建 Flyway / MySQL 表结构 (`V1__init_schema.sql`)
    - 编写关系型 MySQL 实体 `Tenant.java` (SaaS 多租户信息) 与 JpaRepository。
    - 编写纯量+向量大杂烩的 Elasticsearch 模型 `AlertEventDocument.java` (包含 BM25 的 text 和 k-NN 用的 768维 Dense Vector)。

> **状态**：✅ **完全竣工**。基础设施 Docker 正在后台安全启动。

---

## 阶段二：大模型 AI 客户端集成底座 (Phase 2: External AI Clients "The Brain")
*目标：封装好所有的 RestClient，用于向第三方的 SaaS 开火，为我们后期的架构提供抽象层 (Facade)。*

- [x] **集成 Aliyun DashScope (通义千问 VL)**：
    - 封装 VLM Call：传入 Image_URL，强制输出包含 `"entities":[]` 和 `"scene_caption"` 的 JSON。
    - 封装 Text-Embedding Call：传入长难句（如 `scene_caption`），提取其 Float[768] 的密集向量数组。
- [x] **集成 Google Gemini (1.5 Pro/Flash)**：
    - 封装 LLM Call：作为 Query Understanding（意图读取）引擎。传入用户口语化的长句、时区和设备列表，严格提取出 `start_time`、`end_time` 以及 `search_intent_caption`。
- [x] **AI 统一兜底与容错链路 (Fallback)**：实现针对大模型接口限流、Token 消耗报错、超时断联的断路器 (Circuit Breaker) 回调。

> **状态**：✅ **完全竣工**

---

## 阶段三：写入流核心逻辑 (Phase 3: The Write Path - Ingestion)
*目标：实现从外部拿到图片，经过中间件清洗加工，再双写存入 ES。*

- [x] **API 接口白皮书文档化**: 编写正式的 `API_REFERENCE.md` 供客户查阅。
- [ ] **API Controller 开发**：搭建并发布对外接口 `POST /api/v1/events/ingest`。
- [ ] **鉴权拦截器 (Auth Interceptor)**：实现对传入 Headers 里的 API_KEY 进行 MySQL 比对，提取 `tenant_id`。
- [ ] **Kafka 消息投递 (Producer)**：把包装好的源数据（Image, Camera, AuthInfo）抛进 Kafka Queue。
- [ ] **Kafka 异步消费 (Consumer & AI Process)**：
    - 从队列取出图片事件。
    - [核心流] 调用 `VLM API` 获取实体与动作长句 -> 调用 `Embedding API` 获取动作向量 -> 利用 Spring Data ES 的 Template 操作持久化到 ElasticSearch。

> **状态**：⏳ **排队中**

---

## 阶段四：检索流核心逻辑 (Phase 4: The Read Path / Hybrid Search)
*目标：解析口语、混合检索、绝对防错位、硬性拦截。*

- [ ] **API Controller 开发**：搭建并发布对外搜索接口 `POST /api/v1/search/query`。
- [ ] **用户意图解析引擎 (QU Layer)**：将用户的 Query 发往 Gemini API，解析出时间范围、地点映射（Camera_ID）和纯正规英文意图长句 `search_intent_caption`。
- [ ] **搜索词向量化下推**：将上一步的意图长句送往 DashScope 的 Embedding 抽取稠密特征。
- [ ] **超大规模弹性混合搜索 (Hybrid Search Engine Builder)**：
    - `Filter`: 根据 `tenant_id` 和 LLM 解析出来的起止时间，在百万底图中一刀切抛弃垃圾记录。
    - `BM25 Match`: 精确碰撞实体的特征点单词。
    - `k-NN Match`: 向量空间临近点搜索（找具备相似物理动作行为结果的向量记录）。
- [ ] *(加分/可选)* **引入 Reranker 二阶排位赛**。

> **状态**：⏳ **排队中**

---

## 阶段五：联调与演示 (Phase 5: E2E Integration)
*目标：在真实数据库与接口环境，走通 SaaS 黑盒的数据。*
- [ ] 存入一张虚拟监控图；搜一个稀奇古怪的话语，观察它返回最精准匹配的结果和 EventID。
