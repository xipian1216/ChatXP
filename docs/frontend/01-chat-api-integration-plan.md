# ChatXP Android 聊天 API 接入计划

> 文档状态：V1 前端开发基线<br>
> 后端权威契约：[`../backend/01-chat-api-development-plan.md`](../backend/01-chat-api-development-plan.md)

## 1. 目标与范围

本文档记录 Android 对聊天、会话、模型、身份和 SSE 接口的接入计划。HTTP 路径、字段、状态码及数据库语义以后端计划为准；本文件只描述客户端职责、状态和验收标准。

V1 前端完成：

- 游客或注册身份初始化与 token刷新。
- 模型列表和会话级模型选择。
- 会话列表、搜索、分页、切换和管理。
- 新聊天本地草稿与首条消息延迟建会话。
- POST SSE 流式回复及断线恢复。
- 登录、注册和退出后的聊天数据刷新。

附件、语音、消息编辑、分支对话和反馈不在本期范围。

## 2. 分层与公共模型

- `AuthRepository`：当前用户、installation、token、注册、登录和退出。
- `ModelRepository`：模型目录、默认模型和支持的思考程度。
- `SessionRepository`：会话列表、搜索、分页和会话操作。
- `ChatRepository`：消息分页、流式发送、SSE 解析和生成恢复。
- `ChatViewModel`：组合聊天、抽屉、模型、composer 和错误状态。

API DTO、领域模型和 Compose UI Model 必须分离。至少映射：

- `AuthUser`：`id`、`account_type`、`display_name`、`email`、`avatar_text`。
- `ModelOption`：模型 ID、显示名、说明、默认项、支持的思考程度及其他能力。
- `Session`：标题、模型、思考程度、置顶、摘要、消息数和时间。
- `Message`：角色、内容、状态、顺序、实际模型、实际思考程度和错误。
- `Generation`：客户端请求 ID、状态、实际思考程度、当前 assistant 消息和错误。

服务端时间统一转换为本地展示文本，不把“刚刚”等 UI 文案写入 DTO。

## 3. 网络接入

### 3.1 普通请求

- Retrofit 处理 `/api/v1` 下的 JSON 接口。
- Bearer interceptor在请求前获取有效 access token。
- 收到 `AUTH_EXPIRED` 时只允许一个 refresh请求执行，其余请求等待结果。
- 普通成功响应读取 `data`，失败响应读取统一 `error`。
- 错误提示按 `error.code` 本地化，不直接展示服务端 message。

### 3.2 SSE

- OkHttp 处理 `POST /api/v1/chat/streams`。
- 按空行识别完整 SSE 帧，不按单次 socket read解析。
- 支持 JSON 跨 buffer、UTF-8 分片、心跳注释和重复 delta。
- `meta` 替换本地 draft、session 和消息临时 ID。
- `delta` 按分片 sequence追加，忽略已处理分片。
- `done` 使用完整 assistant 消息覆盖本地累计内容。
- `error` 将 assistant 占位标记为失败并映射可重试状态。

连接意外断开后，按 1、2、4 秒递增并以 5 秒封顶轮询 generation，最长 5 分钟；完成或失败后停止。Composable 销毁不代表取消服务端生成。

## 4. 页面数据流

### 4.1 冷启动

1. 读取或生成 installation ID 与 secret。
2. 恢复 installation 当前绑定的游客或注册用户。
3. 并行加载模型目录和会话第一页。
4. 始终创建新的本地空白 draft，默认为“5.5 + 标准”，不自动打开上次会话。
5. 历史会话只显示在抽屉；存在未完成请求时执行 generation恢复。

### 4.2 新聊天和发送

1. 点击新聊天只创建本地 draft，不请求服务端。
2. 发送时生成 `client_request_id` 和 `client_message_id`。
3. 乐观插入用户消息和 streaming assistant占位。
4. 第一条消息通过 `meta` 获得真实 session ID。
5. 流结束后更新会话标题、摘要、消息数和时间。

同一会话存在 `queued/streaming` 任务时禁用再次发送。

### 4.3 会话抽屉

- 打开时先展示缓存，再刷新第一页。
- 搜索防抖 300ms，新查询取消旧请求并重置分页。
- 置顶、重命名可乐观更新，失败时回滚。
- 清空和删除等待服务端成功后再更新本地状态。
- 删除当前会话后进入列表第一项；列表为空时进入空白 draft。

### 4.4 模型与思考程度切换

- 配置按钮直接展开小型浮层：上方选择 5.5/5.6，下方选择标准/进阶，当前项右侧显示勾选。
- 模型和思考程度独立单选；选择后立即更新勾选且保持菜单展开，点击外部或再次点击按钮关闭。
- 草稿态只更新本地配置；已有会话通过 PATCH 更新，失败时恢复旧选择。
- 生成期间切换只影响下一条消息，不改变当前回复或历史消息。
- 详细交互见 [`03-model-switching-plan.md`](03-model-switching-plan.md)。

## 5. 本地状态与缓存

`ChatUiState` 至少包含：会话、当前 session/draft、消息、模型、思考程度、配置菜单与更新状态、搜索、抽屉、加载状态、活动 client request、当前 AuthUser 和错误。

- token、过期时间和 AuthUser在一次 DataStore edit中保存。
- 冷启动不以保存的 selected session作为首页，但可以保留它供兼容迁移后清理。
- 登录、注册和退出后清除旧身份的会话缓存并重新拉取。
- 退出后清除选中会话和活动请求，使用服务端返回的新游客身份。

## 6. 错误和边界

- `SESSION_BUSY`：提示等待回复完成后再清空或删除。
- `AUTH_TRANSITION_BUSY`：提示等待当前回复完成后再登录、注册或退出。
- `MODEL_NOT_FOUND`：刷新模型目录并回退“5.5 + 标准”。
- 模型目录不可用时使用“5.5 + 标准”展示默认态，并禁用目录中不存在的选项。
- `PROVIDER_RATE_LIMIT/PROVIDER_UNAVAILABLE`：保留用户消息并把 assistant标记为可重试失败。
- 未知枚举或可选字段不得导致客户端崩溃。

## 7. 测试与验收

- 冷启动始终进入新对话，历史仍可从抽屉打开。
- 首条消息正确替换 draft和临时消息 ID。
- SSE 跨 buffer、重复 delta、心跳、错误和最终覆盖均正确。
- 断网、切后台和进程恢复后能查询 generation结果。
- 会话搜索、分页、切换、置顶、重命名、清空和删除正确。
- 模型与思考程度的四种组合在草稿与已有会话中均正确。
- 身份切换后 token、头像、会话缓存和页面状态一致。
- Android DTO通过后端导出的 OpenAPI 与固定 SSE fixtures验证。
