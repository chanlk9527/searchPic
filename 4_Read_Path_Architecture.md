# 读取流：查询理解与意图解析子系统 (Read Path - QU) 技术设计文档

## 1. 概述
查询理解（Query Understanding, 简称 QU）是连接用户与底层搜索引擎的核心桥梁。在真实安防场景中，用户往往使用带有强烈口语化色彩、多语言混合的模糊长句进行搜索（如："帮我找一下上周五晚上在前台附近出现的红色电动车"）。
QU 子系统的核心职责是通过调用大语言模型（LLM）API，对用户的原始输入进行**意图清洗**、**时空标量提取**、**标准化翻译**与**同义扩充**，最终将其结构化为下游搜索引擎能够直接使用的硬过滤条件（时间范围、设备 ID）和标准化检索词（纯英文字符串及对应的稠密向量）。

---

## 2. 用户请求接入与预处理
前端应用层需要向后端的 QU 子系统提供丰富且准确的上下文信息，因为大语言模型本身无状态也无法感知物理世界的时间规律。

### 2.1 必选入参定义
每一次自然语言搜图请求到达网关后，必须包含以下基座数据供 LLM 消费：
- **`user_query` (String)**: 用户原始的自然输入短句/长句。
- **`user_timezone` (String)**: 用户的时区标识（如 `Asia/Shanghai`, `America/New_York`）。这对精准转化相对时间（如“昨天”）至关重要。
- **`current_timestamp` (Long)**: 提交查询瞬间的绝对 Unix 时间戳。辅助 LLM 充当计算“上周二”的基准锚点。
- **`device_context` (List)**: 用户账号下所有绑定的 IPC 列表信息（包含 `camera_id` 与用户为设备自定义的面糊别名，如“后院”、“车库”）。这用于设备空间范围的自动映射。
- **`subscription_retention_days` (Integer)**: 用户当前的云存套餐允许溯源的历史天数上限（如 `7` 或 `30`）。它的引入是为了严谨对齐端云生命周期，解决 App 本地深缓存与云端轻量化索引的存活期不对等问题。

---

## 3. 基于 LLM 的查询重写引擎 (Query Rewriting Engine)
在此模块，系统利用如 OpenAI GPT-4o 或 Claude 等第三方 LLM 强大的推理与自然语言理解能力完成结构化拆解。

### 3.1 相对时间绝对化提取 (Time Anchoring)
用户可能在搜索时包含相对时间（如“昨天晚上”），也可能完全不输入任何时间（如“找一下红色电动车”）。
- **实现方案**: 
  - 在交给 LLM 的 System Prompt 中动态注入当前的精确日期与时区。
  - **有时间意图时**：强制 LLM 解析提取出开始时间与结束时间（例如提取出 "2024-05-15T18:00:00" ~ "2024-05-15T23:59:59"），系统将其转换为 Unix `start_time` / `end_time`。
  - **无时间意图时**：如果 LLM 未能在用户输入中提取出明确时间，系统将默认应用一个滚动的安全时间窗口策略（例如默认检索过去 3 天或 7 天内的事件），以此保障底层 Elasticsearch 并不会执行高成本且危险的全表扫描。

### 3.2 空间范围识别与映射
用户的输入中可能包含确切位置描述（如“车库是不是进狗了”），也可能不包含任何地址信息。此外，部分用户账号下原本就仅绑定了一台 IPC 设备。
- **实现方案**:
  - **单设备用户直通**：在请求发起前的应用网关及业务层进行判断，如果该用户账号目前只绑定了 1 台设备，那么无需浪费 token 去调用 LLM 进行空间意图推断，直接将该 `camera_id` 设为底层的强制过滤项。
  - **多设备联合推理**：若用户有多台设备，Prompt 会向大模型提供设备名称与别名的映射数组（如 `[{"id":"cam_1","name":"前台"}, {"id":"cam_2","name":"车库"}]`）。LLM 分析搜索句意图并输出命中的 `camera_ids : ["cam_2"]`。
  - **无明确空间意图**：若输入未指定地点或推断失败，后端逻辑将搜索范围强制平展为**当前用户账号下的所有联机可用设备**进行全域召回查询。

### 3.3 多语言强制英译与扩充 (Pivot Language & Expansion)
由于数据库底层的图片打标结果永远是标准英文，因此来自全球用户的任意母语意图必须被翻译处理。
- **实现方案**: 
  - LLM 在拆分出安防实体后，将其翻译为标准纯英文，并自动带上核心同义词，丢弃无用的停用词语。
  - *举例*：“红色电动车” -> 翻译为 `["red e-bike", "red electric scooter", "red electric bicycle"]`。

### 3.4 核心 Prompt 与输入输出示例 (Prompt Engineering & Examples)
为了一次性且低延迟地完成上述“时空提取”与“语义翻译扩充”，系统会在请求第三方 LLM 时动态拼接所有上下文信息，要求得到结构化的 JSON 返回。

- **System Prompt 模板示例**:
  ```text
  You are an expert security surveillance query analyzer.
  Current Context:
  - Current Time: {{current_timestamp_formatted}}
  - Timezone: {{user_timezone}}
  - Available Cameras: {{device_context_json}}
  
  Your task is to parse the user's search query and extract:
  1. "start_time" and "end_time": In ISO 8601 format (e.g., "2024-05-15T18:00:00Z"). Based on the relative time mentioned. If no time concept is presented, explicitly return null.
  2. "camera_ids": An array of matched camera IDs based on locations mentioned. If no location is mentioned, return an empty array [].
  3. "search_terms": Extract core visual security entities (appearance, clothing colors, vehicle types), translate them into standard English, and expand with 1-2 common synonyms. Output as an array of strings. Drop conversational stop words.
  4. "search_intent_caption": A concise, natural English sentence summarizing the core action, relationship, and overall context in the user's query. This must preserve any verb-object causality (e.g., "A stray cat is wandering into the garage").
  
  Output strictly in JSON format matching the keys "start_time", "end_time", "camera_ids", "search_terms", and "search_intent_caption". Do not include any explanations or markdown formatting blocks.
  ```

- **真实交互流示例 (Payload & Response)**:
  - **User Query**: `"车库这两天是不是进野猫了？"` (日语/英语等异构语言同样适用此链路)
  - **动态注入数据**:
    - `user_timezone`: `"Asia/Shanghai"`
    - `current_timestamp_formatted`: `"2024-05-15T10:00:00+08:00"`
    - `device_context_json`: `[{"id":"cam_1","name":"前台"}, {"id":"cam_2","name":"车库"}]`
  - **LLM API 最终返回的 JSON 结构体**:
    ```json
    {
      "start_time": "2024-05-13T00:00:00+08:00",
      "end_time": "2024-05-15T23:59:59+08:00",
      "camera_ids": ["cam_2"],
      "search_terms": ["stray cat", "feral cat", "wild cat", "cat"],
      "search_intent_caption": "A stray cat is wandering into the garage or lingering around."
    }
    ```

### 3.5 LLM 异常兜底策略 (Fallback Mechanism)
由于我们已把“未输入时间”和“未输入地点”等作为正向的常规业务逻辑妥善流转，因此“高可用兜底”主要聚焦在第三方大模型发生了诸如网络超时、调用限流（Rate Limit）、及严重违反格式化输出等核心故障事件的应对。
系统必须具备健壮的无损降级方案：
- **模型格式输出失控降级**：当 LLM 回参无法被 JSON 序列化解析时，直接舍弃报错，取当前名下所有设备列表及默认的近 3 天时间窗口为基础过滤项。
- **API 完全宕机降级**：当第三方大模型服务彻底挂掉时，系统可调用其它云厂商预置的极简免费翻译 API ，拿着硬翻译得到的小学生英文词汇集合，退化执行纯文本匹配的全词短句检索（放弃高级的语义和向量特征匹配打分），来保障用户的搜图动作依然可用，不至于全局瘫痪白屏报错。

### 3.6 端云数据生命周期不对齐的截断与体验补偿 (Lifecycle Discrepancy Handling)
这是一个极具价值的业务架构考量：用户的 App 本地可能缓存了长达半年的文本告警消息。但受限于高昂的云存储与向量搜索引擎极高的内存成本，云端的图片特征库生命周期（TTL）通常强制与用户的增值服务套餐强绑定（如最多仅保存 7天 或 30天）。若任由用户搜索古老记录，会产生“本地明明看得到，搜却搜不到”的严重系统撕裂感。
- **实现方案**: 
  - **基于套餐订阅的硬向截断**：QU 模块在取得 LLM 结算出的 `start_time` / `end_time` 后，必须将其与用户当前的 `subscription_retention_days` 计算出的最长容忍点（如 `current_time - 30 days`）做交集处理。底层检索时间的起始点绝不能早于套餐所允许的边界。
  - **检索短路防护 (Short-circuit)**：如果 LLM 解析出的时间范围**完全早于**云端允许的生存周期（例如普通用户只有 7天 套餐，却指名道姓搜索“半个月前的前台录像”），后端业务层需直接**短路拦截**，切断向 Elasticsearch 发起请求的动作，节省检索资源的无效开销。
  - **UI 体验补偿与引导 (API Response)**：对于上述发生“时间被部分强行截断”或“查询被完全短路”的场景，后端 API 应在返回结构体中附带特定的越界警戒码（如 `errorCode: E_OUT_OF_RETENTION_PLAN`）。前端 App 捕获此码后可弹出恰当的友好提示（例如：“您的当前套餐仅支持检索过去 30 天的录像，更早的记录暂不提供智能检索。您可以点击 [升级高级套餐] 解锁更长历史”），从而成功将架构缺陷劣势扭转为了极其合理的 **增值转化契机**。

---

## 4. 搜索词特征向量化与下推
从 LLM 收到的这组干净、标准化的强意图检索实体，将进入最后一步“向量化”，以准备组合成 DSL 发往 Elasticsearch。

### 4.1 核心 Embedding 流程
- 获取到 LLM 输出的**全局搜索意图长句**（即刚才提取出的 `search_intent_caption`，例如 `"A stray cat is wandering into the garage or lingering around."`）。
- 调用与“写入流”模块**完全一致**的 Embedding 模型（必须是同一个计算权重的模型，如使用同一个开源的轻量级句子向量模型 / 相同的第三方 Embedding API），来对这个长句进行编码。
- 拿到该长句意图的浮点稠密向量（Dense Vector，例如 `[0.123, -0.456, 0.789...]`）。
- 最终，QU 子系统将把：①时间范围标量 ②设备列表标量 ③标准英文搜索词数组 (BM25专用) ④搜索意图长句生成的向量 (向量空间打分专用)，组合封包，移交给后端的混合检索引擎层进行召回。
