# 写入流：智能图像结构化与打标子系统 (Write Path) 技术设计文档

## 1. 概述
写入流子系统负责接收来自海量 IPC（智能网络摄像机）设备的实时图片告警，通过异步队列进行流量削峰，随后调度核心服务调用第三方 VLM（视觉语言模型）大模型 API 进行图像语义解析和特征提取，最终将结构化特征向量与图像元数据持久化至底层数据库（MySQL 与 Elasticsearch），为后续的自然限语言检索构建底层基础。

---

## 2. 告警接入与网关层 (Ingestion Gateway)

网关层是系统的入口，负责鉴权、限流以及告警元数据的初步封装。

### 2.1 协议与鉴权机制
- **接入协议**：使用 HTTPS/TLS 以保证传输安全性。网关应支持轻量级的 HTTP POST 请求。
- **设备鉴权**：
  - 基于 JWT (JSON Web Token) 或 Token Auth（如 `X-Device-Token`），验证设备身份合法性。
  - Token 中需包含设备注册时下发的 `tenant_id` 与 `camera_id`，防止越权伪造告警。
- **频率限制 (Rate Limiting)**：
  - **单机限流**：针对单个 `camera_id` 实施令牌桶或漏桶算法，防止单台设备异常（如硬件损坏疯狂上报）导致系统过载。例如：限制同一台设备最多 1 帧/秒。

### 2.2 极简脱水透传策略
为了保证网关层具有极高吞吐量与极低延迟，网关不进行任何复杂的业务逻辑处理（如图片下载或识别）。
它仅仅充当元数据封装器：
- 接收设备上传图片后，网关仅从云存储（OSS/S3）获取预先上传好的 `image_url`，结合 HTTP 请求头中的安全认证信息，组装标准的 JSON 载荷发往消息队列。
- **核心封装字段示例**：
  ```json
  {
    "event_id": "uuid-v4",
    "camera_id": "cam_893A4F",
    "timestamp": 1715423800,
    "image_url": "https://oss.example.com/alerts/cam_893A4F/1715423800.jpg",
    "trigger_type": "motion_detect"
  }
  ```

---

## 3. 异步削峰与消息队列设计 (Message Queue)

针对安防场景下极度不稳定的流量特征，系统必须采用消息队列（如 Apache Kafka 或 RocketMQ）实现彻底的异步解耦。

### 3.1 流量特征分析与削峰
- **突发高并发应对**：在雷阵雨、大风引起的树影摇晃等自然现象下，整个城市的室外摄像头可能同时触发大量移动侦测告警。
- **削峰填谷**：MQ 的引入确保了网关层在接收巨额请求时不会直接由于第三方大模型 API 的并发限制或响应瓶颈而导致系统过载，所有的告警事件都会在 MQ 中暂存，由后端工作节点根据 API 的限流规则平滑消费。

### 3.2 Topic 与 Partition 路由策略
- **哈希路由**：为了确保同一台摄像机的告警能够严格按照时间顺序被处理，MQ 生产者在发送消息时，必须以 `camera_id` 作为分发 Key (Partition Key)。
- **顺序一致性**：这样相同设备的图片时序会路由到同一个 Partition，从而避免出现设备“12:00的照片结果比11:59的照片先入库”的倒挂乱序情况。

---

## 4. VLM 图像解析工作节点 (Third-party API Request Node)

这是系统核心的语义处理组件，负责向第三方视觉大模型发起请求进行图片解析。

### 4.1 消费模型与并发控制
- **拉取消费**：工作节点作为 MQ 消费者，主动根据当前系统的并发配额拉取消息。
- **并发控制与限流控制 (Rate Limiting)**：由于第三方大模型 API 会严格限制 TPM（Token Per Minute）和 RPM（Request Per Minute），这里需要实现健壮的流控和重试策略（如 Exponential Backoff）。
- 为了提升吞吐量并尽量控制调用成本，可采用批量请求 API（若第三方支持），或发起异步的独立并发请求。

### 4.2 VLM System Prompt 工程设计
由于下游搜索基底要求使用全英文，模型的输出必须高度结构化，并且要从根本上“抑制幻觉”。

- **终极 Prompt 模板**：
  ```text
  You are an expert security surveillance AI. Extract key attributes from the provided image.
  STRICT RULES:
  1. ONLY describe what is CLEARLY visible. Do NOT guess or hallucinate.
  2. If an object is blurry, partial, or missing colors, omit its details.
  3. Output strictly in English JSON format without any markdown wrappers or extra text.

  Analyze the following aspects thoroughly:
  - environment: (e.g., daytime, nighttime, indoor, outdoor, raining, snowing)
  - persons: list of objects containing {"clothing_color", "accessories", "action", "carried_items"}. Example actions: walking, running, lingering, falling. Example carried_items: backpack, handbag, umbrella.
  - vehicles: list of objects containing {"type", "color", "details"}. Example types: car, truck, e-bike, scooter.
  - animals: list of objects containing {"species", "color"}. Example species: dog, cat, bird.
  - packages_and_baggage: list of objects representing standalone unattended items such as delivery parcels, boxes, or forgotten luggage containing {"type", "color", "shape"}. Example types: cardboard box, plastic mail bag, suitcase.
  - physical_security_events: list of observable status relating to facility integrity containing {"event", "details"}. Example events: door left open, window broken, gate closed.
  - fire_and_smoke: list of hazard triggers containing {"type", "intensity"}. Example types: smoke, open flame.
  - scene_caption: a comprehensive natural language summary (1-2 sentences) describing the holistic scene. It MUST capture the precise spatial relationships and causal interactions between the entities (e.g., "A man in a red jacket is throwing a rock at the window of a black car").

  JSON Output:
  ```

- **VLM 解析结果 JSON 示例**:
  在真实的监控画面下（例如：白天一处庭院监控拍到一名穿灰色卫衣的快递员抱着一个棕色纸箱走向门口，并且庭院里有一只白猫走过），VLM 会精准提取以下纯英文更丰富的维度特征：
  ```json
  {
    "environment": "daytime, outdoor, clear weather",
    "persons": [
      {
        "clothing_color": "grey hoodie, dark pants",
        "accessories": "cap",
        "action": "walking towards the door, carrying a package",
        "carried_items": "brown cardboard box"
      }
    ],
    "vehicles": [],
    "animals": [
      {
        "species": "cat",
        "color": "white"
      }
    ],
    "packages_and_baggage": [
      {
        "type": "cardboard box, parcel",
        "color": "brown",
        "shape": "rectangular"
      }
    ],
    "physical_security_events": [
      {
        "event": "gate open",
        "details": "the front garden gate is slightly open"
      }
    ],
    "fire_and_smoke": [],
    "scene_caption": "A delivery courier in a grey hoodie is walking across the courtyard carrying a brown cardboard box towards the slightly open front gate, while a white cat is walking nearby."
  }
  ```
  
  **重要重构解说：为什么必须要加入 `scene_caption`？**
  如果您仔细观察，前面的几个数组（如 `persons`, `vehicles`）就像是“切碎”的积木名词。这就掩藏了一个致命问题：**丢失了由于画面元素互动而产生的复杂语义（Scene Context & Relationship）**。
  - *反面例子*：如果只是提取名词，当画面出现一个男人、一辆红车被砸破了玻璃。底层的标签库会记录：`男人`、`红车`、`破窗`。但这三者有联系吗？是男人在砸车，还是他恰好路过一辆被人砸坏的红车进行围观？
  - *正面例子*：通过显式地要求大模型给出一句完整的 `"scene_caption": "A man is smashing the window of a red car with a tool"`，我们把**空间关系**（在车顶还是旁边）、**因果互动**（砸窗）、**动宾结构**都原封不动地保留了下来。这段话在转化为 Embedding 稠密向量后，将能在用户搜索“有人砸车玻璃”或者“有只猫趴在车顶上”这类复杂关联句时，爆发出无与伦比的召回精度！

  *注：最终存入 ES 引擎时，结构化的“名词词根”（保证诸如颜色的绝对过滤约束）和这段“全局关系长句”（提供稠密语义向量）将被组合起来双管齐下。*

### 4.3 异常图片拦截机制
为避免向第三方接口发送无效图片浪费 API 调用额度，在发送云端请求前需加入轻量的传统视觉过滤：
- **前置过滤算法**：利用 OpenCV 等轻量库进行快速判定。
- 遇到 **纯黑/严重夜视不可见**（基于像素方差和均值极低）、**全白曝光**（均值极高）、或 **严重破坏/镜头遮挡** 时，直接结束当前告警流，丢弃废片记录。

---

## 5. 特征向量化与数据双写 (Data Sink)

解析出的英文 JSON 结构需要经过向量化转化，并安全落盘。

### 5.1 文本 Embedding 提取
从 VLM 获取到结构化的英文字符串（描述特征）后，调用轻量级的文本向量模型（如 `all-MiniLM-L6-v2`）。
- **流程**：将 JSON 或拼装后的长句文本转化为 384/768 维的高维稠密向量 (Dense Vector)，该动作可以选择调用第三方的 Embedding API ，或者在本地使用极低资源即可完成的轻量模型，无需占用大量算力资源。

### 5.2 MySQL 与 ES 的双写一致性保障
持久化阶段需同时写入关系型数据库（存储结构化元数据留底）和检索引擎（供快速混合搜索）。

- **策略 A：基于 MQ 的一致性 (推荐)**  
  数据入库也作为消费者逻辑一部分。只有 MySQL 成功保存结构化数据，且 Elasticsearch 成功建完特征和向量的索引后，消费者才向消息队列提交 ACK (Acknowledge) 确认消费完毕。若其一失败则重试。
- **策略 B：基于 Binlog 的最终一致性 (解耦更为彻底，适用于高并发大集群)**  
  VLM 服务仅将数据写入 MySQL。系统部署一套 Canal 或 Debezium，自动监听 MySQL 的 Binlog 增量变化，随后由独立的同步进程稳健地同步至 Elasticsearch 进行索引建立。保障两个库最终状态完全一致。
