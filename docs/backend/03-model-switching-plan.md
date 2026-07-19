# ChatXP 后端模型与思考程度切换计划

> 开发版本：`v1.3.0-dev`<br>
> 前端对应计划：[`../frontend/03-model-switching-plan.md`](../frontend/03-model-switching-plan.md)

## 1. 目标与映射

后端对客户端只公开稳定别名和思考程度，供应商完整模型名由环境配置维护。默认组合为 `chat-5.5 + standard`。

| 公开选项 | 内部含义 |
|---|---|
| `chat-5.5` / 5.5 | 映射 Flash 模型 |
| `chat-5.6` / 5.6 | 映射 Pro 模型 |
| `standard` / 标准 | 关闭思考模式 |
| `advanced` / 进阶 | 开启思考模式 |

Flash、Pro 的完整供应商模型名不进入 API 响应或日志。Provider adapter 负责把 `reasoning_mode` 翻译为上游所需的思考开关。

## 2. 公开契约

- `GET /api/v1/models` 返回 `chat-5.5`、`chat-5.6`，每项的 `capabilities.reasoning_modes` 均为 `standard`、`advanced`。
- `Session` 增加必填 `reasoning_mode`，表示会话后续生成的默认思考程度。
- assistant `Message` 增加可空 `reasoning_mode`，与 `model_id` 一同记录实际生成配置；user 消息两者均为空。
- `Generation` 增加必填 `reasoning_mode`，断流恢复时仍可确认任务的实际配置。
- `PATCH /api/v1/sessions/{session_id}` 允许单独或同时更新 `model_id`、`reasoning_mode`。
- `POST /api/v1/chat/streams` 增加可选 `reasoning_mode`；新会话必须同时提供模型和思考程度，已有会话省略时使用会话默认值。
- 未知模型返回 `MODEL_NOT_FOUND`；非法或模型不支持的思考程度返回 `VALIDATION_ERROR`。

## 3. 生成与幂等规则

- 新建生成任务时固化 `model_id` 和 `reasoning_mode`，之后的会话配置切换不改变运行中任务。
- 上游调用从模型目录解析 Flash/Pro，再由 Provider adapter 把 `standard/advanced` 映射为思考关/开。
- 幂等请求指纹包含 `session_id`、`model_id`、`reasoning_mode`和 `content`。
- 相同 `client_request_id` 使用不同模型或思考程度时，返回 `409 VALIDATION_ERROR` 与 `IDEMPOTENCY_KEY_REUSED`，不复用旧任务。
- SSE `meta`、`done` 和生成恢复响应通过 Session、Message、Generation 字段返回实际配置。

## 4. 持久化与迁移

- `sessions.reasoning_mode` 为非空字段，新数据默认 `standard`。
- `messages.reasoning_mode` 允许为空，仅 assistant 消息保存实际值。
- `generations.reasoning_mode` 为非空字段，并在创建任务的事务中与模型一起写入。
- Alembic 迁移将既有 `chat-default` 统一改为 `chat-5.5`，为既有 Session、assistant Message 和 Generation 回填 `standard`。
- 迁移前后校验会话、消息和生成记录数量；既有 user 消息的 `reasoning_mode` 保持为空。

## 5. 配置与安全

- 默认公开模型为 `chat-5.5`，模型目录必须同时包含 5.5 和 5.6 两个唯一别名。
- 环境配置分别绑定 Flash 和 Pro 的完整供应商模型名，API Key、Base URL 和内部模型名不向客户端返回。
- 日志只记录公开模型别名和 `reasoning_mode`，不记录供应商模型名或完整提示词。

## 6. 测试与验收

- 模型目录正确返回 5.5、5.6、默认项和两种思考程度。
- 四种组合均正确映射到 Flash/Pro 和思考开关，且不向客户端泄露内部名称。
- 会话 PATCH、新会话发送、已有会话默认值和运行中生成隔离符合契约。
- Message、Generation 和断流恢复响应保留实际模型与思考程度。
- 相同幂等 ID 与不同配置被拒绝，相同配置不重复调用上游。
- 迁移后旧会话与历史生成可继续访问，并统一显示为“5.5 + 标准”。
