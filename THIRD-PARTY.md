# 第三方组件与开源致谢

Kite 是独立实现，**没有复制** DSH Desktop、OpenCode、Mika 或 Operit 的源代码。
本文件列出真正被打包进 App 的依赖，以及只做参考的项目 —— 两者分开写，避免混淆。

应用内同样可以查看：**设置 → 关于 → 开源许可**（`data/OpenSource.kt` 是唯一数据源）。

---

## 一、运行时依赖（真实打包）

| 组件 | 版本 | 许可证 | 用途 |
|---|---|---|---|
| [Kotlin](https://github.com/JetBrains/kotlin) | 2.1.20 | Apache-2.0 | 开发语言与标准库 |
| [AndroidX / Jetpack](https://github.com/androidx/androidx) | Core 1.15.0 · Activity 1.10.1 · Lifecycle 2.8.7 · DocumentFile 1.0.1 | Apache-2.0 | 生命周期、Activity、SAF 文件访问 |
| [WorkManager](https://developer.android.com/topic/libraries/architecture/workmanager) | 2.10.0 | Apache-2.0 | 定时任务调度 |
| [Jetpack Compose](https://github.com/androidx/androidx) | BOM 2025.04.01 | Apache-2.0 | 全部界面 |
| [Material 3](https://github.com/material-components/material3) | Compose BOM 2025.04.01 | Apache-2.0 | 组件与图标 |
| [kotlinx.coroutines](https://github.com/Kotlin/kotlinx.coroutines) | 1.10.1 | Apache-2.0 | 异步、流式响应 |
| [OkHttp](https://github.com/square/okhttp) | 4.12.0 | Apache-2.0 | 模型 / MCP 的 HTTP 与 SSE |
| [Gson](https://github.com/google/gson) | 2.11.0 | Apache-2.0 | JSON 序列化、会话持久化 |
| [Jsoup](https://github.com/jhy/jsoup) | 1.18.3 | MIT | 网页正文提取 |
| [PDFBox-Android](https://github.com/TomRoush/PdfBox-Android) | 2.0.27.0 | Apache-2.0 | PDF 文本解析 |
| [Shizuku API](https://github.com/RikkaApps/Shizuku) | 13.1.5 | Apache-2.0 | 个人版 shell（UID 2000）能力 |

> PDFBox-Android 是 Apache PDFBox 的 Android 移植版，上游为 Apache Software Foundation。
> DOCX / XLSX / PPTX 的解析是 Kite 自己用 `java.util.zip` + `XmlPullParser` 实现的，没有引入 Apache POI。

---

## 二、参考项目（**未复制代码**）

这些项目用来对照功能形态、协议行为和架构取舍。Kite 的代码是重新写的。

| 项目 | 许可证 | 参考了什么 | 复制代码 |
|---|---|---|---|
| [Mika](https://github.com/wangzan101/Mika) | Apache-2.0 | Android Agent 形态、技能（Skills）组织方式、后台屏幕能力边界 | ❌ 无 |
| [Operit](https://github.com/AAswordman/Operit) | LGPL-3.0-only | 功能清单对照（无障碍自动化、工具集范围） | ❌ 无（LGPL 代码不引入） |
| [OpenCode](https://github.com/anomalyco/opencode) | MIT | Agent 会话循环、工具调用交互 | ❌ 无 |

### 为什么专门说明「未复制」

- **Operit 是 LGPL-3.0**：一旦复制其代码，Kite 就必须整体按 LGPL 分发。Kite 只做功能对照，不引入其源码。
- **Mika 是 Apache-2.0**：法律上可以复制，但 Kite 选择独立实现，以保证架构一致性和 Apache-2.0 的干净授权链。
- **OpenCode 是 MIT**：同理，只参考行为。

---

## 三、评估中的项目（尚未成为依赖）

| 项目 | 许可证 | 状态 |
|---|---|---|
| [Koog](https://github.com/JetBrains/koog) | Apache-2.0 | 评估中，尚未加入依赖 |
| [MCP Kotlin SDK](https://github.com/modelcontextprotocol/kotlin-sdk) | Apache-2.0 | 评估中，尚未替换现有 `mcp/McpClient.kt` |

Kite 当前的 MCP 实现是自行编写的 Streamable HTTP + 旧版 SSE 客户端（`app/src/main/java/app/kite/agent/mcp/`）。
如果将来迁移到官方 Kotlin SDK，会同步更新本文件和应用内许可页。

---

## 四、商标声明

- **KITT / Knight Rider** 商标归其各自权利人所有。Kite 的界面是原创设计，只做风格致敬，**不包含** KITT 的剧照、车标或任何官方素材。
- **Android / Google Play / Kotlin** 是 Google LLC 与 JetBrains 的商标。
- Kite 与上述权利人无隶属、赞助或背书关系。

---

## 五、如果你要分发 Kite

Apache-2.0 要求你在分发时：

1. 附带一份 `LICENSE`；
2. 保留本 `NOTICE` 文件中的归属声明；
3. 如果修改了文件，标注你做过修改。

详见 [LICENSE](LICENSE) 第 4 节。
