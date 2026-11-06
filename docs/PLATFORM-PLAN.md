# Kite 多端规划：Android 原生 / 微信小程序 / 鸿蒙元服务

> 目标：同一套 Kite 智能体能力，按平台约束裁剪；原生端保留全部系统能力，小程序和元服务做“轻客户端 + 云端 Gateway”。

## 1. 总体结论

不要把小程序的代码或 WebView 塞进 Android，也不要把 Android 的 Kotlin 代码硬搬到鸿蒙。三端共享的是**协议、Agent 循环、工具定义和 UI 语言**，不是同一份 UI 代码。

推荐架构：

```
Android 原生（Kotlin/Compose）──┐
微信小程序（TS/WXML）──────────┼──> Kite Gateway（Node/Go）
鸿蒙元服务（ArkTS/ArkUI）──────┘        ├─ 模型代理（Key 只在服务端）
                                          ├─ MCP 代理（HTTP/SSE/OAuth）
                                          ├─ 服务端工具（搜索/网页/文件解析）
                                          ├─ 会话与配额
                                          └─ 内容安全/审计
```

原生端可以直连模型（现在就是这样），小程序/元服务必须走 Gateway，原因是：

- 小程序不能安全保存模型 Key，也不能直连任意 TCP/域名
- 元服务包体小、生命周期短，不适合跑长任务和本地工具循环
- MCP 的 OAuth、stdio、长连接放服务端更稳
- 三端统一计费、限流、内容审核

## 2. 共享协议（先定这个）

### 2.1 会话接口

```http
POST /v1/auth/wechat        { code }
POST /v1/auth/harmony       { accessToken }
POST /v1/sessions           -> { id, title }
GET  /v1/sessions
POST /v1/sessions/{id}/messages
     { text, attachments[], model, reasoningEffort }
     -> text/event-stream
GET  /v1/models?baseUrl=&key=   （仅调试；正式由服务端配置）
```

SSE 事件与原生端保持一致：

```json
{"type":"delta","text":"..."}
{"type":"reasoning","text":"..."}
{"type":"tool_start","call":{"id":"...","name":"web_search","arguments":"{}"}}
{"type":"tool_result","callId":"...","ok":true,"output":"..."}
{"type":"plan","items":[{"text":"...","status":"in_progress"}]}
{"type":"done"}
{"type":"error","message":"..."}
```

### 2.2 工具分层

| 层 | 工具 | 执行方 |
|---|---|---|
| 服务端工具 | web_search、web_fetch、http_request、MCP tools/resources/prompts、PDF/Office 解析 | Gateway |
| 客户端工具 | 选图、录音、文件选择、通讯录/日历/定位（需授权） | 小程序/元服务 |
| 原生专属 | 文件系统、Shell/root/Shizuku、无障碍跨 App、WebView 浏览器 | Android personal |

客户端工具采用“服务端发起、客户端执行、结果回传”的一轮：

```
assistant tool_call(local_tool)
  -> Gateway 把 call 发给客户端
  -> 客户端执行，POST /v1/runs/{id}/tool_result
  -> Gateway 继续 Agent 循环
```

## 3. 微信小程序版本

### 3.1 定位

“随身轻量 Kite”：查资料、读网页、整理文本、看图、语音输入、远程 MCP、简单计划。不做跨 App 自动化、Shell、任意文件系统。

### 3.2 技术栈

- 原生小程序：TypeScript + WXML/WXSS + 自定义组件
- 状态：轻量 store（`mobx-miniprogram` 或自写 observable）
- 流式：`wx.request` 的 `enableChunked`（基础库 2.20+）解析 SSE；旧库降级为 WebSocket
- 上传：`wx.chooseMedia` 图片、`wx.chooseMessageFile` 文档；先传 Gateway，服务端解析
- 语音：`wx.getRecorderManager()` 录音，上传 Gateway 做 ASR（SenseVoice/Whisper），不在端上跑模型
- 存储：`wx.setStorage` 保存会话摘要；完整会话在 Gateway
- 登录：`wx.login` -> code2session -> 自建 token

### 3.3 页面

```
pages/home        品牌首页 + 最近任务 + 输入框
pages/chat        对话、工具卡片、计划面板、图片/文件
pages/settings    模型/思考模式/MCP/隐私
pages/mcp         服务器列表、添加、测试
```

UI 复刻原生端的黑曜石 + 扫描红 + KITT 车头，但用小程序组件重画。

### 3.4 能力

- 文本/图片/语音输入
- 流式回复、思考内容折叠、停止
- 计划面板、子智能体（服务端串行/并行）
- 网页搜索/抓取、HTTP（Gateway 代理）
- 远程 MCP（tools/resources/prompts，OAuth 在 Gateway）
- 文件：图片、PDF、Word、Excel、TXT；服务端解析成文本
- 会话列表/重命名/删除/搜索
- 分享卡片、订阅消息（长任务完成通知）

### 3.5 明确不做

- Shell/root/Shizuku
- 跨 App 点击/读屏
- 任意本地文件系统
- 后台常驻 Agent
- 未备案域名/未审核的 web-view

### 3.6 合规

- 域名备案 + 小程序后台白名单
- AI 生成内容安全：`msgSecCheck`、敏感词、人工申诉入口
- 用户协议/隐私政策，说明模型服务商与 MCP 数据流向
- 不要在小程序里暴露用户自己的 API Key（统一用 Gateway 配额）

### 3.7 里程碑（建议 4–6 周）

1. Week 1：Gateway 会话/SSE/模型代理
2. Week 2：首页/对话/设置 UI，文本流式
3. Week 3：图片、文件上传与解析，计划面板
4. Week 4：MCP 代理、语音输入、订阅消息
5. Week 5：内容安全、埋点（可关）、真机兼容
6. Week 6：提审、灰度

## 4. 鸿蒙元服务版本

### 4.1 定位

“免安装的 Kite 卡片 + 轻对话”：桌面卡片一句话发起任务，点开进入元服务对话；适合查资料、日程、翻译、简单整理。不做重文件系统和跨 App 自动化。

### 4.2 技术栈

- ArkTS + ArkUI（Stage 模型）
- 元服务工程：entry HAP + 卡片 FormExtensionAbility
- 网络：`@ohos.net.http`（流式 `dataReceive`）或 `@ohos.net.webSocket`
- 浏览器：ArkUI `Web` 组件（受限，仅展示/简单交互）
- 文件：`@ohos.file.picker` / `photoAccessHelper`
- 语音：Core Speech Kit（ASR/TTS），或录音上传 Gateway
- 存储：`@ohos.data.preferences` + Gateway 会话
- 分发：AppGallery Connect 元服务，免安装；卡片即服务入口

### 4.3 形态

```
卡片：Kite 车头 + “今天要做什么？” + 3 个快捷指令
  -> 点卡片进入元服务
  -> 对话页（复用协议）
  -> 结果可生成卡片/通知
```

### 4.4 能力

- 文本/图片/语音
- 流式回复、计划、子智能体
- 服务端 web/MCP/文件解析
- 本地：日历、联系人、定位、通知（按权限）
- 分享/卡片刷新
- 华为账号登录（可选）

### 4.5 限制

- 元服务生命周期短，不能常驻后台跑长任务；长任务交给 Gateway，完成后订阅消息/卡片刷新
- 包体限制严格，不能塞大模型/大依赖
- 无障碍扩展能力在元服务上不可依赖
- 不能执行 Shell、不能任意读写文件
- 审核对 AI 生成内容、隐私、权限说明要求高

### 4.6 里程碑（建议 5–7 周）

1. Week 1：DevEco 工程、卡片、Gateway 登录/会话
2. Week 2：对话页、SSE、设置
3. Week 3：图片/语音、文件选择、计划
4. Week 4：MCP/网页/文件解析接入
5. Week 5：卡片快捷指令、通知、权限
6. Week 6：隐私/内容合规、真机
7. Week 7：AppGallery 提审

## 5. 能力矩阵

| 能力 | Android personal | Android Play | 微信小程序 | 鸿蒙元服务 |
|---|---|---|---|---|
| 模型对话/流式 | ✅ | ✅ | ✅ | ✅ |
| 图片理解 | ✅ | ✅ | ✅ | ✅ |
| 语音输入 | ✅ | ✅ | ✅ | ✅ |
| 文件读写 | ✅ 本地 | 沙盒 | 选择文件/云端 | 选择文件/云端 |
| Shell/root/Shizuku | ✅ | ❌ | ❌ | ❌ |
| 跨 App 无障碍 | ✅ | ❌ | ❌ | ❌ |
| WebView 浏览器 | ✅ | ✅ | 受限 | 受限 |
| MCP | ✅ 直连 | ✅ 直连 | ✅ Gateway | ✅ Gateway |
| 计划/子智能体 | ✅ | ✅ | ✅ | ✅ |
| 后台长任务 | ✅ 前台服务 | ✅ | Gateway | Gateway |
| 定时任务 | 待做 | 待做 | 订阅消息 | 卡片/通知 |
| 分享入口 | 待做 | 待做 | 分享卡片 | 卡片 |

## 6. 统一后端（先做）

最小 Gateway：

- Node.js（Fastify/NestJS）或 Go
- Redis：会话、限流、任务状态
- 对象存储：图片/文件
- 模型 Key 只在服务端；按用户配额
- MCP 连接池/OAuth 回调
- SSE 网关（Nginx 关闭缓冲）
- 内容安全：文本/图片审核
- 观测：请求日志、耗时、错误率（不记录对话正文）

接口优先级：

1. `POST /v1/auth/*`
2. `POST /v1/sessions` + `POST /v1/sessions/{id}/messages`（SSE）
3. `POST /v1/uploads` + 文件解析
4. `GET /v1/models`（可选）
5. MCP 代理
6. 客户端工具回调

## 7. 建议顺序

1. **先把 Android 原生稳定版发出去**（当前 0.1.0，补崩溃/无障碍后再上）
2. **做 Gateway**（2–3 周），原生端也可切到 Gateway 作为可选模式
3. **微信小程序 MVP**（4–6 周），先文本+网页+MCP，再加文件/语音
4. **鸿蒙元服务 MVP**（5–7 周），先卡片+文本，再加语音/文件
5. 三端共用一份工具协议和提示词，UI 各自实现

## 8. 风险

- **微信审核**：AI 对话、MCP 远程工具、文件解析都容易被问；先做最小闭环
- **鸿蒙元服务**：包体/生命周期/审核比原生严；不要承诺后台常驻
- **成本**：Gateway 统一 Key 后，必须限流/配额，否则被刷
- **安全**：MCP 是任意远程工具，服务端要域名白名单/超时/审计
- **商标**：KITT 只是致敬，图标和命名不要用 KITT 车标/剧照

## 9. 一句话

原生端继续做“全能力个人助手”；小程序和鸿蒙元服务做“Gateway 驱动的轻助手”。先统一协议和后端，再分别做 UI，不要三端各写一套 Agent。
