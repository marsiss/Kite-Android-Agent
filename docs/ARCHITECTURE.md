# Kite 架构

> 面向要改代码的人。读完你应该知道：一次对话从点击发送到工具执行完，中间经过了哪些文件。

## 1. 分层

```
┌─────────────────────────────────────────────────────┐
│  ui/            Compose 界面，只跟 AppViewModel 说话 │
├─────────────────────────────────────────────────────┤
│  AppViewModel   会话状态、事件归并、持久化调度       │
├─────────────────────────────────────────────────────┤
│  agent/         Agent 循环、LLM 客户端、工具、提示词 │
├─────────────────────────────────────────────────────┤
│  mcp/  access/  browser/    外部能力适配层           │
├─────────────────────────────────────────────────────┤
│  data/          Settings / Session / Crypto / 解析   │
└─────────────────────────────────────────────────────┘
```

**硬规则**

- `ui/` 不直接碰 `data/`，一切经过 `AppViewModel`
- `agent/` 不依赖 `ui/`
- `data/` 不依赖任何上层
- `agent/Tools.kt` 可以调用 `data/` 和 `mcp/`，但不能弹 UI —— 需要用户确认时走 `ApprovalGate` 挂起

违反这些规则的直接后果是：单元没法测、后台任务（`ScheduledWorker`）会因为没有 Activity 而崩。

---

## 2. 一次对话的完整流程

```
用户点发送
   │
   ▼
AppViewModel.send()
   │  组装 Message 列表（系统提示词 + 历史 + 用户输入）
   ▼
AgentEngine.run()
   │
   ├─► 上下文压缩检查：估算 token > 24k ？→ 压缩旧消息，保留最近 30 条
   │
   ├─► LlmClient.stream()
   │      POST /chat/completions  stream=true
   │      SSE 逐块解析：
   │        delta.content          → 追加到气泡
   │        delta.reasoning_content → 追加到「思考过程」折叠区
   │        delta.tool_calls[]     → 按 index 累积 arguments 片段
   │      ── 流结束 ──
   │
   ├─► 有 tool_calls ？
   │      否 → 结束，落盘会话
   │      是 ↓
   │
   ├─► 对每个 tool_call：
   │      ├─ 危险工具？→ ApprovalGate.request() 挂起，等 UI 回执
   │      │                 120 秒无响应按「拒绝」处理
   │      ├─ 破坏性写？→ UndoStore.backup() 先备份
   │      └─ Tools.execute() 执行，结果截断到上限
   │
   ├─► 工具结果作为 role=tool 消息追加，回到 LlmClient.stream()
   │      （循环，最多 N 轮）
   │
   └─► 落盘 SessionStore，通知 UI
```

**关键文件**

| 步骤 | 文件 |
|---|---|
| 组装消息、估算 token | `agent/AgentEngine.kt` |
| SSE 解析 | `agent/LlmClient.kt` |
| 工具 schema 与执行 | `agent/Tools.kt` |
| 系统提示词 | `agent/Prompts.kt` |
| 审批 | `agent/ApprovalGate.kt` |
| 备份 | `data/UndoStore.kt` |
| 落盘 | `data/SessionStore.kt` |

---

## 3. 工具系统

工具是**一个 JSON Schema + 一个执行分支**，刻意没有做插件化抽象。

### 3.1 定义

在 `Tools.kt` 的 `toolDefinitions()` 里返回 OpenAI function 格式：

```kotlin
fun(name = "read_file", description = "...") {
    string("path", "相对工作区的路径", required = true)
    integer("start_line", "起始行（从 1 开始）")
    integer("end_line", "结束行")
}
```

### 3.2 执行

同文件的 `execute(name, args)` 是一个大 `when`。加分支即可。

### 3.3 三个必须遵守的约定

1. **危险工具要登记** —— 加进危险集合，`ApprovalGate` 才会拦。
2. **破坏性写要先备份** —— 调用 `UndoStore.backup(path)`，`undo_last` 才救得回来。
3. **必须在 `Prompts.kt` 里登记** —— 系统提示词里有一份工具行为说明。不登记的话模型不知道什么时候该用它，会乱试。

### 3.4 输出截断

工具输出直接进上下文，不截断会瞬间撑爆 token 预算。所有工具返回前都要过截断，
文档解析的上限是 **12 万字符**。

---

## 4. 上下文管理

没有引入 tokenizer，用的是**字符数估算**（中文按 1 token ≈ 1 字符，英文按 4 字符 ≈ 1 token 加权）。

超过约 **24k token** 时压缩：

- 保留系统提示词（永远不压缩）
- 保留最近 **30 条**消息
- 更早的消息折叠成一条摘要式系统消息

这是有损的。如果你发现模型「忘了前面说过什么」，先看是不是触发了压缩。

---

## 5. MCP

```
McpStore (加密保存服务器配置)
   │
   ▼
McpManager   按服务器维护连接，启动时 initialize + tools/list
   │
   ├─► 工具以 mcp__<server>__<tool> 注册进 Tools 的可用列表
   ├─► resources / prompts 供 UI 浏览
   │
   ▼
McpClient    JSON-RPC over HTTP
   ├─ Streamable HTTP：POST /mcp，响应可能是 JSON 或 SSE 流
   └─ 旧版 SSE：GET /sse 建流 + POST /messages 发请求
```

**当前没做的**：OAuth 自动授权（只有 Header Token）、stdio 传输、连接重试退避、采样（sampling）。

---

## 6. 两个构建变体

```
app/src/main/        ← 两个版本共用
app/src/personal/    ← 只有 personal 编译
```

`app/build.gradle.kts` 里：

```kotlin
buildTypes {
    release  { buildConfigField("boolean", "PERSONAL", "false") }
    personal { buildConfigField("boolean", "PERSONAL", "true")
               versionNameSuffix = "-personal" }
}
```

代码里用 `BuildConfig.PERSONAL` 分支，或把整个文件放 `app/src/personal/`。

**最容易踩的坑**：让 Play 版编译路径依赖 personal 的类。`app/src/main/` 里**不能**直接 import
`app/src/personal/` 下的任何东西 —— 编译 `release` 时会直接报 unresolved reference。

CI 会同时构建三个变体，就是为了拦这个。

### 权限差异

| | release | personal |
|---|---|---|
| 权限数量 | 6 | 54 |
| `MANAGE_EXTERNAL_STORAGE` | ❌ | ✅ |
| 无障碍服务 | ❌ | ✅ |
| Shizuku provider | ❌ | ✅ |
| 短信 / 通话 / 通讯录 / 定位 | ❌ | ✅ |

---

## 7. 安全边界

| 机制 | 实现位置 |
|---|---|
| 密钥加密 | `data/Crypto.kt`（Android Keystore AES-GCM） |
| 路径穿越拦截 | `data/LocalFiles.kt` |
| 工具审批 | `agent/ApprovalGate.kt` |
| 撤销备份 | `data/UndoStore.kt` |
| 前台保活 | `AgentService.kt` |

**信任模型**：Kite 信任用户配置的模型服务商。模型输出的工具调用会被执行（危险操作需确认）。
连了恶意端点，它就能读你工作区里的所有东西。这是 Agent 的固有性质，不是 bug。

详见 [SECURITY.md](../SECURITY.md)。

---

## 8. 加东西的路径

| 想做什么 | 改哪里 |
|---|---|
| 加一个工具 | `agent/Tools.kt` + `agent/Prompts.kt`（见 [CONTRIBUTING.md](../CONTRIBUTING.md#加新工具)） |
| 加一个模型服务商预设 | `agent/ModelCatalog.kt` + `data/Settings.kt` |
| 加一个界面 | `ui/` 新建 Screen + `ui/KiteRoot.kt` 加路由 |
| 改配色 | `ui/theme/Theme.kt` |
| 加一种文档格式 | `data/DocumentParser.kt` |
| 改系统提示词 | `agent/Prompts.kt` |
| 加一个 MCP 传输方式 | `mcp/McpClient.kt` |
| 加依赖 | 三处同步，见 [CONTRIBUTING.md](../CONTRIBUTING.md#加新依赖) |
