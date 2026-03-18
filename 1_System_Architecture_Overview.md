# 【云端智能 IPC】基于大模型与多模态模型的自然语言搜图系统架构设计

## 1. 项目概述与业务蓝图 (Project Overview)

### 1.1 项目背景与痛点分析
- **1.1.1 传统 IPC 检索的局限性**：详述基于时间轴和“机非人”硬规则检索的用户体验痛点。
- **1.1.2 边缘算力缺失的挑战**：分析存量 IPC 硬件无 AI 能力的现状，确立纯云端架构的必要性。
- **1.1.3 全球化多语言支持痛点**：说明出海业务面临的用户母语复杂性对底层检索架构的冲击。

### 1.2 业务目标与核心场景
- **1.2.1 核心业务指标**：定义自然语言搜图的预期查准率、端到端延迟（P99）及系统吞吐量。
- **1.2.2 典型用户 User Story**：列举 3-5 个真实安防防盗、寻物的搜索场景。

### 1.3 术语表 (Glossary)
- **VLM (Vision-Language Model)** / **LLM (Large Language Model)** / **Hybrid Search (混合检索)** / **BM25** / **QU (Query Understanding)** / **Reranker (重排模型)** 等核心专业词汇定义。

---

## 2. 总体系统架构设计 (System Architecture)

### 2.1 宏观架构逻辑拓扑
- **2.1.1 三层业务架构划分**：定义接入层、核心处理层（QU 与特征提取）、数据存储层。
- **2.1.2 写入流与读取流解耦设计**：阐述告警打标与用户查询分离的“异步+实时”混合架构原则。

### 2.2 核心技术栈选型与论证
- **2.2.1 微服务框架**：基于 Spring Boot/Cloud 的后端服务栈。
- **2.2.2 大模型底座选型**：Qwen-VL（打标）、轻量级 LLM（解析）的性能与成本对比。
- **2.2.3 搜索引擎选型**：Elasticsearch 8.x 支持 Hybrid Search 的优势分析。
- **2.2.4 关系型数据库与中间件**：MySQL (元数据) 与 Redis/Kafka (队列与缓存) 的角色定位。

### 2.3 全球化语言统一策略 (Pivot Language Strategy)
- 详述“**入口强制翻译，底层绝对纯英文**”的战略架构依据及其对后期扩展性的决定性作用。

---

## 3. 写入流：智能图像结构化与打标子系统 (Write Path)

### 3.1 告警接入与网关层 (Ingestion Gateway)
- **3.1.1 协议与鉴权**：设备端上报告警的认证机制与频率限制。
- **3.1.2 极简脱水透传策略**：网关层如何快速封装 `camera_id`, `timestamp`, `image_url`。

### 3.2 异步削峰与消息队列设计 (Message Queue)
- **3.2.1 流量特征分析**：应对雷雨、树影摇晃导致的突发高并发告警。
- **3.2.2 Topic 与 Partition 策略**：基于 Camera ID 的哈希路由，确保同一通道告警有序消费。

### 3.3 VLM 图像解析工作节点 (GPU Worker Node)
- **3.3.1 消费模型与微批处理 (Micro-batching)**：如何最大化榨干 GPU 显存吞吐率。
- **3.3.2 VLM System Prompt 工程设计**：给出强制输出精简英文 JSON、抑制幻觉的终极 Prompt 模板。
- **3.3.3 异常图片拦截机制**：如何识别并丢弃全黑、全白、严重遮挡的废片，节省算力。

### 3.4 特征向量化与数据双写 (Data Sink)
- **3.4.1 文本 Embedding 提取**：调用本地轻量级模型将英文描述转为高维稠密向量。
- **3.4.2 MySQL 与 ES 的双写一致性保障**：利用消息确认机制（ACK）或 Canal 监听 Binlog 实现最终一致性。

---

## 4. 读取流：查询理解与意图解析子系统 (Read Path - QU)

### 4.1 用户请求接入与预处理
- **4.1.1 入参定义**：用户输入字符串、时区信息、请求上下文（如绑定的设备列表）。

### 4.2 基于 LLM 的查询重写引擎 (Query Rewriting Engine)
- **4.2.1 相对时间绝对化提取 (Time Anchoring)**：将“昨天下午”转换为精确 Unix 时间戳范围的技术实现。
- **4.2.2 空间范围识别与映射**：将用户口语化的地点映射至 MySQL 中的物理摄像机通道。
- **4.2.3 多语言强制英译与扩充**：将异构母语转换为标准英文，并补充同义词。
- **4.2.4 LLM 异常兜底策略 (Fallback)**：当 LLM 宕机或解析出空时间时，后端的强制默认时间窗口代码逻辑。

### 4.3 搜索词向量化
- 对翻译后的标准英文实体进行 Embedding 处理，准备构建查询 DSL。

---

## 5. 核心检索引擎设计 (Search Engine Layer)

### 5.1 Elasticsearch 索引结构与分词器配置 (Index Mapping & Settings)
- **5.1.1 english Analyzer 的深度定制**：词根还原 (Stemmer) 与停用词过滤配置。
- **5.1.2 安防领域同义词字典库 (Synonym Graph)**：`e-bike`, `scooter` 与 `bicycle` 的等价映射表设计。
- **5.1.3 数据结构映射**：定义 `text` 字段与 `dense_vector` 字段。

### 5.2 混合检索查询构建 (Hybrid Search DSL Construction)
- **5.2.1 前置标量过滤层 (Pre-filtering)**：基于 `camera_id` 和 `timestamp` 的硬性截断机制。
- **5.2.2 BM25 (Sparse) 与 向量 (Dense) 分数融合机制**：详细解释 Alpha Weighting 的权重配比算法与调参经验（如设定 0.4 的依据）。

### 5.3 重排与极速截断机制 (Cross-Encoder Re-ranking) [核心防线]
- **5.3.1 二阶段级联打分架构**：召回 Top 100 与重排 Top 10 的漏斗设计。
- **5.3.2 解决“属性错位”难题**：详细阐述 Reranker 模型如何消除“黄衣黑车”与“黑衣黄车”的向量歧义。
- **5.3.3 严格相似度阈值截断 (Hard Threshold)**：实现“宁缺毋滥”的最终代码逻辑。

---

## 6. 数据存储与生命周期管理 (Data Storage & Lifecycle)

### 6.1 MySQL 核心元数据设计
- **6.1.1 ER 实体关系图说明**：设备表、告警事件表、VLM 结果记录表结构设计。
- **6.1.2 索引优化策略**：针对设备 ID 与时间戳的联合索引设计。

### 6.2 Elasticsearch 存储架构设计
- **6.2.1 Index 滚动与分片策略 (Rollover & Sharding)**：按天/按周建立时序索引，避免单点过大。
- **6.2.2 向量索引算法选择**：HNSW (Hierarchical Navigable Small World) 的内存占用与召回率权衡。

### 6.3 告警图片与数据的冷热分离与过期淘汰 (TTL)
- 基于用户云存套餐（如 7天/30天）的 OSS 图片删除与 ES 索引自动清理脚本。

---

## 7. 非功能性指标与高可用架构 (NFR & High Availability)

### 7.1 性能与算力成本评估 (Cost & Capacity Planning)
- **7.1.1 成本模型推演**：云端大模型 API vs. 私有化部署成本对比。
- **7.1.2 算力预估**：峰值并发下的 GPU 算力预估与 Auto-scaling 策略。

### 7.2 可靠性与容灾机制 (Reliability & Disaster Recovery)
- **7.2.1 消息队列防丢**：消息队列的防丢失机制 (At-least-once 语义)。
- **7.2.2 降级策略 (Circuit Breaker)**：LLM/VLM 服务限流、熔断与降级策略，模型挂掉时退化为传统检索。

### 7.3 系统监控与埋点告警 (Monitoring & Observability)
- **7.3.1 核心监控指标**：队列积压深度、大模型推理耗时、ES 查询 P99 延迟。
- **7.3.2 日志分析**：无结果查询 (Zero-result Query) 日志分析及用于后期持续优化同义词库和 VLM Prompt。

---

## 8. 项目实施路径与演进规划 (Implementation Plan)

### 8.1 Phase 1：核心骨架与概念验证 (PoC / MVP)
- 打通单语种链路，验证 VLM 提示词效果与 ES 纯英文 Hybrid 搜索准确度。

### 8.2 Phase 2：工程基建与压测 (Infrastructure & Load Testing)
- 引入 MQ、双写机制，完成全球化 LLM 意图解析的联调。进行突发流量压测。

### 8.3 Phase 3：精排优化与灰度发布 (Reranking & Beta Rollout)
- 引入 Cross-Encoder，微调相关性阈值。向部分高频云存用户进行 A/B 测试。
