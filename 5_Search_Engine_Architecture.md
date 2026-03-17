# 核心检索引擎设计 (Search Engine Layer) 技术设计文档

## 1. 概述
在安防自然语言意图搜图系统中，核心检索引擎（基于 Elasticsearch 8.x+）是最关键的底层基座。它需要融合前面“写入流”结构化提取出的多维度实体对象（人、车、物、动作）以及“全局意图段落（`scene_caption`）”，结合“读取流”LLM 翻译转化后的时空边界与查询意图，执行**超大规模下的高精度混合检索（Hybrid Search）**。
本系统的核心追求是**“宁缺毋滥（Exact Match 首位）”**：严防传统向量检索（Vector Search）带来的“属性错位幻觉”，确保搜“黄衣黑车”绝不会返回“黑衣黄车”。

---

## 2. Elasticsearch 索引结构与分词器配置 (Index Mapping & Settings)
为了保障纯英文底层的绝对纯净与高召回准确率，Elasticsearch 的索引规划至关重要。

### 2.1 深度定制的 English Analyzer
系统统一使用英语作为引擎基底 (Pivot Language)。但常规的英文分词器不够用，必须进行以下定制：
- **词根还原 (Stemmer)**：将动词变体（如 `running`, `ran`）和复数（`cars`, `boxes`）统一缩减为基本词根（`run`, `car`, `box`），防止同一个单词的不同形态导致漏搜。
- **自定义停用词过滤 (Stopwords)**：移除 `a`, `the`, `is` 等无意义的语法词，只保留对安防搜索起决定性作用的名词和形容词。

### 2.2 安防领域同义词字典库 (Synonym Graph)
为了弥补 LLM 偶尔同义词扩充的遗漏，建立一份长期积累的行业级 Synonym Filter。
- **配置示例**：
  ```
  "scooter, e-bike, electric bicycle, moped"
  "dog, puppy, hound"
  "package, parcel, cardboard box, mail bag"
  ```
  这样当用户搜索 `scooter` 时，倒排索引底层可以直接命中被打标为 `e-bike` 的图片记录。

### 2.3 数据结构映射 (Index Mapping)
写入流的纯英文 JSON 在入库时的核心 Mapping 数据结构如下：
```json
{
  "mappings": {
    "properties": {
      "event_id": { "type": "keyword", "index": false },
      "image_url": { "type": "keyword", "index": false },
      "timestamp": { "type": "date", "format": "epoch_second" },
      "camera_id": { "type": "keyword" },
      // 离散实体属性，使用定制 analyzer 以便 BM25 词频计算得分
      "entities_text": { 
        "type": "text", 
        "analyzer": "custom_english_analyzer" 
      },
      // scene_caption 对应的稠密向量，用于语义意图捕捉
      "caption_vector": { 
        "type": "dense_vector", 
        "dims": 768, 
        "index": true, 
        "similarity": "cosine" 
      }
    }
  }
}
```
*注：我们在业务层会将 `persons`, `vehicles`, `animals` 等实体 JSON 拍平合并成一段长文本 `entities_text` 用于全文倒排索引检索；而 `scene_caption` 则被转化为 `caption_vector`。*

---

## 3. 混合检索查询构建 (Hybrid Search DSL Construction)
单纯靠“名词词频（BM25）”无法理解动作和因果，单纯靠“向量匹配（k-NN）”容易无视具体的衣服颜色（颜色在全局句向量中的权重极低）。因此，两者必须融为一体。

### 3.1 物理前置标量硬过滤 (Pre-filtering)
这是整个检索的第一步，也是保护计算资源最重要的一步。
利用读取流 LLM 提取出的 `camera_ids` 与 `start_time` / `end_time`：
- **查询结构**：在 DSL 中使用 `filter` 子句。
- **效果**：无论后续的语义匹配多复杂，引擎首先会在瞬间把不属于这台 IPC、或者不在这个 3 天时间窗口里的几十上百万张垃圾图片记录全部物理截断（不参与打分）。

### 3.2 组合召回打分机制 (Lexical + Semantic)
在这批已被圈定时间与地点的告警记录池中：
1. **BM25 词频检索 (Sparse)**：拿 QU 子系统传入的 `search_terms`（也就是 `"stray cat, feral cat"` 这样的分词数组）去找底层图片的 `entities_text`。这保证了实体单词的精确命中，尤其是用户如果在乎“红色（Red）”，那画面里必须真真实实存在“Red”，实现了刚性约束。
2. **K-NN 向量检索 (Dense)**：拿 QU 读取流大模型输出的代表完整上下关逻辑的 **`search_intent_caption` 向量**，去最近邻搜索（k-NN）图片底库中写入的大段 **`caption_vector`** 层。这也是整个系统设计的精妙所在：两个从长句（动作、主谓宾结构）被 Embedding 的向量能在空间中高度相似，这能立刻捕捉到被切碎名词所丢失的复杂行为语义（例如“拿着箱子跑入车库”的动作关系）。
3. **融合分数函数 (RRF - Reciprocal Rank Fusion) 或 Alpha Weighting**：
   将 BM25 的得分与 kNN 余弦相似度的得分进行线性加权。在安防诉求下，通常会让 BM25 略占上风（例如 `Alpha = 0.6` 给予文本匹配，`0.4` 给予向量），因为用户在这类场景通常对**颜色和物种极其敏感**。如果颜色都对不上，语义再相似也不行。

---

## 4. 重排与极速截断机制 (Re-ranking) [核心防线]
到这一步，系统通常会召回分数最高的 Top 100 张图片。但面对安防中极其恶心的**“属性错位幻觉”**（用户搜：黄衣服男人和黑车，召回却是：黑衣服男人和黄车，因为两者在 BM25 词频和普通句向量里得分几乎一样），我们必须引入 Cross-Encoder 机制的重排序。

### 4.1 二阶段级联打分架构
- **一阶段召回 (Recall)**：上文提到的 Elasticsearch DSL 混合检索，快速从百万级图片池捞出最相关的 100 条候选结果。
- **二阶段重排 (Re-ranking)**：将 QU 模块翻译出的标准英文搜索长句 **`search_intent_caption`** 与这 100 条召回底库的完整英文 **`scene_caption`** 文本，组装成查询文本对 (Text-Pair)，交给专门用于判断文本相关性的第三方 **Reranker 模型 API**（如 BGE-Reranker、Cohere Rerank 接口等）。

### 4.2 解决“属性错位”难题
Reranker（交互式编码器）的原理与普通的 Embedding 不同，它不是把两句话变成两个向量去比对，而是直接拿两句话一起扔进大模型进行逐字注意力比对。
它能一眼看穿：用户要找的黄色绑定的是衬衫，黑色绑定的是车；而底库里的黄绑定的是车。因此立刻将其相关度打为 0.1 分，而真正完全匹配的打为 0.98 分。这从根本上肃清了自然语言搜图中的错位幻觉。

### 4.3 严格相似度阈值截断 (Hard Threshold)
重排结束后，我们将得到几十张打了绝对分数的记录：
- 在常规推荐系统中，展示前 5 张就可以了。
- 但在**严肃安防排查**中，我们代码中会设置一个硬性拦截阈值（例如 `min_relevance_score = 0.85`）。
- **宁缺毋滥**：如果有结果超过 0.85，就算只有一张，我们也光明正大地反馈给用户这一张。如果没有哪怕一张越过雷池，后端直接返回空列表（并在 App 提示：“经过 AI 排查，您的监控期间未发现任何高度相似目标”），**坚决拒绝给用户凑数瞎推荐无关画面**，这是建立用户 AI 信任度的底线。
