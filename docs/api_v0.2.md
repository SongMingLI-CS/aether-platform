# Aether Platform API（V0.2）

> 自动生成的交互式文档：启动后端后访问 http://localhost:8080/swagger-ui.html（OpenAPI 源：`/v3/api-docs`）。
> 本文为 Markdown 版速查，覆盖全部端点、统一响应、错误码与 SSE 实时推送格式。

## 1. 约定

- **Base URL**：`/api/v1`（存活探测 `/api/ping` 除外）
- **Content-Type**：`application/json`（SSE 端点除外）
- **统一响应包**：所有成功/失败响应都套一层：

```json
{ "code": 0, "message": "OK", "data": { }, "timestamp": "2026-01-01T00:00:00Z" }
```

- `code = 0` 表示成功；非 0 时 `data` 为 `null`，错误码见 [§5 错误码](#5-错误码)。
- 时间统一为 **UTC** 的 ISO-8601 时间戳（`java.time.Instant`）。

## 2. 知识原子（Atom）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `POST` | `/api/v1/atoms` | 创建（Builder 模式，纯文本/Markdown） |
| `GET` | `/api/v1/atoms` | 分页查询（`contentType`、`keyword` 可选过滤） |
| `GET` | `/api/v1/atoms/{id}` | 详情 |
| `PATCH` | `/api/v1/atoms/{id}` | 更新（`contentText` / `contentType` 至少一项） |
| `DELETE` | `/api/v1/atoms/{id}` | 逻辑删除 |

`contentType` 枚举：`TEXT` / `MARKDOWN` / `IMAGE_URL`。

### 创建

```bash
curl -X POST http://localhost:8080/api/v1/atoms \
  -H 'Content-Type: application/json' \
  -d '{"contentText":"Spring Boot 微服务实践笔记","contentType":"MARKDOWN"}'
```

```json
{
  "code": 0, "message": "OK",
  "data": {
    "id": 1,
    "contentText": "Spring Boot 微服务实践笔记",
    "contentType": "MARKDOWN",
    "createdAt": "2026-09-05T09:00:00Z",
    "updatedAt": "2026-09-05T09:00:00Z",
    "version": 0
  }
}
```

### 分页查询

```bash
curl 'http://localhost:8080/api/v1/atoms?page=0&size=20&keyword=Spring&contentType=TEXT'
```

响应 `data` 结构：

```json
{ "totalElements": 42, "totalPages": 3, "page": 0, "size": 20, "content": [ /* AtomResponse... */ ] }
```

## 3. 连接发现（Connection）

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `GET` | `/api/v1/connections` | 分页查询（`status`、`minSimilarity` 可选过滤） |
| `GET` | `/api/v1/atoms/{id}/connections` | 某原子的所有连接 |
| `PATCH` | `/api/v1/connections/{id}` | 状态流转 |
| `GET` | `/api/v1/connections/stream` | 实时订阅（SSE） |

`status` 枚举：`PENDING`（AI 刚发现，待确认）/ `CONFIRMED`（已确认）/ `IGNORED`（已忽略）。

### 状态流转（PENDING → CONFIRMED / IGNORED）

只允许把 `PENDING` 流转为 `CONFIRMED` 或 `IGNORED`，**不允许**设回 `PENDING`。

```bash
curl -X PATCH http://localhost:8080/api/v1/connections/1 \
  -H 'Content-Type: application/json' -d '{"status":"CONFIRMED"}'
```

`ConnectionResponse` 结构：

```json
{
  "id": 1,
  "sourceAtomId": 2, "sourceText": "旧笔记片段…",
  "targetAtomId": 5, "targetText": "新笔记片段…",
  "similarity": 0.9213,
  "status": "CONFIRMED",
  "reason": "「…」与「…」内容语义相近，疑似相关",
  "createdAt": "2026-09-05T09:01:00Z"
}
```

> `sourceAtomId ≤ targetAtomId`（无向连接按 (min,max) 归一化存储，保证一对原子只有一条连接）。

## 4. SSE 实时推送

`GET /api/v1/connections/stream`（`text/event-stream`）。每次 AI 流水线发现新连接即推送一条事件：

```
event: connection-discovered
data: {"id":7,"sourceAtomId":2,"sourceText":"…","targetAtomId":9,"targetText":"…","similarity":0.88,"status":"PENDING","reason":"…","createdAt":"…"}

```

- 事件名固定为 `connection-discovered`，`data` 为 `ConnectionResponse` JSON（单行）。
- 长连接无超时；Electron 桌面端主进程订阅此流后弹出系统原生通知。

```bash
curl -N http://localhost:8080/api/v1/connections/stream
```

## 5. 错误码

| code | HTTP | 含义 |
| :--- | :--- | :--- |
| `40000` | 400 | 参数错误 / 校验失败 / 非法状态流转 |
| `40400` | 404 | 资源不存在 |
| `40500` | 405 | 请求方法不支持 |
| `40900` | 409 | 乐观锁版本冲突（并发修改） |
| `50000` | 500 | 系统内部错误 |

## 6. 系统

| 方法 | 路径 | 说明 |
| :--- | :--- | :--- |
| `GET` | `/api/ping` | 存活探测，返回 `{"status":"Aether Core-Zero Online"}` |
