# ChatXP 登录/注册后端开发计划

> 开发版本：`v1.2.0-dev`<br>
> 文档状态：后端开发基线<br>
> 前端计划：[`../frontend/02-login-registration-plan.md`](../frontend/02-login-registration-plan.md)

## 1. 目标与范围

本版本在现有设备游客身份上增加可选的邮箱账号能力。登录不是使用 ChatXP 的前置条件，游客可以完整使用文本聊天；登录或注册用于长期保存和恢复账号会话。

本期实现：

- 游客进入登录/注册页。
- 用户名、邮箱和密码注册。
- 邮箱和密码登录。
- 登录或注册后保留登录前的游客会话。
- 登录状态跨 App 启动保持。
- 当前设备退出登录并立即切换为新游客。
- 游客头像和登录用户文字头像。

本期不实现：

- 邮箱验证码和邮箱真实性验证。
- 忘记密码与密码找回接口。
- 用户名、邮箱或密码修改。
- 账号注销、退出全部设备和多账号快捷切换。
- 完整个人中心页面。

## 2. 前端边界

页面布局、表单状态、头像、账号面板和主题适配由[前端登录/注册计划](../frontend/02-login-registration-plan.md)定义。本文件只定义服务端身份状态、接口、持久化、迁移和安全规则。

## 3. 身份状态与数据流

客户端统一使用以下身份状态：

```text
Initializing
    ├── Guest
    │     ├── register success ──> Registered
    │     └── login success ─────> Registered
    ├── Registered
    │     └── logout success ────> Guest（新游客）
    └── Error
```

### 3.1 匿名初始化

1. Android 读取或生成 installation ID 与 installation secret。
2. 使用 `/auth/anonymous` 恢复该安装当前绑定的用户。
3. installation 可能绑定游客，也可能绑定此前已登录的注册用户。
4. token 和 `AuthUser` 原子写入 DataStore。

### 3.2 游客注册

1. 客户端使用当前游客 Bearer token 调用注册接口。
2. 服务端在原游客用户记录上补充账号字段并切换为 `registered`。
3. 原 user ID 不变，因此原游客会话、消息和生成记录天然保留。
4. 服务端提升 installation token version、吊销该安装的旧 refresh token并签发新 token。
5. 客户端原子替换 token 和用户资料，返回新的空白对话并刷新侧边栏。

### 3.3 游客登录已有账号

1. 服务端验证邮箱和密码。
2. 在单一事务中把当前游客的 sessions、messages 和 generations 归属转移到目标账号。
3. 保留双方全部会话的 ID、标题、模型、置顶状态和时间，不按标题或内容去重。
4. 将当前 installation 改绑目标账号，吊销游客 refresh token并提升 token version。
5. 删除已无资源和 installation 的游客用户。
6. 客户端替换身份并重新加载账号会话列表。

### 3.4 退出登录

1. 用户在账号面板点击“退出登录”并确认。
2. 服务端仅吊销当前 installation 的 refresh token并提升其 token version。
3. 服务端创建新的游客用户并将当前 installation 改绑新游客。
4. 账号会话保留在账号内，其他设备登录状态不受影响。
5. 服务端返回新游客 token 和用户资料。
6. Android 清除账号缓存、选中会话及活动请求，写入游客身份并进入空白对话。
7. 新游客之后产生的会话会在下次登录时继续合并到账号。

### 3.5 活跃生成限制

当前用户存在 `queued` 或 `streaming` generation 时，不允许登录、注册或退出登录。服务端返回 `409 AUTH_TRANSITION_BUSY`，Android 提示“请等待当前回复完成后再操作”。

## 4. 公共接口

所有路径使用 `/api/v1` 前缀。除匿名初始化和 refresh 外，以下账号接口均需要 Bearer token。

### 4.1 AuthUser

| 字段 | 类型 | 必填 | 说明 |
|---|---|---|---|
| `id` | UUID | 是 | 当前用户 ID |
| `account_type` | `guest` / `registered` | 是 | 身份类型 |
| `display_name` | string/null | 是 | 游客为 `null` |
| `email` | string/null | 是 | 游客为 `null` |
| `avatar_text` | string/null | 是 | 游客为 `null` |

所有 token 响应统一包含：

```json
{
  "data": {
    "user_id": "uuid",
    "user": {
      "id": "uuid",
      "account_type": "registered",
      "display_name": "小明",
      "email": "name@example.com",
      "avatar_text": "小"
    },
    "access_token": "eyJ...",
    "token_type": "Bearer",
    "expires_in": 3600,
    "refresh_token": "opaque-refresh-token",
    "refresh_expires_in": 7776000
  }
}
```

### 4.2 初始化或恢复 installation

`POST /api/v1/auth/anonymous`

保留现有请求字段：`installation_id`、`installation_secret`、`platform`、`app_version`。服务端根据 installation 当前绑定关系返回游客或注册用户。

### 4.3 当前用户

`GET /api/v1/auth/me`

响应为 `DataEnvelope<AuthUser>`，用于启动恢复、侧边栏头像和账号面板。

### 4.4 注册

`POST /api/v1/auth/register`

请求：

```json
{
  "display_name": "小明",
  "email": "name@example.com",
  "password": "example-password"
}
```

成功返回新的 token 响应。当前用户必须为游客。

### 4.5 登录

`POST /api/v1/auth/login`

请求：

```json
{
  "email": "name@example.com",
  "password": "example-password"
}
```

成功完成游客会话合并并返回目标账号的新 token 响应。登录失败统一返回 `INVALID_CREDENTIALS`。

### 4.6 退出登录

`POST /api/v1/auth/logout`

无请求体。成功创建并返回新游客的 token 响应。当前用户必须为注册用户。

### 4.7 刷新令牌

`POST /api/v1/auth/refresh`

保留 `refresh_token` 请求字段，响应增加 `user`。refresh token 必须同时匹配 user 和 installation；成功后继续执行轮换。

## 5. 错误契约

| HTTP | 错误码 | Android 行为 |
|---|---|---|
| 400 | `VALIDATION_ERROR` | 映射到对应字段 |
| 401 | `INVALID_CREDENTIALS` | 显示“邮箱或密码错误” |
| 409 | `EMAIL_ALREADY_REGISTERED` | 提示该邮箱已注册并可切换登录 |
| 409 | `ALREADY_AUTHENTICATED` | 刷新当前身份并返回聊天 |
| 409 | `AUTH_TRANSITION_BUSY` | 提示等待当前回复完成 |
| 429 | `LOGIN_RATE_LIMITED` | 提示稍后重试 |

登录失败按“规范化邮箱 + installation”计数：15 分钟内最多失败 5 次。达到限制后返回 `LOGIN_RATE_LIMITED`。V1 单实例使用内存计数，服务重启后计数可重置。

## 6. 服务端数据模型

### 6.1 users

| 字段 | 约束 |
|---|---|
| `id` | UUID 主键 |
| `account_type` | `guest` 或 `registered` |
| `display_name` | 注册用户非空，最长 30 |
| `email_normalized` | 注册用户唯一；游客为空 |
| `password_hash` | 注册用户非空；游客为空 |
| `created_at` | UTC 时间 |
| `updated_at` | UTC 时间 |

### 6.2 installations

| 字段 | 约束 |
|---|---|
| `installation_id` | UUID 主键 |
| `installation_secret_hash` | 非空，只保存 keyed hash |
| `user_id` | FK `users.id`，非空 |
| `token_version` | 整数，默认 1 |
| `platform` | 当前为 `android` |
| `app_version` | 非空 |
| `created_at` | UTC 时间 |
| `last_seen_at` | UTC 时间 |

一个注册账号可以绑定多个 installations；一个 installation 同时只绑定一个用户。

### 6.3 refresh_tokens

在现有字段基础上增加 `installation_id` 外键。退出当前设备时只吊销该 installation 下的 refresh token，不影响同一账号的其他 installation。

### 6.4 资源归属

`sessions`、`messages` 和 `generations` 继续保存 `user_id`。登录合并时三类记录必须在同一事务内更新为目标账号 ID。

## 7. 认证与安全

- 密码使用 Argon2id 哈希，不可加密后可逆保存。
- 邮箱只在注册、登录和当前用户接口中使用，不写入普通业务日志。
- Access token包含 `sub`、`installation_id`、`token_version`、`iat`、`exp` 和 `jti`。
- 每次 Bearer 鉴权同时校验 installation 仍绑定 token用户且 token version一致。
- 登录和注册成功后提升 token version，使旧 access token立即失效。
- 登录错误统一使用 `INVALID_CREDENTIALS`，避免账号枚举。
- 不记录原始密码、installation secret、access token或 refresh token。
- 外网环境必须使用 HTTPS。

## 8. 数据迁移

Alembic 迁移必须：

1. 将 `anonymous_users` 迁移为 `users`，保留原 `id`，全部标记为 `guest`。
2. 为每条原匿名用户记录创建 installation，迁移 installation ID、secret hash、平台、版本和时间。
3. 为 refresh token补充对应 installation ID。
4. 保持 sessions、messages 和 generations 的 user ID 不变。
5. 重建必要的外键和唯一索引。
6. 校验迁移前后用户数、会话数、消息数、generation 数和外键完整性。

已安装 Android 客户端保存的 installation ID 与 secret继续有效；升级后第一次匿名初始化不得创建重复游客或丢失历史会话。

## 9. 测试与验收

### 服务端

- 现有游客数据迁移后仍可访问。
- 游客注册后 user ID 和全部会话保持不变。
- 游客登录已有账号后，双方会话完整合并且不复制重复记录。
- 当前设备退出不影响同账号其他设备。
- 退出后旧 access/refresh token立即失效并返回新游客。
- 新游客再次登录时，新产生的会话继续合并。
- 活跃 generation期间登录、注册和退出返回 `AUTH_TRANSITION_BUSY`。
- 重复邮箱、错误密码、登录限流和跨用户访问符合错误契约。

### 端到端验收

1. 游客创建对话后可以注册，注册完成后仍能看到该对话。
2. 游客创建对话后可以登录已有账号，同时看到账号历史与游客对话。
3. 用户退出后进入空白游客状态，账号对话不在游客侧边栏出现。
4. 再次登录后，退出期间的新游客对话被合并回账号。
5. App 重启保持登录身份，但主页面仍是新建对话。
