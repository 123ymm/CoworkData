# CoworkData Persistence API 接口文档

## 0. 工程包结构（Spring Boot + MyBatis）

```
com.huawei.coworkdata
├── controller/     # REST 接口层，接收 HTTP 请求
├── service/        # 业务接口（XxxService）
│   └── impl/       # 业务实现（XxxServiceImpl）
├── mapper/         # 数据访问层（MyBatis-Plus Mapper）
├── entity/         # 数据库表实体
├── dto/            # 请求/响应对象
├── config/         # Spring / MyBatis 配置
└── util/           # 工具类
```

调用链：`Controller` → `Service`（接口）→ `ServiceImpl` → `Mapper` → PostgreSQL

## 1. 概述

**CoworkData 是一个数据持久化服务。**

客户端（地端 cowork、Host 或其他服务）调用本服务暴露的 HTTP 接口，完成数据上传、查询回放与持久化入库；数据落在 PostgreSQL（会话投影、领域事件、SSE 历史、快照等）。

能力来源上对齐 `IpMasterCoworkPy` 的 `persistence` 层对外方法，并以 REST 形式对外提供。

| 项 | 说明 |
|---|---|
| **服务定位** | 数据持久化服务（上传 / 入库 / 查询 / 回放） |
| **Base URL** | `http://localhost:8080`（未配置 `server.port`，Spring Boot 默认 8080） |
| **Content-Type** | `application/json`（除 GET 无 body 外） |
| **鉴权** | `/api/**` 当前 **免认证**（开发配置）；生产请加鉴权 |
| **对应 Python** | `ipmastercowork.persistence`（不含 `migrations.py`、`models.py`、`_` 私有方法） |
| **项目说明** | 见仓库根目录 [README.md](../README.md) |

---

## 2. 路由设计思路

统一前缀 `/api`（已去掉 `persistence`），按资源域分层：

```
/api/
├── sessions/              ← 会话同步 + 投影读写（SessionController）
├── events/                ← 事件落库 / 流水线 / 快照（EventPersistenceController）
├── event-persister/       ← EventPersister
├── projection-updater/    ← ProjectionUpdater
├── snapshot-writer/       ← SnapshotWriter
├── skill-reporter/        ← SkillReporter
├── reconcile/             ← reconcile_stranded_running_sessions
├── db/                    ← 库初始化
└── test/                  ← 连通性测试
```

设计原则：

1. **读写在路径上区分**：`GET` 查询、`POST` 创建/触发、`PUT` 更新、`DELETE` 软删。
2. **会话 ID 放路径**：`/sessions/{sessionId}/...`。
3. **会话全量事件回放**走 `GET /sessions/{sessionId}/events`（不再用 `/events/sessions/{id}`）。
4. **流水线**：`POST /events/pipeline` 一次完成落库 → 投影 → 快照。
5. **列表按用户**：`GET /sessions?userId=`（不再提供无条件全量列表）。

典型调用关系：

```text
Host / 前端 ──HTTP──▶ CoworkData REST
                         ├─ sessions 投影表
                         ├─ events 事件表
                         ├─ snapshots 快照表
                         └─ session_sse_events
```

---

## 3. 公共数据模型

### 3.1 EventDto（事件）

对应 Python `ctx_weft.core.events.Event`，用于写事件、流水线、订阅器。

```json
{
  "id": "01HXXX...",
  "runId": "run_abc",
  "sequence": 42,
  "sessionId": "ses_xxx",
  "type": "SessionCreated",
  "timestamp": "2026-08-24T12:00:00+08:00",
  "tenantId": "default",
  "taskId": "tsk_xxx",
  "agentId": "agt_xxx",
  "payload": { "user_prompt": "你好" },
  "metadata": {},
  "causationId": null
}
```

常见 `type`：`SessionCreated`、`SessionFinished`、`TaskCreated`、`TaskFinished`、`RunFinished` 等（与 Python `EventType` 字符串一致）。

### 3.2 SessionRecordDto（会话投影）

对应 Python `SessionRecord`。

```json
{
  "id": "ses_xxx",
  "tenantId": "default",
  "userId": "zhangsan",
  "userPrompt": "用户首条输入",
  "status": "RUNNING",
  "goal": "",
  "rootAgentId": "agt_root",
  "llmProvider": "openai",
  "llmModel": "gpt-4",
  "tokenBudget": 200000,
  "failureCounter": 0,
  "config": { "template_id": "tpl_xxx" },
  "workspace": "/path/to/workspace",
  "lastUploadIndex": 0,
  "deleteAt": null,
  "createdAt": "2026-08-24T12:00:00+08:00",
  "updatedAt": "2026-08-24T12:00:00+08:00"
}
```

合法 `status`：`RUNNING`、`SUCCEEDED`、`FAILED`、`CANCELED`、`INTERRUPTED`、`PAUSED_HITL`、`PAUSED`。

- `lastUploadIndex`：地端 cowork 上次上传到的进度索引，默认 `0`
- `deleteAt`：软删时间；`null` 表示未删除。列表/查询自动过滤已软删行

### 3.3 RunSnapshotDto（运行快照）

对应 Python `RunSnapshot`。

```json
{
  "id": "snp_xxx",
  "runId": "run_abc",
  "sessionId": "ses_xxx",
  "lastEventId": "01HXXX...",
  "lastEventSequence": 42,
  "stateBlob": { "session_id": "ses_xxx" },
  "snapshotReason": "periodic",
  "snapshotAt": "2026-08-24T12:00:00+08:00"
}
```

### 3.4 Task 字典（load_tasks 返回）

前端形态，含 `is_daemon`（调用方展示前应 `pop` 掉）。

```json
{
  "id": "tsk_xxx",
  "session_id": "ses_xxx",
  "status": "ACTIVE",
  "title": "任务标题",
  "description": "",
  "user_prompt": "",
  "assigned_agent_id": "agt_xxx",
  "creator_agent_id": "agt_xxx",
  "settings": {},
  "result": null,
  "outputs": null,
  "error": null,
  "created_at": "2026-08-24T12:00:00+08:00",
  "updated_at": "2026-08-24T12:00:00+08:00",
  "is_daemon": false
}
```

### 3.5 其他请求体

**AppendSseEventRequest**

```json
{ "eventJson": "{\"type\":\"message\",\"content\":\"hello\"}" }
```

**SaveWorkspaceRequest**

```json
{ "workspace": "D:/work/user_xxx/project" }
```

**UpdateSessionStatusRequest**

```json
{ "status": "INTERRUPTED" }
```

**SessionsStoreRequest**（SkillReporter 用户上下文）

```json
{
  "entries": [
    {
      "sessionId": "ses_abc123",
      "workspace": "D:/work/user_xxx",
      "userInfo": { "username": "zhangsan" }
    }
  ]
}
```

---

## 4. Session API（统一）

**Controller：** `SessionController`  
**前缀：** `/api/sessions`

地端同步与投影读写已合并为一套接口；冲突处以同步接口为准。

| 方法 | 路径 | 作用 |
|------|------|------|
| `GET` | `/api/sessions?userId={userId}` | 查某个人的全部会话列表（**必须**带 `userId`） |
| `GET` | `/api/sessions/active` | 投影表中 `status=RUNNING` 的 session id |
| `GET` | `/api/sessions/{sessionId}` | 单条会话投影；不存在 → **404** |
| `GET` | `/api/sessions/{sessionId}/upload-watermark` | 查询上次上传水位 |
| `POST` | `/api/sessions/{sessionId}/upload` | 增量上传事件并推进水位 |
| `GET` | `/api/sessions/{sessionId}/events` | 会话回放：领域事件表 `events` 全量（按 id 升序） |
| `DELETE` | `/api/sessions/{sessionId}` | 软删（写 `delete_at`）；不存在 → **404**；**204** |
| `GET` | `/api/sessions/{sessionId}/tasks` | 该会话任务列表 |
| `POST` | `/api/sessions/{sessionId}/sse-events` | 追加前端 SSE 历史（表 `session_sse_events`）；**201** |
| `GET` | `/api/sessions/{sessionId}/sse-events` | 读取前端 SSE 历史（表 `session_sse_events`，非领域 `events`） |
| `PUT` | `/api/sessions/{sessionId}/workspace` | 写入 workspace |
| `GET` | `/api/sessions/{sessionId}/workspace` | 读取 workspace |
| `GET` | `/api/sessions/{sessionId}/status` | 读取 status |
| `PUT` | `/api/sessions/{sessionId}/status` | 更新 status |
| `PUT` | `/api/sessions/{sessionId}/last-upload-index` | 直接设置上传水位（调试/补偿用） |

> **`events` vs `sse-events`**：`/events` 读写领域事件表 `events`（恢复/回放真相源）；`/sse-events` 读写 `session_sse_events`（已翻译成前端协议的 SSE 消息历史，不含 text_delta 等）。二者不是同一张表。

### 调用示例

```bash
# 按用户列表
curl "http://localhost:8080/api/sessions?userId=zhangsan"

# 上传水位
curl http://localhost:8080/api/sessions/ses_abc123/upload-watermark

# 增量上传
curl -X POST http://localhost:8080/api/sessions/ses_abc123/upload \
  -H "Content-Type: application/json" \
  -d '{
    "userId": "zhangsan",
    "uploadIndex": 50,
    "events": [
      {
        "id": "01HABCDEF",
        "runId": "run_1",
        "sequence": 1,
        "sessionId": "ses_abc123",
        "type": "SessionCreated",
        "timestamp": "2026-08-24T12:00:00+08:00",
        "tenantId": "default",
        "payload": { "user_prompt": "你好", "user_id": "zhangsan" }
      }
    ]
  }'

# 回放全部事件
curl http://localhost:8080/api/sessions/ses_abc123/events

# 单条会话
curl http://localhost:8080/api/sessions/ses_abc123

# 任务 / workspace / status
curl http://localhost:8080/api/sessions/ses_abc123/tasks
curl -X PUT http://localhost:8080/api/sessions/ses_abc123/workspace \
  -H "Content-Type: application/json" \
  -d '{"workspace":"D:/work/user_xxx/project"}'
curl -X PUT http://localhost:8080/api/sessions/ses_abc123/status \
  -H "Content-Type: application/json" \
  -d '{"status":"INTERRUPTED"}'

# 软删
curl -X DELETE http://localhost:8080/api/sessions/ses_abc123
```

增量上传说明：

- `userId`：写入 `sessions.user_id`，供按人列表查询
- `uploadIndex`：新水位；省略则为「旧水位 + 新写入事件数」
- 事件按 `id` 幂等；`uploadIndex < 当前水位` → **409**
- 会话不存在时自动创建最小投影行

---

## 5. Event 与快照 API

**Controller：** `EventPersistenceController`  
**前缀：** `/api/events`

| 方法 | 路径 | 作用 |
|------|------|------|
| `POST` | `/api/events` | 仅追加一条事件；**201** |
| `POST` | `/api/events/pipeline` | 落库 + 投影 + 快照 |
| `GET` | `/api/events/sessions/{sessionId}/types?types=...` | 按类型过滤事件 |
| `GET` | `/api/events/active-session-ids` | 按事件日志判断仍 active 的 session |
| `GET` | `/api/events/last-activity-times` | 各 session 最后活动时间 |
| `GET` | `/api/events/sessions/{sessionId}/after/{afterEventId}` | 增量事件 |
| `POST` | `/api/events/sessions/{sessionId}/snapshots` | 写快照；**201** |
| `GET` | `/api/events/sessions/{sessionId}/snapshots/latest` | 最新快照 |

> 全量回放请用 `GET /api/sessions/{sessionId}/events`，不再提供 `GET /api/events/sessions/{sessionId}`。

### `last-activity-times` 查询参数

| 参数 | 必填 | 说明 |
|------|------|------|
| `excludeTypes` | 否 | 排除的事件类型；默认排除 `SessionStatusChanged`（避免 recovery 污染排序） |

### `pipeline` 做了什么

等价于依次调用：

1. `EventPersister.on_event` — 跳过瞬态事件，写入 `events`
2. `ProjectionUpdater.on_event` — 更新 `sessions` / `tasks` 投影
3. `SnapshotWriter.on_event` — `RunFinished` 周期性 / `SessionFinished` 时写快照

**Host 发事件的推荐路径：** `POST /api/events/pipeline`。

### 调用示例

```bash
# 仅落库
curl -X POST http://localhost:8080/api/events \
  -H "Content-Type: application/json" \
  -d '{
    "id": "01HABCDEF",
    "runId": "run_1",
    "sequence": 1,
    "sessionId": "ses_abc123",
    "type": "SessionCreated",
    "timestamp": "2026-08-24T12:00:00+08:00",
    "tenantId": "default",
    "payload": { "user_prompt": "你好", "tenant_id": "default" }
  }'

# 流水线（推荐）
curl -X POST http://localhost:8080/api/events/pipeline \
  -H "Content-Type: application/json" \
  -d @event.json

# 按类型读事件
curl "http://localhost:8080/api/events/sessions/ses_abc123/types?types=SessionFinished"

# 最后活动时间
curl "http://localhost:8080/api/events/last-activity-times"
curl "http://localhost:8080/api/events/last-activity-times?excludeTypes=SessionStatusChanged"

# 增量事件
curl http://localhost:8080/api/events/sessions/ses_abc123/after/01HABCDEF

# 最新快照
curl http://localhost:8080/api/events/sessions/ses_abc123/snapshots/latest

# 写入快照
curl -X POST http://localhost:8080/api/events/sessions/ses_abc123/snapshots \
  -H "Content-Type: application/json" \
  -d '{
    "id": "snp_xxx",
    "runId": "run_1",
    "lastEventId": "01HABCDEF",
    "lastEventSequence": 1,
    "stateBlob": { "session_id": "ses_abc123" },
    "snapshotReason": "manual",
    "snapshotAt": "2026-08-24T12:00:00+08:00"
  }'
```

---

## 6. 流水线 / 订阅器 API（EventBus 订阅者）

**Controller：** `PersistencePipelineController`  
**前缀：** `/api`

| 方法 | 路径 | Python | 作用 |
|------|------|--------|------|
| `POST` | `/api/event-persister/on-event` | `EventPersister.on_event` | 只把事件写入 `events`（跳过 text_delta 等瞬态类型） |
| `POST` | `/api/projection-updater/on-event` | `ProjectionUpdater.on_event` | 只根据事件更新 sessions/tasks 投影 |
| `POST` | `/api/snapshot-writer/on-event` | `SnapshotWriter.on_event` | 只处理快照计数与写入 |
| `POST` | `/api/snapshot-writer/close` | `SnapshotWriter.close` | 关闭快照 writer 内存状态 |
| `POST` | `/api/skill-reporter/on-event` | `SkillReporter.on_event` | Skill 任务计时与上报逻辑 |
| `POST` | `/api/skill-reporter/close` | `SkillReporter.close` | 关闭 skill reporter |
| `POST` | `/api/reconcile/stranded-running-sessions` | `reconcile_stranded_running_sessions` | 校正「投影 RUNNING 但事件已终态」的会话 |

**reconcile 响应：**

```json
{ "reconciled": 2 }
```

`reconciled` 为本次修正条数。适用场景：启动后 recovery 之后调用，把投影表与事件真相对齐。

### 调用示例

```bash
curl -X POST http://localhost:8080/api/event-persister/on-event \
  -H "Content-Type: application/json" \
  -d @event.json

curl -X POST http://localhost:8080/api/projection-updater/on-event \
  -H "Content-Type: application/json" \
  -d @event.json

curl -X POST http://localhost:8080/api/reconcile/stranded-running-sessions
```

---

## 7. 数据库初始化 API（postgres.__init__）

**Controller：** `DbInitController`  
**前缀：** `/api/db`

| 方法 | 路径 | Python | 作用 |
|------|------|--------|------|
| `GET` | `/api/db/resolve-url?url=...` | `resolve_db_url` | 把 shorthand URL 转成 async 驱动 URL（工具方法） |
| `POST` | `/api/db/tables` | `create_tables` | 执行 `schema.sql` 建表 |
| `POST` | `/api/db/session-factory?databaseUrl=...` | `create_session_factory` | 返回解析后的 URL 与驱动提示（Java 侧无 factory 对象） |
| `POST` | `/api/db/init?databaseUrl=` | `init_db` | 建表 + 返回初始化信息；`databaseUrl` 可选 |
| `PUT` | `/api/db/skill-reporter/sessions-store` | `set_sessions_store` | 注入 SkillReporter 用的 session 用户上下文 |

### 调用示例

```bash
curl "http://localhost:8080/api/db/resolve-url?url=postgresql://cowork:cowork123@localhost:5432/coworkdata"

curl -X POST http://localhost:8080/api/db/tables

curl -X POST http://localhost:8080/api/db/init

curl -X PUT http://localhost:8080/api/db/skill-reporter/sessions-store \
  -H "Content-Type: application/json" \
  -d '{"entries":[{"sessionId":"ses_abc","userInfo":{"username":"zhangsan"}}]}'
```

说明：应用启动时 `spring.sql.init.mode=always` 也会执行 `schema.sql`，一般不必再调 `/tables`。

---

## 8. 测试接口

**Controller：** `TestController`  
**前缀：** `/api/test`

| 方法 | 路径 | 作用 |
|------|------|------|
| `GET` | `/api/test/hello?name=world` | 健康检查 / 连通性测试 |

```bash
curl "http://localhost:8080/api/test/hello?name=CoworkData"
```

响应：

```json
{
  "message": "Hello, CoworkData!",
  "timestamp": "2026-08-24T13:00:00",
  "status": "ok"
}
```

---

## 9. 典型业务流程

### 9.1 创建会话（Host 侧发事件）

```text
1. POST /api/events/pipeline
   type=SessionCreated → 投影表出现 RUNNING 会话

2. PUT /api/sessions/{id}/workspace
   写入 agent 工作目录

3. GET /api/sessions/{id}
   前端拉会话详情
```

### 9.2 崩溃恢复

```text
1. GET /api/events/active-session-ids
   事件层判断哪些 session 仍 active

2. GET /api/sessions/active
   投影层 RUNNING 列表

3. PUT /api/sessions/{id}/status
   把需恢复的标 INTERRUPTED

4. POST /api/reconcile/stranded-running-sessions
   修正投影与事件不一致

5. GET /api/events/sessions/{id}/snapshots/latest
   加速 replay
```

### 9.3 软删会话

```text
DELETE /api/sessions/{id}
→ 写入 delete_at（软删）；关联 events/tasks 保留
```

### 9.4 按用户列表 + 活动时间

```text
GET /api/sessions?userId=zhangsan
GET /api/events/last-activity-times
→ 用 last_activity 覆盖展示时间，避免 updated_at 被 recovery 污染
```

### 9.5 地端增量同步

```text
1. GET /api/sessions/{id}/upload-watermark
2. POST /api/sessions/{id}/upload   （带 userId + events + uploadIndex）
3. GET /api/sessions/{id}/events    （云端回放校验）
```

---

## 10. Python ↔ HTTP 对照速查

| Python 模块 / 类 | REST 前缀 |
|------------------|-----------|
| 地端同步（新） | `/api/sessions`（watermark / upload / events / ?userId=） |
| `postgres.state_store.PostgresStateStore` | `/api/sessions` |
| `postgres.event_store.PostgresEventStore` | `/api/events` |
| `event_persister.EventPersister` | `POST /api/event-persister/on-event` |
| `postgres.projection_updater.ProjectionUpdater` | `POST /api/projection-updater/on-event` |
| `snapshot_writer.SnapshotWriter` | `POST /api/snapshot-writer/*` |
| `skill_reporter.SkillReporter` | `POST /api/skill-reporter/*` + `PUT /api/db/skill-reporter/sessions-store` |
| `postgres.reconcile.reconcile_stranded_running_sessions` | `POST /api/reconcile/stranded-running-sessions` |
| `postgres.__init__` | `/api/db/*` |

---

## 11. 注意事项

1. **两套 active 列表**：`/sessions/active`（投影 `RUNNING`）与 `/events/active-session-ids`（事件逻辑）判据不同，recovery 以事件层为主。
2. **瞬态事件**：`text_delta`、`reasoning_delta` 等不会落库，也不会驱动投影/快照。
3. **会话列表**：必须带 `userId`；全量无条件列表已移除。
4. **全量回放**：使用 `GET /api/sessions/{id}/events`。
5. **软删**：`DELETE /api/sessions/{id}` 写 `delete_at`，不物理删关联数据。
6. **鉴权**：当前 `/api/**` 为 `permitAll`，生产请加鉴权。
7. **数据库**：见 `application.yml`；表结构见 `schema.sql`。
