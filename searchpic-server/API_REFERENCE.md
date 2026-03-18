# SearchPic SaaS 接口白皮书

欢迎使用 SearchPic API。通过此接口，您的应用可以方便地提交安防监控图像，并通过自然语言进行语义与视觉的混合搜索。

## 接口访问地址 (Base URL)
所有的 API 请求都需要带上前缀，例如：`https://api.searchpic.com/v1`

## 鉴权机制 (Authentication)
每一个请求都必须在 `Authorization` 请求头中携带您的租户 API Key。
```http
Authorization: Bearer sk-your-tenant-api-key-here
```

## 0. 素材上传预接口 (Image Upload)
提供一张本地监控画面的直接上传通道。如告警或图库系统本身没有基于公网的图片链接（CDN/OSS URL），可先用本接口完成图像托管。

- **URL:** `/storage/upload`
- **请求方式:** `POST`
- **Content-Type:** `multipart/form-data`

### 请求参数
| 字段名称 | 类型 | 是否必填 | 描述 |
|-------|------|----------|-------------|
| `file` | File | 是 | 要上传的图片二进制文流（.jpg / .png） |

### 响应数据 (Response)
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "url": "http://localhost:9000/searchpic-bucket/tenant_id/uuid.jpg"
  }
}
```
*获得该 public URL 后，可作为后续图文索引入库 `image_url` 字段的值。*

---

## 1. 图像入库与分析接口 (Image Ingestion)
此入口用于提交安防监控图片。后台将自动对接 VLM 大模型和 Embedding 向量萃取进行处理。

- **URL:** `/events/ingest`
- **请求方式:** `POST`
- **Content-Type:** `application/json`

### 请求参数 (Request Body)
| 字段名称 | 类型 | 是否必填 | 描述 |
|-------|------|----------|-------------|
| `event_id` | String | 是 | 贵司内部生成的主键/唯一标识，用于该次事件告警。 |
| `camera_id` | String | 是 | 物理摄像机 ID，可用于搜索时的地区过滤。 |
| `timestamp` | Long | 是 | Epoch 毫秒时间戳，指该图片截图发生的时间。 |
| `image_url` | String | 是 | VLM 模型可公网访问的该告警图片 URL。 |

### 请求示例
```json
{
  "event_id": "evt_987654321",
  "camera_id": "cam_front_door_01",
  "timestamp": 1715423800000,
  "image_url": "https://oss.example.com/alerts/evt_987654321.jpg"
}
```

### 响应数据 (Response)
```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "status": "PROCESSING",
    "received_at": 1715423801200
  }
}
```
*注：考虑到大模型的耗时，写入接口为**异步操作**。您的图片会被置入 Kafka 中排队，并在后台完成所有特征计算与存盘。*

---

## 2. 自然语言意图搜索接口 (Natural Language Search)
向数据库发送自然语言指令（中文/英文皆可）进行多模态搜索。

- **URL:** `/search/query`
- **请求方式:** `POST`
- **Content-Type:** `application/json`

### 请求参数 (Request Body)
| 字段名称 | 类型 | 是否必填 | 描述 |
|-------|------|----------|-------------|
| `query` | String | 是 | 用户侧口语化的查找意图（例如："昨天下午有谁穿红衣服在门口拿包裹"）。 |
| `timezone` | String | 是 | 用户所在地的 IANA 时区（如 "Asia/Shanghai"），由于用户的意图常带有相对时间词，需用于对齐时间窗口。 |
| `camera_ids` | Array(String) | 否 | 选填；如有明确指定的排查摄像头列表，请传入对应数组以提高查询精度。 |

### 请求示例
```json
{
  "query": "昨天下午有谁在门口拿走了我的快递贴纸？",
  "timezone": "Asia/Shanghai",
  "camera_ids": ["cam_front_door_01", "cam_garage_02"]
}
```

### 响应数据 (Response)
返回一个经过相关度（Relevance Score）打分并排序的结果集。

```json
{
  "code": 200,
  "message": "成功",
  "data": {
    "results": [
      {
        "event_id": "evt_987654321",
        "camera_id": "cam_front_door_01",
        "timestamp": 1715423800000,
        "image_url": "https://oss.example.com/alerts/evt_987654321.jpg",
        "relevance_score": 0.92
      }
    ]
  }
}
```
*注：返回的结果即为最贴合您语义描述的监控画面集合。如果没有结果越过严格的分数阈值设定，`results` 数组将会为空。*
