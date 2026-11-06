package app.kite.agent.data

data class OpenSourceItem(
    val name: String,
    val version: String,
    val license: String,
    val url: String,
    val purpose: String,
)

/**
 * Attribution for third-party open source used by Kite.
 * Kite is an independent implementation; no DSH/OpenCode source code is copied.
 */
val openSourceItems = listOf(
    OpenSourceItem(
        "Kotlin",
        "2.1.20",
        "Apache License 2.0",
        "https://github.com/JetBrains/kotlin",
        "Android 应用开发语言与标准库",
    ),
    OpenSourceItem(
        "AndroidX / Jetpack",
        "Core 1.15.0 · Activity 1.10.1",
        "Apache License 2.0",
        "https://github.com/androidx/androidx",
        "应用生命周期、Activity、DocumentFile、WorkManager",
    ),
    OpenSourceItem(
        "Jetpack Compose",
        "BOM 2025.04.01",
        "Apache License 2.0",
        "https://github.com/androidx/androidx",
        "全部界面与交互",
    ),
    OpenSourceItem(
        "Material 3",
        "Compose BOM 2025.04.01",
        "Apache License 2.0",
        "https://github.com/material-components/material3",
        "Material 3 组件与图标",
    ),
    OpenSourceItem(
        "Kotlin Coroutines",
        "1.10.1",
        "Apache License 2.0",
        "https://github.com/Kotlin/kotlinx.coroutines",
        "异步任务、流式响应、后台任务",
    ),
    OpenSourceItem(
        "OkHttp",
        "4.12.0",
        "Apache License 2.0",
        "https://github.com/square/okhttp",
        "模型与 MCP 的 HTTP/SSE 网络请求",
    ),
    OpenSourceItem(
        "Gson",
        "2.11.0",
        "Apache License 2.0",
        "https://github.com/google/gson",
        "JSON 序列化与会话持久化",
    ),
    OpenSourceItem(
        "Jsoup",
        "1.18.3",
        "MIT License",
        "https://github.com/jhy/jsoup",
        "网页正文提取",
    ),
    OpenSourceItem(
        "PDFBox-Android",
        "2.0.27.0",
        "Apache License 2.0",
        "https://github.com/TomRoush/PdfBox-Android",
        "PDF 文本解析",
    ),
    OpenSourceItem(
        "Shizuku API",
        "13.1.5",
        "Apache License 2.0",
        "https://github.com/RikkaApps/Shizuku",
        "个人版的 shell UID 能力",
    ),
)

const val KITE_ATTRIBUTION_NOTE =
    "Kite 是独立实现，未复制 DSH Desktop 或 OpenCode 的源代码。" +
        "只在功能形态与工具行为上做兼容参考；上游项目及其贡献者保留各自权利。"

/**
 * Projects we study for behavior/architecture but do not ship code from.
 * Kept separate from dependencies so the attribution is honest.
 */
val referenceProjects = listOf(
    OpenSourceItem(
        "Mika",
        "reference",
        "Apache License 2.0",
        "https://github.com/wangzan101/Mika",
        "Android Agent 形态、技能与后台屏幕设计参考；未复制代码",
    ),
    OpenSourceItem(
        "Operit",
        "reference",
        "LGPL-3.0-only",
        "https://github.com/AAswordman/Operit",
        "功能清单参考；因 LGPL 未复制代码",
    ),
    OpenSourceItem(
        "OpenCode",
        "reference",
        "MIT License",
        "https://github.com/anomalyco/opencode",
        "Agent 会话/工具行为参考；未复制代码",
    ),
    OpenSourceItem(
        "Koog",
        "evaluating",
        "Apache License 2.0",
        "https://github.com/JetBrains/koog",
        "Kotlin Agent 框架评估中；尚未加入依赖",
    ),
    OpenSourceItem(
        "MCP Kotlin SDK",
        "evaluating",
        "Apache-2.0 / MIT transition",
        "https://github.com/modelcontextprotocol/kotlin-sdk",
        "官方 MCP Kotlin SDK 评估中；尚未替换现有客户端",
    ),
)
