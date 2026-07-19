# ChatXP V1 后端 API 与数据开发计划

> 文档状态：V1 后端开发基线<br>
> 前端接入计划：[`../frontend/01-chat-api-integration-plan.md`](../frontend/01-chat-api-integration-plan.md)
> API 前缀：`/api/v1`

## 1. 目标与范围

本文档是 ChatXP Android 客户端与 FastAPI 服务端之间的 V1 开发契约，用于统一接口、字段、状态流转、持久化和验收标准。

V1 实现以下闭环：

- 游客身份初始化、邮箱账号注册、登录、退出与令牌刷新。
- 游客会话在注册或登录后自动保留到账号。
- 获取可用模型并按会话切换模型。
- 会话列表、搜索、切换、重命名、置顶、清空和删除。
- 新聊天本地草稿、首条消息延迟建会话。
- 文本消息发送和 SSE 流式回复。
- 流式连接中断后的生成状态恢复。

以下功能不在 V1 实现范围：

- 邮箱验证、忘记密码和账号资料修改。
- 多账号快捷切换和一键退出全部设备。
- 附件、图片、语音和多模态消息。
- 消息编辑、分支对话、重新生成和停止生成。
- 消息反馈、个人资料和后台管理界面。

附件、忘记密码、个人资料编辑和反馈入口可以保留在客户端，但需显示“暂未开放”，不得调用未定义接口。消息复制完全由客户端本地完成。

## 2. 技术基线

### 2.1 服务端

- FastAPI、Pydantic v2。
- SQLAlchemy 2.x 异步会话、`aiosqlite` 驱动。
- Alembic 管理数据库迁移。
- OpenAI-compatible Provider 对接上游模型。
- 单 Uvicorn worker、单服务实例。

### 2.2 SQLite

服务启动时必须设置：

```sql
PRAGMA journal_mode = WAL;
PRAGMA foreign_keys = ON;
PRAGMA busy_timeout = 5000;
```

SQLite 方案只用于 V1 单机或单容器部署。不得启动多个共享同一 SQLite 文件的 FastAPI 实例。后续迁移 PostgreSQL 时保持本文件定义的 HTTP、SSE 和领域字段兼容。

## 3. 通用协议

### 3.1 基础规则

- 所有请求和响应使用 UTF-8。
- 普通请求和响应使用 `application/json`。
- 流式响应使用 `text/event-stream`。
- 所有 ID 使用 UUID 字符串。
- 所有时间使用 UTC RFC 3339，例如 `2026-07-13T08:30:00Z`。
- 受保护接口使用 `Authorization: Bearer <access_token>`。
- 字符串校验前先去除首尾空白；不自动折叠正文内部空白。
- 服务端生成并返回 `X-Request-ID`；客户端可以传入同名请求头用于链路追踪。

普通成功响应统一包装为：

```json
{
  "data": {}
}
```

普通失败响应统一为：

```json
{
  "error": {
    "code": "SESSION_NOT_FOUND",
    "message": "Session not found",
    "request_id": "2878cb66-ff43-45ed-97a0-f456dee88f26",
    "details": {}
  }
}
```

`message` 用于开发诊断，不作为 Android 最终展示文案。Android 根据 `code` 映射本地化提示；未知错误显示统一兜底提示。

### 3.2 状态码和错误码

| HTTP | 错误码 | 使用场景 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 业务参数不合法 |
| 401 | `AUTH_INVALID` | 令牌、installation secret 或 refresh token 无效 |
| 401 | `AUTH_EXPIRED` | access token 已过期 |
| 401 | `INVALID_CREDENTIALS` | 邮箱或密码错误 |
| 404 | `MODEL_NOT_FOUND` | 模型不存在或已停用 |
| 404 | `SESSION_NOT_FOUND` | 会话不存在或不属于当前用户 |
| 404 | `GENERATION_NOT_FOUND` | 生成请求不存在或不属于当前用户 |
| 409 | `SESSION_BUSY` | 会话有运行中的生成任务，禁止清空或删除 |
| 409 | `EMAIL_ALREADY_REGISTERED` | 注册邮箱已存在 |
| 409 | `ALREADY_AUTHENTICATED` | 当前 installation 已是登录状态 |
| 409 | `AUTH_TRANSITION_BUSY` | 当前身份存在运行中的生成任务 |
| 429 | `LOGIN_RATE_LIMITED` | 登录失败次数过多 |
| 429 | `PROVIDER_RATE_LIMIT` | 上游模型限流 |
| 502 | `PROVIDER_UNAVAILABLE` | 上游模型不可用或返回非法响应 |
| 500 | `GENERATION_INTERRUPTED` | 服务重启导致未完成任务中断 |
| 500 | `INTERNAL_ERROR` | 未分类服务端错误 |

校验错误的 `details` 至少包含字段路径。服务端日志不得记录 Authorization、refresh token、installation secret、上游 API Key 或完整提示词正文。

### 3.3 分页

会话列表和消息列表均采用不透明游标。客户端不得解析或拼接游标，只能原样传回。

```json
{
  "data": {
    "items": [],
    "next_cursor": null,
    "has_more": false
  }
}
```

会话列表默认 `limit=30`，消息列表默认 `limit=50`；两者允许范围均为 `1..100`。

## 4. 公开数据模型

### 4.1 AuthUser

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | UUID | 是 | 当前用户 ID |
| `account_type` | `guest` / `registered` | 是 | 游客或注册账号 |
| `display_name` | string/null | 是 | 游客为 `null` |
| `email` | string/null | 是 | 游客为 `null` |
| `avatar_text` | string/null | 是 | 用户名首个字素；游客为 `null` |

### 4.2 ModelOption

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | string | 是 | 对客户端公开的稳定模型别名 |
| `display_name` | string | 是 | 顶部模型选择器展示名称 |
| `description` | string/null | 是 | 模型说明，可为空 |
| `is_default` | boolean | 是 | 是否为默认模型；列表中必须且只能有一个 |
| `capabilities.streaming` | boolean | 是 | V1 固定为 `true` |
| `capabilities.attachments` | boolean | 是 | V1 固定为 `false` |
| `capabilities.reasoning_modes` | array | 是 | 支持的思考程度，本期为 `standard`、`advanced` |

客户端不可见上游供应商模型名、API Key 和 Base URL。

### 4.3 Session

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | UUID | 是 | 会话 ID |
| `title` | string | 是 | 1–100 字符 |
| `model_id` | string | 是 | 后续消息使用的默认模型 |
| `reasoning_mode` | `standard` / `advanced` | 是 | 后续消息使用的默认思考程度 |
| `is_pinned` | boolean | 是 | 是否置顶 |
| `last_message_preview` | string/null | 是 | 最后消息前 80 个 Unicode 字符 |
| `message_count` | integer | 是 | 当前未删除消息数量 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 最近活动时间 |

服务端列表排序固定为：`is_pinned DESC, updated_at DESC, id DESC`。置顶操作也更新 `updated_at`，使最近置顶的会话优先显示。

### 4.4 Message

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | UUID | 是 | 消息 ID |
| `session_id` | UUID | 是 | 所属会话 |
| `role` | `user` / `assistant` | 是 | 消息角色 |
| `content` | string | 是 | 文本内容；流式期间可为空或为部分内容 |
| `status` | `streaming` / `completed` / `failed` | 是 | 消息状态 |
| `sequence` | integer | 是 | 会话内从 1 开始递增的稳定顺序 |
| `model_id` | string/null | 是 | assistant 实际使用的公开模型别名 |
| `reasoning_mode` | `standard` / `advanced` / null | 是 | assistant 实际使用的思考程度 |
| `client_message_id` | UUID/null | 是 | user 消息幂等 ID |
| `error_code` | string/null | 是 | assistant 失败原因 |
| `prompt_tokens` | integer/null | 是 | 上游返回的输入 token 数 |
| `completion_tokens` | integer/null | 是 | 上游返回的输出 token 数 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

用户消息写入后状态直接为 `completed`，`model_id` 和 `reasoning_mode` 均为 `null`。assistant 消息创建时为 `streaming`，正常结束后改为 `completed`，失败时改为 `failed`。

### 4.5 Generation

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | UUID | 是 | 服务端生成任务 ID |
| `client_request_id` | UUID | 是 | 客户端请求幂等 ID |
| `status` | `queued` / `streaming` / `completed` / `failed` | 是 | 生成状态 |
| `session_id` | UUID | 是 | 所属会话 |
| `user_message_id` | UUID | 是 | 本次用户消息 |
| `reasoning_mode` | `standard` / `advanced` | 是 | 本次生成实际使用的思考程度 |
| `assistant_message` | Message | 是 | 当前部分内容或最终内容 |
| `error_code` | string/null | 是 | 失败原因 |
| `error_message` | string/null | 是 | 开发诊断信息 |
| `created_at` | datetime | 是 | 创建时间 |
| `updated_at` | datetime | 是 | 更新时间 |

## 5. 身份与账号接口

所有 token 响应在既有字段基础上增加 `user: AuthUser`。Access token包含 `installation_id` 和 `token_version`，服务端鉴权时同时检查 installation 绑定关系和版本。

### 5.1 初始化或恢复 installation

`POST /api/v1/auth/anonymous`

无需 Bearer token。

请求：

```json
{
  "installation_id": "83cf8513-c2a7-4242-99fa-4ca32ef9ca52",
  "installation_secret": "base64url-random-32-bytes",
  "platform": "android",
  "app_version": "1.0.0"
}
```

约束：

- `installation_id` 由 Android 首次启动时生成并持久化。
- `installation_secret` 使用安全随机数生成，至少 32 字节，服务端只保存哈希。
- 相同 installation ID 首次请求创建游客；后续请求必须通过 secret 校验。
- installation 已绑定注册账号时恢复该账号，已退出时恢复新游客。
- 不返回已存在设备的原始 secret。

响应：

```json
{
  "data": {
    "user_id": "a5bd28fb-912a-41b1-a725-bf24415eec71",
    "user": {
      "id": "a5bd28fb-912a-41b1-a725-bf24415eec71",
      "account_type": "guest",
      "display_name": null,
      "email": null,
      "avatar_text": null
    },
    "access_token": "eyJ...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "opaque-refresh-token",
    "refresh_expires_in": 7776000
  }
}
```

### 5.2 获取当前用户

`GET /api/v1/auth/me`

需要 Bearer token，返回 `DataEnvelope<AuthUser>`。用于启动恢复、侧边栏头像和账号面板。

### 5.3 注册

`POST /api/v1/auth/register`

需要当前游客 Bearer token。

```json
{
  "display_name": "小明",
  "email": "name@example.com",
  "password": "example-password"
}
```

- `display_name` 去除首尾空白后为 1–30 个字符，不要求唯一。
- 邮箱规范化为小写并作为唯一登录标识。
- 密码长度为 8–72 个字符，使用 Argon2id 哈希保存。
- 当前游客原地升级为注册用户，user ID 与已有会话归属不变。
- 成功后提升 installation token version、吊销旧 refresh token并返回新 token 响应。

### 5.4 登录

`POST /api/v1/auth/login`

需要当前游客 Bearer token。

```json
{
  "email": "name@example.com",
  "password": "example-password"
}
```

- 登录成功后，在单一事务中将游客 sessions、messages 和 generations 转移到目标账号。
- 保留双方全部会话，不按标题或内容去重。
- 当前 installation 改绑目标账号，游客旧 token失效。
- 错误邮箱与错误密码统一返回 `401 INVALID_CREDENTIALS`。

### 5.5 退出登录

`POST /api/v1/auth/logout`

需要当前注册用户 Bearer token且无请求体。服务端只吊销当前 installation 的 token，创建新游客并改绑当前 installation；成功返回新游客的 token 响应。其他设备保持登录，账号会话不转移给新游客。

### 5.6 刷新令牌

`POST /api/v1/auth/refresh`

请求：

```json
{
  "refresh_token": "opaque-refresh-token"
}
```

响应字段与匿名身份响应一致并包含 `user`。refresh token必须匹配 user 与 installation。每次成功刷新必须轮换 refresh token，并立即吊销旧 token。重复使用已吊销 token返回 `401 AUTH_INVALID`。

Android 在普通请求收到 `AUTH_EXPIRED` 后只允许串行执行一次刷新，其余请求等待刷新结果；刷新失败后清除 token，并使用 installation ID 与 secret恢复 installation 当前绑定身份。

### 5.7 账号转换限制

- 注册、登录和退出必须是单一数据库事务。
- 当前用户存在 `queued` 或 `streaming` generation 时返回 `409 AUTH_TRANSITION_BUSY`。
- 登录失败按规范化邮箱与 installation 限制为 15 分钟内最多 5 次，超限返回 `429 LOGIN_RATE_LIMITED`。
- 本期不提供忘记密码接口。

## 6. 模型接口

### 6.1 获取模型目录

`GET /api/v1/models`

响应：

```json
{
  "data": {
    "items": [
      {
        "id": "chat-5.5",
        "display_name": "5.5",
        "description": "适合日常对话的 Flash 模型",
        "is_default": true,
        "capabilities": {
          "streaming": true,
          "attachments": false,
          "reasoning_modes": ["standard", "advanced"]
        }
      },
      {
        "id": "chat-5.6",
        "display_name": "5.6",
        "description": "适合复杂任务的 Pro 模型",
        "is_default": false,
        "capabilities": {
          "streaming": true,
          "attachments": false,
          "reasoning_modes": ["standard", "advanced"]
        }
      }
    ]
  }
}
```

模型目录由环境变量配置。建议配置项：

```text
AI_BASE_URL=
AI_API_KEY=
AI_DEFAULT_MODEL=chat-5.5
AI_MODELS_JSON=[{"id":"chat-5.5","provider_model":"provider-flash-model","display_name":"5.5","description":"Flash 模型"},{"id":"chat-5.6","provider_model":"provider-pro-model","display_name":"5.6","description":"Pro 模型"}]
```

`provider_model` 仅在服务端内部使用。`standard` 映射为关闭思考模式，`advanced` 映射为开启思考模式；具体上游参数由 Provider adapter 处理。专项规则见 [`03-model-switching-plan.md`](03-model-switching-plan.md)。

## 7. 会话接口

### 7.1 获取会话列表

`GET /api/v1/sessions?q=&cursor=&limit=30`

- `q` 可选，去除首尾空白后最长 100 字符。
- 搜索范围为会话标题和最后消息摘要。
- SQLite V1 使用大小写不敏感的包含匹配；后续可改为 FTS，但接口不变。

响应：

```json
{
  "data": {
    "items": [
      {
        "id": "97978da3-a40b-410f-ae60-c83ed26d142c",
        "title": "制定学习计划",
        "model_id": "chat-5.5",
        "reasoning_mode": "standard",
        "is_pinned": true,
        "last_message_preview": "我会把计划拆成四个阶段。",
        "message_count": 4,
        "created_at": "2026-07-13T08:00:00Z",
        "updated_at": "2026-07-13T08:30:00Z"
      }
    ],
    "next_cursor": null,
    "has_more": false
  }
}
```

### 7.2 获取单个会话

`GET /api/v1/sessions/{session_id}`

返回单个 `Session`。不存在或不属于当前用户时统一返回 `404 SESSION_NOT_FOUND`，不得泄露其他用户资源是否存在。

### 7.3 更新会话

`PATCH /api/v1/sessions/{session_id}`

请求至少包含一个字段：

```json
{
  "title": "新的标题",
  "model_id": "chat-5.5",
  "reasoning_mode": "standard",
  "is_pinned": true
}
```

字段规则：

- `title` 去除首尾空白后长度为 1–100。
- `model_id` 必须存在于当前模型目录。
- `reasoning_mode` 必须为该模型支持的 `standard` 或 `advanced`。
- 模型或思考程度切换只影响后续生成；历史 assistant 消息的实际配置不变。
- 当前正在生成的任务捕获启动时的模型和思考程度，切换不会修改该任务。

响应为更新后的 `Session`。

### 7.4 删除会话

`DELETE /api/v1/sessions/{session_id}`

- 永久删除会话、消息和生成记录。
- 有 `queued` 或 `streaming` 任务时返回 `409 SESSION_BUSY`。
- 成功返回 `204 No Content`。
- Android 必须在执行前显示不可逆确认对话框。

### 7.5 清空会话消息

`DELETE /api/v1/sessions/{session_id}/messages`

- 保留会话标题、模型和置顶状态。
- 清空后 `message_count=0`、`last_message_preview=null`。
- 有运行中任务时返回 `409 SESSION_BUSY`。
- 成功返回 `204 No Content`。

### 7.6 新聊天创建规则

点击“新建聊天”不调用会话创建接口，只在 Android 进入本地草稿态。用户发送第一条消息时，由流式消息接口在同一事务中创建会话。

默认标题规则：

1. 取规范化后的第一条用户消息。
2. 将换行替换为单个空格。
3. 取前 30 个 Unicode 字符。
4. 超出 30 个字符时追加 `…`。

## 8. 消息与生成接口

### 8.1 获取消息列表

`GET /api/v1/sessions/{session_id}/messages?before=&limit=50`

- 不传 `before` 时查询最新一页。
- `before` 表示继续查询该游标之前的更早消息。
- `items` 始终按 `sequence ASC` 返回，便于直接追加到 UI。
- 分页响应使用 `next_before`，不使用会话列表的 `next_cursor` 字段名。

```json
{
  "data": {
    "items": [],
    "next_before": null,
    "has_more": false
  }
}
```

### 8.2 发送消息并订阅回复

`POST /api/v1/chat/streams`

请求：

```json
{
  "client_request_id": "1f5735d5-9c41-4350-bee2-728524106e55",
  "client_message_id": "50711fca-62c8-40d2-98a3-34c74d175962",
  "session_id": null,
  "model_id": "chat-5.5",
  "reasoning_mode": "standard",
  "content": "帮我制定一份学习计划"
}
```

字段规则：

| 字段 | 规则 |
|---|---|
| `client_request_id` | 必填 UUID；标识整次生成并提供幂等性 |
| `client_message_id` | 必填 UUID；标识本次用户消息并提供幂等性 |
| `session_id` | 新聊天为 `null`；已有会话传真实 ID |
| `model_id` | 新聊天必填；已有会话可省略并使用会话默认模型 |
| `reasoning_mode` | 新聊天必填 `standard` 或 `advanced`；已有会话可省略并使用会话默认值 |
| `content` | 必填；原始正文 1–20,000 个 Unicode 字符且必须包含非空白字符；服务端原样保留首尾空白、换行和 Markdown 标记 |

新会话情况下，创建会话、用户消息、assistant 占位消息和 generation 记录必须处于同一个数据库事务。已有会话必须锁定或以事务方式计算下一 `sequence`，避免并发消息产生重复顺序。

响应头：

```text
Content-Type: text/event-stream; charset=utf-8
Cache-Control: no-cache
Connection: keep-alive
X-Accel-Buffering: no
```

SSE 帧统一使用如下格式，`data` 必须是单行 JSON：

```text
id: 1
event: delta
data: {"assistant_message_id":"...","sequence":1,"content_delta":"你好"}

```

事件定义如下。

#### meta

流建立后的第一个业务事件：

```json
{
  "generation_id": "619bc6fa-bd2b-43e8-a3f3-0da73a07aebe",
  "client_request_id": "1f5735d5-9c41-4350-bee2-728524106e55",
  "session_created": true,
  "session": {},
  "user_message": {},
  "assistant_message_id": "25626d53-b060-4ac8-8e83-a0db882cf8df"
}
```

Android 收到 `meta` 后，用真实 session ID 替换本地草稿 ID，并使用服务端消息 ID 替换乐观占位 ID。

#### delta

```json
{
  "assistant_message_id": "25626d53-b060-4ac8-8e83-a0db882cf8df",
  "sequence": 12,
  "content_delta": "下一段文本"
}
```

事件中的 `sequence` 是当前 SSE 流内从 1 开始递增的分片序号，不是消息的会话顺序。客户端只接受比已处理分片序号大的事件。

#### done

```json
{
  "assistant_message": {},
  "finish_reason": "stop",
  "usage": {
    "prompt_tokens": 120,
    "completion_tokens": 280,
    "total_tokens": 400
  },
  "session": {}
}
```

`done` 必须包含完整 assistant 消息，客户端以其内容覆盖本地累计内容，消除分片遗漏或重复造成的差异。

#### error

```json
{
  "code": "PROVIDER_UNAVAILABLE",
  "message": "Upstream provider unavailable",
  "retryable": true,
  "assistant_message_id": "25626d53-b060-4ac8-8e83-a0db882cf8df"
}
```

开始发送 SSE 响应头之前发生的错误使用普通 HTTP 错误响应。响应头发出后发生的错误使用 `event: error`，随后关闭连接。

#### heartbeat

没有业务事件时，每 15 秒发送一次注释心跳：

```text
: ping

```

### 8.3 幂等行为

- `(user_id, client_request_id)` 全局唯一。
- `(user_id, client_message_id)` 全局唯一。
- 同一 `client_request_id` 和相同请求内容再次提交时，不重复写消息、不重复调用模型。
- 任务仍在运行时，重复请求订阅同一任务的后续事件；客户端应先调用恢复接口取得当前完整内容。
- 任务已结束时，重复请求依次返回 `meta` 和 `done`，然后关闭流。
- 相同 ID 携带不同 session、model、reasoning mode 或 content 时返回 `409 VALIDATION_ERROR`，`details.reason` 为 `IDEMPOTENCY_KEY_REUSED`。

### 8.4 查询生成状态

`GET /api/v1/generations/by-client-request/{client_request_id}`

响应：

```json
{
  "data": {
    "id": "619bc6fa-bd2b-43e8-a3f3-0da73a07aebe",
    "client_request_id": "1f5735d5-9c41-4350-bee2-728524106e55",
    "status": "streaming",
    "session_id": "97978da3-a40b-410f-ae60-c83ed26d142c",
    "user_message_id": "0e82b353-7191-49d6-ab9f-995307f63782",
    "reasoning_mode": "standard",
    "assistant_message": {},
    "error_code": null,
    "error_message": null,
    "created_at": "2026-07-13T08:30:00Z",
    "updated_at": "2026-07-13T08:30:03Z"
  }
}
```

Android 在 SSE 意外断开后按 1、2、4 秒递增并以 5 秒封顶的间隔轮询，最长持续 5 分钟：

- `queued/streaming`：用返回的完整 assistant 内容覆盖当前部分内容并继续轮询。
- `completed`：应用最终消息并停止轮询。
- `failed`：展示失败状态和可重试提示并停止轮询。
- 超过 5 分钟：停止自动轮询，保留“点击恢复”入口。

服务进程启动时必须将遗留的 `queued/streaming` generation 和对应 assistant 消息标为 `failed`，错误码为 `GENERATION_INTERRUPTED`。

## 9. 服务端持久化设计

### 9.1 users

| 字段 | 建议类型 | 约束 |
|---|---|---|
| `id` | string(36) | PK |
| `account_type` | string | CHECK guest/registered |
| `display_name` | string(30)/null | 注册用户非空 |
| `email_normalized` | string/null | 注册用户唯一 |
| `password_hash` | string/null | 注册用户非空，Argon2id |
| `created_at` | datetime | NOT NULL |
| `updated_at` | datetime | NOT NULL |

### 9.2 installations

| 字段 | 建议类型 | 约束 |
|---|---|---|
| `installation_id` | string(36) | PK |
| `installation_secret_hash` | string | NOT NULL |
| `user_id` | string(36) | FK users.id, NOT NULL |
| `token_version` | integer | NOT NULL, DEFAULT 1 |
| `platform` | string | NOT NULL |
| `app_version` | string | NOT NULL |
| `created_at` | datetime | NOT NULL |
| `last_seen_at` | datetime | NOT NULL |

一个注册用户可绑定多个 installations，一个 installation 同时只绑定一个用户。

### 9.3 refresh_tokens

| 字段 | 建议类型 | 约束 |
|---|---|---|
| `id` | string(36) | PK |
| `user_id` | string(36) | FK, NOT NULL |
| `installation_id` | string(36) | FK, NOT NULL |
| `token_hash` | string | UNIQUE, NOT NULL |
| `expires_at` | datetime | NOT NULL |
| `revoked_at` | datetime/null | 允许为空 |
| `replaced_by_id` | string(36)/null | 自引用 FK |
| `created_at` | datetime | NOT NULL |

### 9.4 sessions

| 字段 | 建议类型 | 约束 |
|---|---|---|
| `id` | string(36) | PK |
| `user_id` | string(36) | FK, NOT NULL |
| `title` | string(100) | NOT NULL |
| `model_id` | string | NOT NULL |
| `reasoning_mode` | string | CHECK standard/advanced, NOT NULL |
| `is_pinned` | boolean | NOT NULL, DEFAULT false |
| `created_at` | datetime | NOT NULL |
| `updated_at` | datetime | NOT NULL |

索引：`(user_id, updated_at)`、`(user_id, is_pinned, updated_at)`。

### 9.5 messages

| 字段 | 建议类型 | 约束 |
|---|---|---|
| `id` | string(36) | PK |
| `user_id` | string(36) | FK, NOT NULL |
| `session_id` | string(36) | FK ON DELETE CASCADE, NOT NULL |
| `role` | string | CHECK user/assistant |
| `content` | text | NOT NULL |
| `status` | string | CHECK streaming/completed/failed |
| `sequence` | integer | NOT NULL |
| `model_id` | string/null | assistant 使用 |
| `reasoning_mode` | string/null | assistant 使用，CHECK standard/advanced |
| `client_message_id` | string(36)/null | user 使用 |
| `error_code` | string/null | 允许为空 |
| `prompt_tokens` | integer/null | 允许为空 |
| `completion_tokens` | integer/null | 允许为空 |
| `created_at` | datetime | NOT NULL |
| `updated_at` | datetime | NOT NULL |

唯一约束：`(session_id, sequence)`、`(user_id, client_message_id)`。SQLite 迁移需使用部分唯一索引排除 `client_message_id IS NULL`。

### 9.6 generations

| 字段 | 建议类型 | 约束 |
|---|---|---|
| `id` | string(36) | PK |
| `user_id` | string(36) | FK, NOT NULL |
| `session_id` | string(36) | FK ON DELETE CASCADE, NOT NULL |
| `client_request_id` | string(36) | NOT NULL |
| `user_message_id` | string(36) | FK ON DELETE CASCADE, NOT NULL |
| `assistant_message_id` | string(36) | FK ON DELETE CASCADE, NOT NULL |
| `model_id` | string | NOT NULL |
| `reasoning_mode` | string | CHECK standard/advanced, NOT NULL |
| `status` | string | CHECK queued/streaming/completed/failed |
| `request_fingerprint` | string | 校验幂等请求的会话、模型、思考程度和内容 |
| `error_code` | string/null | 允许为空 |
| `error_message` | string/null | 允许为空 |
| `created_at` | datetime | NOT NULL |
| `updated_at` | datetime | NOT NULL |

唯一约束：`(user_id, client_request_id)`。

### 9.7 身份迁移与写入策略

- 所有查询都必须同时限定当前 `user_id`。
- Alembic 将既有 `anonymous_users` 迁移为 `users`，原记录标记为 `guest` 并保留 user ID。
- 为每个原游客创建 installation，迁移 installation ID、secret hash、平台、版本与时间。
- 既有 sessions、messages 和 generations 的 user ID 保持不变，升级后不得丢失游客历史。
- 模型配置迁移将既有 `chat-default` 改为 `chat-5.5`，并为既有会话、assistant 消息和生成记录回填 `standard`。
- 游客登录已有账号时，在同一事务内转移三类业务记录并改绑 installation；游客注册则原地升级。
- 退出只改绑当前 installation 到新游客，不转移或删除账号会话。
- 上游生成使用独立 `asyncio.Task`，HTTP 连接只订阅事件，不拥有任务生命周期。
- assistant 内容累计达到 256 个字符或距离上次落库达到 500ms 时批量更新；结束和失败时强制落库。
- 用户消息写入时立即更新会话 `updated_at`；完成时再更新摘要和 `updated_at`。
- 上游 usage 不可用时 token 字段保持 `null`，不得估算后冒充供应商数据。
- 模型目录来自配置而非数据库表；会话保存公开模型别名。

## 10. 前端边界

Android 分层、页面状态、SSE 消费、缓存与交互规则由[前端聊天 API 接入计划](../frontend/01-chat-api-integration-plan.md)定义。本文件中的公开字段、HTTP 接口、SSE 事件和错误码是前后端联调的权威契约。

## 11. 安全和运维约束

- 所有生产或外网环境必须使用 HTTPS。
- installation secret、refresh token、密码和 AI API Key 只保存哈希或安全存储形式；密码使用 Argon2id。
- access token 建议为 1 小时 JWT；refresh token 为 90 天高熵不透明字符串。
- JWT 必须包含 `sub`、`installation_id`、`token_version`、`iat`、`exp`、`jti`，并验证签名、过期时间、installation 绑定和版本。
- CORS 不作为 Android 安全控制；默认不开放任意 Web Origin。
- 日志只记录 request ID、user ID、session ID、generation ID、模型别名、耗时和错误码。
- 服务启动执行 Alembic upgrade，并完成遗留 generation 状态修复后才对外报告 ready。
- 健康检查至少提供 `/health/live` 和 `/health/ready`，不放在需要 Bearer token 的 API 路由下。

## 12. 测试与验收

### 12.1 服务端自动化测试

- 首次游客初始化、同设备身份恢复、错误 secret 拒绝。
- 游客原地注册后 user ID与会话保持不变。
- 游客登录已有账号后双方会话完整合并，退出只影响当前设备。
- 旧 token失效、重复邮箱、统一凭证错误、登录限流和活跃生成转换限制。
- access token 过期、refresh token 轮换和旧 token 重放拒绝。
- 不同用户之间的会话、消息和 generation 完全隔离。
- 模型目录默认为 5.5，5.5/5.6 与标准/进阶的四种组合正确映射。
- 未知模型返回 `MODEL_NOT_FOUND`，非法思考程度返回 `VALIDATION_ERROR`。
- 会话搜索、置顶排序、游标稳定性和分页边界。
- 标题截断、重命名、切换模型与思考程度、清空和级联删除。
- 新会话发送的事务原子性和消息 sequence 唯一性。
- SSE `meta → delta* → done` 顺序、UTF-8 分片和心跳。
- 上游限流、不可用和中途失败映射。
- 相同幂等 ID 不重复创建消息或调用上游。
- 客户端断开后任务继续，恢复接口返回当前或最终内容。
- 服务重启后遗留任务标为 `GENERATION_INTERRUPTED`。

### 12.2 契约测试

- FastAPI OpenAPI Schema 作为 REST DTO 的唯一来源。
- Android DTO 使用导出的 OpenAPI fixture 校验字段名、可空性和枚举。
- SSE 的 `meta`、`delta`、`done`、`error` 分别维护固定 JSON fixture。
- 任何删除字段、重命名字段、改变可空性或枚举的改动都视为破坏性变更，必须升级 API 版本。

### 12.3 端到端验收

满足以下条件才可视为 V1 闭环完成：

1. 新安装应用可自动获得游客身份且无需登录即可聊天。
2. 用户可在 5.5/5.6 和标准/进阶之间选择，并发送首条文本消息。
3. 回复以 SSE 增量展示，并在完成后持久化。
4. 重启应用后可以恢复会话和消息历史。
5. 抽屉可搜索、切换、重命名、置顶、清空和删除会话。
6. SSE 中断后生成继续，客户端能恢复最终或当前内容。
7. 两个用户无法读取或修改彼此数据。
8. 游客注册或登录后可继续访问此前游客会话。
9. 当前设备退出后进入新游客状态，其他设备和账号会话不受影响。

## 13. 兼容性与后续演进

- PostgreSQL 迁移只替换数据库配置、迁移脚本和并发实现，不改变 V1 HTTP 契约。
- 多实例部署前，生成任务和事件总线必须迁移到可共享的队列/缓存系统。
- 附件功能未来应引入结构化 `content_parts`，不得在 V1 的 `content` 中嵌入临时 JSON。
- 邮箱验证、忘记密码、资料修改和账号注销作为后续独立能力，不改变现有游客会话合并语义。
- 新增消息编辑、分支或重新生成时需要单独设计 parent message 和版本关系，不修改既有消息语义。
