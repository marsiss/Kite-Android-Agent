package app.kite.agent.agent

import app.kite.agent.BuildConfig
import app.kite.agent.R
import app.kite.agent.SystemAccess
import app.kite.agent.access.A11y
import app.kite.agent.browser.BrowserController
import app.kite.agent.data.ToolCall
import app.kite.agent.data.LocalFiles
import app.kite.agent.data.UndoStore
import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.LocationManager
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.os.BatteryManager
import android.os.Build
import android.os.Environment
import android.os.StatFs
import android.provider.AlarmClock
import android.provider.CalendarContract
import android.provider.ContactsContract
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.net.URLEncoder
import java.util.Locale
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import rikka.shizuku.Shizuku

data class ToolResult(val ok: Boolean, val output: String)

private fun prop(type: String, description: String): JsonObject = JsonObject().apply {
    addProperty("type", type)
    addProperty("description", description)
}

private fun schema(properties: Map<String, JsonObject>, required: List<String>): JsonObject {
    val props = JsonObject()
    properties.forEach { (key, value) -> props.add(key, value) }
    return JsonObject().apply {
        addProperty("type", "object")
        add("properties", props)
        add("required", JsonArray().apply { required.forEach { add(it) } })
    }
}

/**
 * Tool definitions exposed to the model and the local executor.
 */
object ToolDefs {

    val definitions: JsonArray by lazy {
        JsonArray().apply {
            add(function(
                "list_dir",
                "列出工作区中某个目录的文件和子目录。",
                schema(mapOf("path" to prop("string", "相对路径，默认 .")), emptyList()),
            ))
            add(function(
                "read_file",
                "读取文本文件，返回带行号的内容。",
                schema(
                    mapOf(
                        "path" to prop("string", "相对路径"),
                        "offset" to prop("integer", "起始行，从 1 开始，默认 1"),
                        "limit" to prop("integer", "最多读取行数，默认 200，最大 400"),
                    ),
                    listOf("path"),
                ),
            ))
            add(function(
                "write_file",
                "创建或覆盖一个文本文件。目录会自动创建。",
                schema(
                    mapOf(
                        "path" to prop("string", "相对路径"),
                        "content" to prop("string", "完整文件内容"),
                    ),
                    listOf("path", "content"),
                ),
            ))
            add(function(
                "replace_in_file",
                "在文件中把一段旧文本替换为新文本，返回替换次数。",
                schema(
                    mapOf(
                        "path" to prop("string", "相对路径"),
                        "old" to prop("string", "要被替换的原文"),
                        "new" to prop("string", "替换后的文本"),
                    ),
                    listOf("path", "old", "new"),
                ),
            ))
            add(function(
                "grep",
                "在工作区文本文件中搜索正则表达式，返回匹配行。",
                schema(
                    mapOf(
                        "pattern" to prop("string", "正则表达式"),
                        "path" to prop("string", "搜索目录，默认 ."),
                        "glob" to prop("string", "文件名过滤，默认 *"),
                    ),
                    listOf("pattern"),
                ),
            ))
            add(function(
                "run_shell",
                "在安卓设备的 Shell 中执行命令，返回 stdout/stderr。需要用户开启 Shell 权限；普通模式在应用沙盒内，Root/Shizuku 模式可访问系统命令。",
                schema(
                    mapOf(
                        "command" to prop("string", "要执行的命令"),
                        "cwd" to prop("string", "工作目录，默认 ."),
                    ),
                    listOf("command"),
                ),
            ))
            add(function(
                "web_fetch",
                "抓取一个网页并提取正文文本。",
                schema(mapOf("url" to prop("string", "http/https 地址")), listOf("url")),
            ))
            add(function(
                "web_search",
                "搜索网页，返回标题、链接和摘要。适合查资料、找文档。",
                schema(
                    mapOf(
                        "query" to prop("string", "搜索词"),
                        "max" to prop("integer", "结果数，默认 6，最大 10"),
                    ),
                    listOf("query"),
                ),
            ))
            add(function(
                "http_request",
                "发起 HTTP 请求，支持 GET/POST/PUT/PATCH/DELETE，返回状态码、响应头和响应体。",
                schema(
                    mapOf(
                        "method" to prop("string", "HTTP 方法，默认 GET"),
                        "url" to prop("string", "http/https 地址"),
                        "headers" to prop("string", "可选，JSON 对象，如 {\"Authorization\":\"Bearer x\"}"),
                        "body" to prop("string", "可选，请求体"),
                    ),
                    listOf("url"),
                ),
            ))
            add(function(
                "move_path",
                "移动或重命名文件/目录。",
                schema(mapOf("from" to prop("string", "源路径"), "to" to prop("string", "目标路径")), listOf("from", "to")),
            ))
            add(function(
                "copy_path",
                "复制文件或目录。",
                schema(mapOf("from" to prop("string", "源路径"), "to" to prop("string", "目标路径")), listOf("from", "to")),
            ))
            add(function(
                "delete_path",
                "删除文件或目录。删除目录需要 recursive=true。",
                schema(
                    mapOf(
                        "path" to prop("string", "路径"),
                        "recursive" to prop("boolean", "是否递归删除目录，默认 false"),
                    ),
                    listOf("path"),
                ),
            ))
            add(function(
                "make_dir",
                "创建目录（含多级）。",
                schema(mapOf("path" to prop("string", "目录路径")), listOf("path")),
            ))
            add(function(
                "file_info",
                "查看文件/目录的大小、修改时间和绝对路径。",
                schema(mapOf("path" to prop("string", "路径")), listOf("path")),
            ))
            add(function(
                "device_info",
                "读取手机型号、Android 版本、电量、存储、网络和屏幕信息。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "list_apps",
                "列出手机里可启动的应用，可按名称过滤。",
                schema(mapOf("query" to prop("string", "可选，名称/包名过滤")), emptyList()),
            ))
            add(function(
                "open_app",
                "按应用名或包名打开一个手机应用。",
                schema(mapOf("name" to prop("string", "应用名或包名")), listOf("name")),
            ))
            add(function(
                "open_url",
                "用系统浏览器打开 http/https 链接。",
                schema(mapOf("url" to prop("string", "http/https 地址")), listOf("url")),
            ))
            add(function(
                "clipboard_read",
                "读取系统剪贴板文本。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "clipboard_write",
                "把文本写入系统剪贴板。",
                schema(mapOf("text" to prop("string", "文本")), listOf("text")),
            ))
            add(function(
                "share_text",
                "调起系统分享面板分享一段文本。",
                schema(mapOf("text" to prop("string", "文本")), listOf("text")),
            ))
            add(function(
                "notify",
                "发一条系统通知。",
                schema(mapOf("title" to prop("string", "标题"), "text" to prop("string", "内容")), listOf("title", "text")),
            ))
            add(function(
                "speak",
                "用系统 TTS 朗读一段文本。",
                schema(mapOf("text" to prop("string", "要朗读的文本")), listOf("text")),
            ))
            add(function(
                "get_location",
                "读取设备最后一次已知位置（需要定位权限）。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "read_contacts",
                "按姓名或号码搜索通讯录（需要通讯录权限）。",
                schema(
                    mapOf(
                        "query" to prop("string", "姓名或号码关键字，空则列出前几条"),
                        "limit" to prop("integer", "最多返回条数，默认 10，最大 30"),
                    ),
                    emptyList(),
                ),
            ))
            add(function(
                "compose_sms",
                "打开发短信界面并填好号码和内容，由用户确认发送。",
                schema(mapOf("number" to prop("string", "号码"), "body" to prop("string", "短信内容")), listOf("number")),
            ))
            add(function(
                "dial_number",
                "打开拨号界面并填好号码，由用户确认拨打。",
                schema(mapOf("number" to prop("string", "号码")), listOf("number")),
            ))
            add(function(
                "create_calendar_event",
                "打开日历新建事件界面，预填标题和时间，由用户保存。",
                schema(
                    mapOf(
                        "title" to prop("string", "标题"),
                        "begin" to prop("integer", "开始时间，毫秒时间戳，默认一小时后"),
                        "minutes" to prop("integer", "时长分钟，默认 60"),
                        "location" to prop("string", "可选地点"),
                    ),
                    listOf("title"),
                ),
            ))
            add(function(
                "set_alarm",
                "打开系统闹钟界面，预填时间，由用户确认。",
                schema(
                    mapOf(
                        "hour" to prop("integer", "小时 0-23"),
                        "minute" to prop("integer", "分钟 0-59"),
                        "label" to prop("string", "可选标签"),
                    ),
                    listOf("hour", "minute"),
                ),
            ))
            add(function(
                "browser_open",
                "打开一个网页浏览器窗口并加载 URL，返回标题、地址和正文摘要。",
                schema(mapOf("url" to prop("string", "http/https 地址")), listOf("url")),
            ))
            add(function(
                "browser_read",
                "读取当前浏览器页面的正文。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "browser_click",
                "点击当前页面中 CSS 选择器匹配的元素，然后返回页面摘要。",
                schema(mapOf("selector" to prop("string", "CSS 选择器，如 a.more 或 #submit")), listOf("selector")),
            ))
            add(function(
                "browser_type",
                "在输入框中填写文本（自动触发 input/change 事件）。",
                schema(
                    mapOf(
                        "selector" to prop("string", "CSS 选择器"),
                        "text" to prop("string", "要填写的文本"),
                    ),
                    listOf("selector", "text"),
                ),
            ))
            add(function(
                "browser_back",
                "浏览器后退一页。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "browser_close",
                "关闭浏览器窗口。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "local_list",
                "列出用户通过系统文件选择器授权的本地文件夹。无需所有文件权限。",
                schema(mapOf("path" to prop("string", "相对路径，空为根")), emptyList()),
            ))
            add(function(
                "local_read",
                "读取用户授权的本地文件夹里的文本文件。",
                schema(mapOf("path" to prop("string", "相对路径")), listOf("path")),
            ))
            add(function(
                "local_write",
                "写入用户授权的本地文件夹；自动创建父目录。",
                schema(
                    mapOf(
                        "path" to prop("string", "相对路径"),
                        "content" to prop("string", "文件内容"),
                    ),
                    listOf("path", "content"),
                ),
            ))
            add(function(
                "local_mkdir",
                "在用户授权的本地文件夹里创建目录。",
                schema(mapOf("path" to prop("string", "相对路径")), listOf("path")),
            ))
            add(function(
                "local_delete",
                "删除用户授权文件夹里的文件或目录。",
                schema(mapOf("path" to prop("string", "相对路径")), listOf("path")),
            ))
            if (BuildConfig.PERSONAL) {
                add(function(
                    "screen_read",
                    "读取当前手机屏幕上的文字、按钮、输入框和坐标。需要先在 设置 → 全部权限 → 无障碍自动化 开启服务。",
                    schema(emptyMap(), emptyList()),
                ))
                add(function(
                    "screen_tap",
                    "在当前屏幕上按文字/描述查找并点击控件；找不到时会按坐标点击。",
                    schema(
                        mapOf(
                            "text" to prop("string", "按钮文字、控件描述或其中一段"),
                            "index" to prop("integer", "同名控件的序号，默认 0"),
                        ),
                        listOf("text"),
                    ),
                ))
                add(function(
                    "screen_tap_at",
                    "按屏幕坐标点击。",
                    schema(
                        mapOf(
                            "x" to prop("integer", "横坐标"),
                            "y" to prop("integer", "纵坐标"),
                        ),
                        listOf("x", "y"),
                    ),
                ))
                add(function(
                    "screen_type",
                    "在当前聚焦的输入框里输入文字，并自动触发输入事件。",
                    schema(mapOf("text" to prop("string", "要输入的文字")), listOf("text")),
                ))
                add(function(
                    "screen_screenshot",
                    "用无障碍截图当前屏幕，保存到工作区并返回路径。",
                    schema(emptyMap(), emptyList()),
                ))
                add(function(
                    "screen_scroll",
                    "滚动当前屏幕，direction=forward 向下/向后，back 向上/向前。",
                    schema(mapOf("direction" to prop("string", "forward 或 back")), emptyList()),
                ))
                add(function(
                    "screen_back",
                    "执行系统返回。",
                    schema(emptyMap(), emptyList()),
                ))
                add(function(
                    "screen_home",
                    "回到手机桌面。",
                    schema(emptyMap(), emptyList()),
                ))
                add(function(
                    "screen_recents",
                    "打开最近任务。",
                    schema(emptyMap(), emptyList()),
                ))
            }
            add(function(
                "undo_last",
                "撤销最后一次文件写入/替换/删除/移动。只保留一级备份。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "schedule_task",
                "在 N 分钟后让 Kite 在后台自动执行一条任务，完成后发通知。适合提醒、定时查资料、定时整理。",
                schema(
                    mapOf(
                        "prompt" to prop("string", "到时要执行的完整任务"),
                        "minutes" to prop("integer", "多少分钟后执行，最少 1 分钟"),
                    ),
                    listOf("prompt", "minutes"),
                ),
            ))
            add(function(
                "list_skills",
                "列出工作区 .kite/skills 里已安装的技能。",
                schema(emptyMap(), emptyList()),
            ))
            add(function(
                "read_skill",
                "读取一个技能的 SKILL.md 正文。执行技能前必须先读。",
                schema(mapOf("name" to prop("string", "技能名")), listOf("name")),
            ))
            add(function(
                "write_skill",
                "新建或覆盖一个技能（SKILL.md）。只在用户明确要求沉淀技能时使用。",
                schema(
                    mapOf(
                        "name" to prop("string", "技能名，小写字母/数字/连字符"),
                        "description" to prop("string", "一句话说明什么时候用"),
                        "body" to prop("string", "技能正文，步骤、检查项、示例"),
                    ),
                    listOf("name", "description", "body"),
                ),
            ))
            add(function(
                "spawn_subagent",
                "启动一个子智能体独立完成一部分任务，返回它的结论。适合调研、并行分析或隔离长任务。",
                schema(
                    mapOf(
                        "task" to prop("string", "交给子智能体的完整任务"),
                        "max_steps" to prop("integer", "子智能体最多执行步数，默认 6，最大 10"),
                    ),
                    listOf("task"),
                ),
            ))
            add(function(
                "todo_write",
                "更新当前任务的计划清单。复杂任务开始时先写计划，执行中更新状态。",
                schema(
                    mapOf(
                        "items" to JsonObject().apply {
                            addProperty("type", "array")
                            add(
                                "items",
                                JsonObject().apply {
                                    addProperty("type", "object")
                                    add(
                                        "properties",
                                        JsonObject().apply {
                                            add("text", prop("string", "任务描述"))
                                            add(
                                                "status",
                                                JsonObject().apply {
                                                    addProperty("type", "string")
                                                    add(
                                                        "enum",
                                                        JsonArray().apply {
                                                            add("pending")
                                                            add("in_progress")
                                                            add("done")
                                                        },
                                                    )
                                                },
                                            )
                                        },
                                    )
                                    add(
                                        "required",
                                        JsonArray().apply { add("text") },
                                    )
                                },
                            )
                        },
                    ),
                    listOf("items"),
                ),
            ))
        }
    }

    private fun function(name: String, description: String, parameters: JsonObject): JsonObject =
        JsonObject().apply {
            addProperty("type", "function")
            add(
                "function",
                JsonObject().apply {
                    addProperty("name", name)
                    addProperty("description", description)
                    add("parameters", parameters)
                },
            )
        }
}

class ToolRunner(
    private val context: Context,
    private val workspace: File,
    private val shellEnabled: Boolean,
    private val allowExternal: Boolean = false,
    private val shellMode: String = "normal",
    private val mcp: app.kite.agent.mcp.McpManager? = null,
    private val approval: Boolean = false,
) {

    private val appContext: Context = context.applicationContext
    private val root: File = workspace.canonicalFile
    private val dangerous = setOf(
        "run_shell", "write_file", "replace_in_file", "delete_path", "move_path", "copy_path",
        "screen_tap", "screen_tap_at", "screen_type", "screen_scroll",
        "dial_number", "compose_sms", "http_request", "local_write", "local_delete",
        "write_skill",
    )
    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    suspend fun run(call: ToolCall): ToolResult = withContext(Dispatchers.IO) {
        val args = runCatching { JsonParser.parseString(call.arguments).asJsonObject }
            .getOrElse { return@withContext ToolResult(false, "参数不是合法 JSON") }
        if (approval && call.name in dangerous) {
            val allowed = ApprovalGate.ask(call.name, call.arguments)
            if (!allowed) return@withContext ToolResult(false, "用户拒绝了这次操作")
        }
        runCatching {
            if (call.name.startsWith("mcp__")) {
                val manager = mcp ?: return@runCatching ToolResult(false, "MCP 未初始化")
                return@runCatching manager.call(call.name, args)
            }
            when (call.name) {
                "list_dir" -> listDir(args.string("path", "."))
                "read_file" -> readFile(args.string("path", ""), args.int("offset", 1), args.int("limit", 200))
                "write_file" -> writeFile(args.string("path", ""), args.string("content", ""))
                "replace_in_file" -> replaceInFile(
                    args.string("path", ""),
                    args.string("old", ""),
                    args.string("new", ""),
                )
                "grep" -> grep(args.string("pattern", ""), args.string("path", "."), args.string("glob", "*"))
                "run_shell" -> runShell(args.string("command", ""), args.string("cwd", "."))
                "web_fetch" -> webFetch(args.string("url", ""))
                "web_search" -> webSearch(args.string("query", ""), args.int("max", 6))
                "http_request" -> httpRequest(
                    args.string("method", "GET"),
                    args.string("url", ""),
                    args.string("headers", ""),
                    args.string("body", ""),
                )
                "move_path" -> movePath(args.string("from", ""), args.string("to", ""))
                "copy_path" -> copyPath(args.string("from", ""), args.string("to", ""))
                "delete_path" -> deletePath(args.string("path", ""), args.bool("recursive", false))
                "make_dir" -> makeDir(args.string("path", ""))
                "file_info" -> fileInfo(args.string("path", ""))
                "device_info" -> deviceInfo()
                "list_apps" -> listApps(args.string("query", ""))
                "open_app" -> openApp(args.string("name", ""))
                "open_url" -> openUrl(args.string("url", ""))
                "clipboard_read" -> clipboardRead()
                "clipboard_write" -> clipboardWrite(args.string("text", ""))
                "share_text" -> shareText(args.string("text", ""))
                "notify" -> notify(args.string("title", ""), args.string("text", ""))
                "speak" -> speak(args.string("text", ""))
                "get_location" -> getLocation()
                "read_contacts" -> readContacts(args.string("query", ""), args.int("limit", 10))
                "compose_sms" -> composeSms(args.string("number", ""), args.string("body", ""))
                "dial_number" -> dialNumber(args.string("number", ""))
                "create_calendar_event" -> createCalendarEvent(
                    args.string("title", ""),
                    args.long("begin", 0L),
                    args.int("minutes", 60),
                    args.string("location", ""),
                )
                "set_alarm" -> setAlarm(args.int("hour", 7), args.int("minute", 0), args.string("label", ""))
                "browser_open" -> browserOpen(args.string("url", ""))
                "browser_read" -> browserRead()
                "browser_click" -> browserClick(args.string("selector", ""))
                "browser_type" -> browserType(args.string("selector", ""), args.string("text", ""))
                "browser_back" -> browserBack()
                "browser_close" -> browserClose()
                "local_list" -> local { LocalFiles.list(appContext, args.string("path", "")) }
                "local_read" -> local { LocalFiles.read(appContext, args.string("path", "")) }
                "local_write" -> local {
                    LocalFiles.write(appContext, args.string("path", ""), args.string("content", ""))
                }
                "local_mkdir" -> local { LocalFiles.mkdir(appContext, args.string("path", "")) }
                "local_delete" -> local { LocalFiles.delete(appContext, args.string("path", "")) }
                "undo_last" -> {
                    val restored = UndoStore.restoreLast(appContext)
                    if (restored != null) ToolResult(true, "已恢复：$restored")
                    else ToolResult(false, "没有可撤销的文件操作")
                }
                "schedule_task" -> {
                    val prompt = args.string("prompt", "")
                    val minutes = args.int("minutes", 10)
                    if (prompt.isBlank()) ToolResult(false, "缺少 prompt")
                    else {
                        val id = ScheduleManager.schedule(appContext, prompt, minutes)
                        ToolResult(true, "已安排：${minutes.coerceAtLeast(1)} 分钟后执行，任务 ID $id")
                    }
                }
                "list_skills" -> {
                    val skills = app.kite.agent.data.SkillStore(appContext, root).list()
                    if (skills.isEmpty()) ToolResult(true, "还没有技能。")
                    else ToolResult(
                        true,
                        skills.joinToString("\n") { "- ${it.name}: ${it.description}" },
                    )
                }
                "read_skill" -> {
                    val name = args.string("name", "")
                    val skill = app.kite.agent.data.SkillStore(appContext, root).get(name)
                    if (skill == null) ToolResult(false, "没有这个技能：$name")
                    else ToolResult(true, "# ${skill.name}\n\n${skill.body.take(60_000)}")
                }
                "write_skill" -> {
                    val name = args.string("name", "")
                    val description = args.string("description", "")
                    val body = args.string("body", "")
                    if (name.isBlank() || body.isBlank()) ToolResult(false, "缺少 name 或 body")
                    else {
                        val skill = app.kite.agent.data.SkillStore(appContext, root)
                            .save(name, description, body)
                        ToolResult(true, "已保存技能 ${skill.name}：${skill.file.absolutePath}")
                    }
                }
                "screen_read" -> a11y { A11y.readScreen() }
                "screen_tap" -> a11y { A11y.tap(args.string("text", ""), args.int("index", 0)) }
                "screen_tap_at" -> a11y { A11y.tapAt(args.int("x", 0).toFloat(), args.int("y", 0).toFloat()) }
                "screen_type" -> a11y { A11y.type(args.string("text", "")) }
                "screen_screenshot" -> a11y {
                    val target = File(root, "screenshot-${System.currentTimeMillis()}.png")
                    A11y.screenshot(target)
                }
                "screen_scroll" -> a11y {
                    A11y.scroll(args.string("direction", "forward") != "back")
                }
                "screen_back" -> a11y { A11y.back() }
                "screen_home" -> a11y { A11y.home() }
                "screen_recents" -> a11y { A11y.recents() }
                "mcp_list_resources" -> mcp?.listResources(args.string("server", ""))
                    ?: ToolResult(false, "MCP 未初始化")
                "mcp_read_resource" -> mcp?.readResource(
                    args.string("uri", ""),
                    args.string("server", ""),
                ) ?: ToolResult(false, "MCP 未初始化")
                "mcp_list_prompts" -> mcp?.listPrompts(args.string("server", ""))
                    ?: ToolResult(false, "MCP 未初始化")
                "mcp_get_prompt" -> mcp?.getPrompt(
                    args.string("name", ""),
                    args.string("arguments", "{}"),
                    args.string("server", ""),
                ) ?: ToolResult(false, "MCP 未初始化")
                else -> ToolResult(false, "未知工具：${call.name}")
            }
        }.getOrElse { error ->
            android.util.Log.e("KiteTool", "tool ${call.name} failed", error)
            ToolResult(false, error.message ?: error.javaClass.simpleName)
        }
    }

    private fun resolve(path: String): File {
        val raw = path.ifBlank { "." }
        val candidate = (if (File(raw).isAbsolute) File(raw) else File(root, raw)).canonicalFile
        val insideWorkspace = candidate.path == root.path ||
            candidate.path.startsWith(root.path + File.separator)
        if (!insideWorkspace) {
            if (allowExternal && isExternal(candidate)) return candidate
            throw IllegalArgumentException("路径超出工作区：$path（个人版可开启所有文件访问）")
        }
        return candidate
    }

    private fun isExternal(file: File): Boolean {
        val external = Environment.getExternalStorageDirectory().canonicalFile
        return file.path == external.path || file.path.startsWith(external.path + File.separator)
    }

    private fun listDir(path: String): ToolResult {
        val dir = resolve(path)
        if (!dir.exists()) return ToolResult(false, "目录不存在：$path")
        if (!dir.isDirectory) return ToolResult(false, "不是目录：$path")
        val entries = dir.listFiles()?.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
            ?: emptyList()
        if (entries.isEmpty()) return ToolResult(true, "(空目录)")
        val text = entries.take(200).joinToString("\n") { file ->
            val size = if (file.isFile) "  ${file.length()} B" else ""
            (if (file.isDirectory) "${file.name}/" else file.name) + size
        }
        return ToolResult(true, text)
    }

    private fun readFile(path: String, offset: Int, limit: Int): ToolResult {
        if (path.isBlank()) return ToolResult(false, "缺少 path")
        val file = resolve(path)
        if (!file.exists() || !file.isFile) return ToolResult(false, "文件不存在：$path")
        if (file.length() > 2_000_000) return ToolResult(false, "文件太大（>2MB）")
        val bytes = file.readBytes()
        if (bytes.any { it == 0.toByte() }) return ToolResult(false, "这是二进制文件，无法按文本读取")
        val lines = String(bytes, Charsets.UTF_8).lines()
        val start = (offset.coerceAtLeast(1) - 1).coerceAtMost(lines.size)
        val count = limit.coerceIn(1, 400)
        val slice = lines.drop(start).take(count)
        if (slice.isEmpty()) return ToolResult(true, "(没有更多内容)")
        val width = (start + slice.size).toString().length
        val text = slice.mapIndexed { index, line ->
            val number = (start + index + 1).toString().padStart(width)
            "$number | $line"
        }.joinToString("\n")
        return ToolResult(true, text.take(80_000))
    }

    private fun writeFile(path: String, content: String): ToolResult {
        if (path.isBlank()) return ToolResult(false, "缺少 path")
        if (content.length > 400_000) return ToolResult(false, "内容太大（>400k 字符）")
        val file = resolve(path)
        if (file.exists()) UndoStore.backup(appContext, file)
        file.parentFile?.mkdirs()
        file.writeText(content)
        return ToolResult(true, "已写入 ${content.length} 字符到 $path")
    }

    private fun replaceInFile(path: String, old: String, new: String): ToolResult {
        if (path.isBlank() || old.isEmpty()) return ToolResult(false, "缺少 path 或 old")
        val file = resolve(path)
        if (!file.exists()) return ToolResult(false, "文件不存在：$path")
        val text = file.readText()
        val count = text.windowed(old.length, 1).count { it == old }
        if (count == 0) return ToolResult(false, "没有找到要替换的文本")
        UndoStore.backup(appContext, file)
        file.writeText(text.replace(old, new))
        return ToolResult(true, "已替换 $count 处，可用 undo_last 撤销")
    }

    private fun grep(pattern: String, path: String, glob: String): ToolResult {
        if (pattern.isBlank()) return ToolResult(false, "缺少 pattern")
        val regex = runCatching { Regex(pattern) }.getOrElse {
            return ToolResult(false, "正则不合法：${it.message}")
        }
        val rootDir = resolve(path)
        val matches = mutableListOf<String>()
        val skip = setOf(".git", "build", "node_modules", ".gradle", ".idea", "__pycache__")
        val fileGlob = globToRegex(glob)

        fun walk(dir: File) {
            if (matches.size >= 80) return
            val children = dir.listFiles() ?: return
            for (child in children) {
                if (matches.size >= 80) return
                if (child.isDirectory) {
                    if (child.name !in skip) walk(child)
                } else if (child.length() <= 512_000 && fileGlob.matches(child.name)) {
                    runCatching {
                        val lines = child.readText().lines()
                        lines.forEachIndexed { index, line ->
                            if (matches.size < 80 && regex.containsMatchIn(line)) {
                                val relative = child.relativeTo(root).path
                                matches += "$relative:${index + 1}: ${line.take(240)}"
                            }
                        }
                    }
                }
            }
        }
        walk(rootDir)
        return if (matches.isEmpty()) ToolResult(true, "(没有匹配)") else ToolResult(true, matches.joinToString("\n"))
    }

    private fun globToRegex(glob: String): Regex {
        val escaped = Regex.escape(glob).replace("\\*", ".*").replace("\\?", ".")
        return Regex("^$escaped$")
    }

    private fun runShell(command: String, cwd: String): ToolResult {
        if (!shellEnabled) {
            return ToolResult(false, "用户没有开启 Shell 权限。请在设置中开启，或改用文件工具。")
        }
        if (command.isBlank()) return ToolResult(false, "缺少 command")
        if (command.length > 4_000) return ToolResult(false, "命令太长")
        val dir = resolve(cwd)
        if (!dir.isDirectory) return ToolResult(false, "工作目录不存在：$cwd")

        return when (shellMode) {
            "root" -> {
                val su = SystemAccess.suPath()
                    ?: return ToolResult(
                        false,
                        "没有找到 su，手机未 root。华为 NOP-AN00 默认未 root，App 无法自行获取真正的 root 权限。",
                    )
                val process = ProcessBuilder(su, "-c", command)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .apply { environment().applyShellEnv(dir) }
                    .start()
                collect(process)
            }

            "shizuku" -> {
                if (!SystemAccess.isShizukuRunning()) {
                    return ToolResult(
                        false,
                        "Shizuku 没有运行。先安装并启动 Shizuku，再在「全部权限」里授权 Kite。",
                    )
                }
                if (!SystemAccess.isShizukuGranted()) {
                    return ToolResult(false, "还没有授权 Kite 使用 Shizuku。请到「全部权限 → Shizuku」点授权。")
                }
                val process = runCatching {
                    shizukuProcess(command, shellEnv(dir), dir.absolutePath)
                }.getOrElse {
                    return ToolResult(false, "Shizuku 执行失败：${it.message ?: it.javaClass.simpleName}")
                }
                collect(process)
            }

            else -> {
                val process = ProcessBuilder("/system/bin/sh", "-c", command)
                    .directory(dir)
                    .redirectErrorStream(true)
                    .apply { environment().applyShellEnv(dir) }
                    .start()
                collect(process)
            }
        }
    }

    private fun collect(process: Process): ToolResult {
        val output = StringBuilder()
        val reader = Thread {
            runCatching {
                process.inputStream.bufferedReader().useLines { lines ->
                    lines.forEach { line ->
                        if (output.length < 120_000) output.appendLine(line)
                    }
                }
            }
        }
        reader.start()
        val waiter = Thread { runCatching { process.waitFor() } }
        waiter.start()
        waiter.join(30_000)
        val finished = !waiter.isAlive
        if (!finished) runCatching { process.destroyForcibly() }
        reader.join(1_000)
        val prefix = if (finished) "" else "[超时，进程已终止]\n"
        val body = output.toString().ifBlank { "(无输出)" }
        val exit = if (finished) runCatching { process.exitValue() }.getOrDefault(-1) else -1
        return ToolResult(finished && exit == 0, prefix + body.take(120_000))
    }

    private fun MutableMap<String, String>.applyShellEnv(dir: File) {
        this["HOME"] = dir.absolutePath
        this["TMPDIR"] = File(dir, ".tmp").apply { mkdirs() }.absolutePath
        this["PATH"] = "/system/bin:/system/xbin:/vendor/bin"
        this["TERM"] = "dumb"
    }

    private fun shellEnv(dir: File): Array<String> = arrayOf(
        "HOME=${dir.absolutePath}",
        "TMPDIR=${File(dir, ".tmp").apply { mkdirs() }.absolutePath}",
        "PATH=/system/bin:/system/xbin:/vendor/bin",
        "TERM=dumb",
    )

    @Suppress("PrivateApi")
    private fun shizukuProcess(command: String, env: Array<String>, dir: String): Process =
        SystemAccess.newShizukuProcess(command, env, dir)

    private fun webFetch(url: String): ToolResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(false, "只支持 http/https 地址")
        }
        val request = Request.Builder()
            .url(url)
            .header("User-Agent", "Kite/0.1 (Android)")
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return ToolResult(false, "HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            if (body.length > 3_000_000) return ToolResult(false, "页面太大")
            val doc = Jsoup.parse(body, url)
            val title = doc.title()
            val text = doc.body()?.text().orEmpty()
            return ToolResult(true, (if (title.isNotBlank()) "# $title\n\n" else "") + text.take(60_000))
        }
    }

    private fun webSearch(query: String, max: Int): ToolResult {
        if (query.isBlank()) return ToolResult(false, "缺少 query")
        val limit = max.coerceIn(1, 10)
        val ddg = runCatching { searchDuckDuckGo(query, limit) }.getOrNull()
        if (!ddg.isNullOrBlank()) return ToolResult(true, ddg.take(30_000))
        val bing = runCatching { searchBingRss(query, limit) }.getOrElse {
            return ToolResult(false, "搜索失败：${it.message ?: it.javaClass.simpleName}")
        }
        return if (bing.isBlank()) {
            ToolResult(false, "搜索没有返回结果（DuckDuckGo / Bing RSS 都为空）")
        } else {
            ToolResult(true, bing.take(30_000))
        }
    }

    private fun searchDuckDuckGo(query: String, limit: Int): String {
        val url = "https://html.duckduckgo.com/html/?q=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
            )
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("DuckDuckGo HTTP ${response.code}")
            val doc = Jsoup.parse(response.body?.string().orEmpty())
            val items = doc.select("div.result:not(.result--ad)").take(limit)
            if (items.isEmpty()) return ""
            return items.mapIndexed { index, item ->
                val anchor = item.selectFirst("a.result__a")
                val rawLink = anchor?.attr("href").orEmpty()
                val link = runCatching {
                    val normalized = if (rawLink.startsWith("//")) "https:$rawLink" else rawLink
                    Uri.parse(normalized).getQueryParameter("uddg") ?: normalized
                }.getOrDefault(rawLink)
                val snippet = item.selectFirst(".result__snippet")?.text().orEmpty()
                "${index + 1}. ${anchor?.text().orEmpty()}\n$link\n$snippet"
            }.joinToString("\n\n")
        }
    }

    /** Bing 的 RSS 出口比抓 HTML 稳，百度 403 / DDG 被墙时用它兜底。 */
    private fun searchBingRss(query: String, limit: Int): String {
        val url = "https://cn.bing.com/search?format=rss&q=" + URLEncoder.encode(query, "UTF-8")
        val request = Request.Builder()
            .url(url)
            .header(
                "User-Agent",
                "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120 Mobile Safari/537.36",
            )
            .build()
        http.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IllegalStateException("Bing RSS HTTP ${response.code}")
            val body = response.body?.string().orEmpty()
            val doc = Jsoup.parse(body, "", Parser.xmlParser())
            val items = doc.select("item").take(limit)
            if (items.isEmpty()) return ""
            return items.mapIndexed { index, item ->
                val title = item.selectFirst("title")?.text().orEmpty()
                val link = item.selectFirst("link")?.text().orEmpty()
                val description = Jsoup.parse(item.selectFirst("description")?.text().orEmpty()).text()
                "${index + 1}. $title\n$link\n$description"
            }.joinToString("\n\n")
        }
    }

    private fun httpRequest(method: String, url: String, headers: String, body: String): ToolResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(false, "只支持 http/https 地址")
        }
        val verb = method.ifBlank { "GET" }.uppercase()
        if (verb !in setOf("GET", "POST", "PUT", "PATCH", "DELETE", "HEAD")) {
            return ToolResult(false, "不支持的 HTTP 方法：$verb")
        }
        val builder = Request.Builder().url(url)
        if (headers.isNotBlank()) {
            runCatching { JsonParser.parseString(headers).asJsonObject }.getOrNull()?.entrySet()?.forEach { entry ->
                if (!entry.value.isJsonNull) builder.header(entry.key, entry.value.asString)
            }
        }
        val requestBody = if (verb in setOf("POST", "PUT", "PATCH", "DELETE")) {
            body.toRequestBody("application/json; charset=utf-8".toMediaType())
        } else {
            null
        }
        val request = builder.method(verb, requestBody).build()
        http.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val head = response.headers.joinToString("\n") { "${it.first}: ${it.second}" }
            val prefix = if (response.isSuccessful) "" else "请求返回非 2xx\n"
            return ToolResult(
                response.isSuccessful,
                "$prefix HTTP ${response.code}\n$head\n\n${text.take(200_000)}",
            )
        }
    }

    private fun movePath(from: String, to: String): ToolResult {
        if (from.isBlank() || to.isBlank()) return ToolResult(false, "缺少 from 或 to")
        val source = resolve(from)
        val target = resolve(to)
        if (!source.exists()) return ToolResult(false, "源路径不存在：$from")
        if (target.exists()) return ToolResult(false, "目标已存在：$to")
        UndoStore.backup(appContext, source)
        target.parentFile?.mkdirs()
        val moved = source.renameTo(target) || runCatching {
            source.copyRecursively(target, overwrite = false)
            source.deleteRecursively()
            true
        }.getOrDefault(false)
        return if (moved) ToolResult(true, "已移动：$from → $to") else ToolResult(false, "移动失败")
    }

    private fun copyPath(from: String, to: String): ToolResult {
        if (from.isBlank() || to.isBlank()) return ToolResult(false, "缺少 from 或 to")
        val source = resolve(from)
        val target = resolve(to)
        if (!source.exists()) return ToolResult(false, "源路径不存在：$from")
        if (target.exists()) return ToolResult(false, "目标已存在：$to")
        target.parentFile?.mkdirs()
        val copied = runCatching { source.copyRecursively(target, overwrite = false) }.getOrDefault(false)
        return if (copied) ToolResult(true, "已复制：$from → $to") else ToolResult(false, "复制失败")
    }

    private fun deletePath(path: String, recursive: Boolean): ToolResult {
        if (path.isBlank()) return ToolResult(false, "缺少 path")
        val file = resolve(path)
        if (!file.exists()) return ToolResult(false, "路径不存在：$path")
        if (file.canonicalPath == root.canonicalPath) return ToolResult(false, "不能删除工作区根目录")
        if (file.isDirectory && !recursive) return ToolResult(false, "这是目录，需要 recursive=true")
        UndoStore.backup(appContext, file)
        val deleted = runCatching { file.deleteRecursively() }.getOrDefault(false)
        return if (deleted) ToolResult(true, "已删除：$path") else ToolResult(false, "删除失败")
    }

    private fun makeDir(path: String): ToolResult {
        if (path.isBlank()) return ToolResult(false, "缺少 path")
        val dir = resolve(path)
        return if (dir.exists() && dir.isDirectory) {
            ToolResult(true, "目录已存在：${dir.absolutePath}")
        } else if (dir.mkdirs()) {
            ToolResult(true, "已创建目录：${dir.absolutePath}")
        } else {
            ToolResult(false, "创建目录失败")
        }
    }

    private fun fileInfo(path: String): ToolResult {
        if (path.isBlank()) return ToolResult(false, "缺少 path")
        val file = resolve(path)
        if (!file.exists()) return ToolResult(false, "路径不存在：$path")
        val time = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault())
            .format(java.util.Date(file.lastModified()))
        return ToolResult(
            true,
            buildString {
                appendLine("路径：${file.absolutePath}")
                appendLine("类型：${if (file.isDirectory) "目录" else "文件"}")
                if (file.isFile) appendLine("大小：${file.length()} 字节")
                appendLine("修改时间：$time")
                if (file.isDirectory) appendLine("子项数：${file.listFiles()?.size ?: 0}")
            }.trim(),
        )
    }

    private fun deviceInfo(): ToolResult {
        val battery = appContext.getSystemService(Context.BATTERY_SERVICE) as? BatteryManager
        val level = battery?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: -1
        val charging = battery?.isCharging == true
        val external = runCatching { StatFs(Environment.getExternalStorageDirectory().absolutePath) }.getOrNull()
        val internal = runCatching { StatFs(appContext.filesDir.absolutePath) }.getOrNull()
        val cm = appContext.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager
        val caps = cm?.activeNetwork?.let { cm.getNetworkCapabilities(it) }
        val network = when {
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) == true -> "Wi-Fi"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) == true -> "移动网络"
            caps?.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET) == true -> "以太网"
            else -> "未连接"
        }
        val validated = caps?.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED) == true
        val metrics = appContext.resources.displayMetrics
        fun gb(bytes: Long) = String.format(Locale.US, "%.1f GB", bytes / 1_000_000_000.0)
        return ToolResult(
            true,
            buildString {
                appendLine("厂商：${Build.MANUFACTURER}")
                appendLine("型号：${Build.MODEL}")
                appendLine("设备：${Build.DEVICE}")
                appendLine("Android：${Build.VERSION.RELEASE}（API ${Build.VERSION.SDK_INT}）")
                appendLine("电量：${if (level >= 0) "$level%" else "未知"}${if (charging) " · 充电中" else ""}")
                if (external != null) {
                    appendLine("外部存储：${gb(external.availableBytes)} 可用 / ${gb(external.totalBytes)} 总计")
                }
                if (internal != null) {
                    appendLine("应用存储：${gb(internal.availableBytes)} 可用 / ${gb(internal.totalBytes)} 总计")
                }
                appendLine("网络：$network${if (validated) " · 已验证" else ""}")
                appendLine("屏幕：${metrics.widthPixels}×${metrics.heightPixels} @ ${metrics.densityDpi}dpi")
            }.trim(),
        )
    }

    private fun listApps(query: String): ToolResult {
        val pm = appContext.packageManager
        val apps = pm.getInstalledApplications(0)
            .filter { pm.getLaunchIntentForPackage(it.packageName) != null }
            .map { it.packageName to pm.getApplicationLabel(it).toString() }
            .filter { (pkg, label) ->
                query.isBlank() || label.contains(query, ignoreCase = true) ||
                    pkg.contains(query, ignoreCase = true)
            }
            .sortedBy { it.second.lowercase() }
            .take(60)
        if (apps.isEmpty()) return ToolResult(true, "(没有匹配的应用)")
        return ToolResult(true, apps.joinToString("\n") { (pkg, label) -> "$label — $pkg" })
    }

    private fun openApp(name: String): ToolResult {
        if (name.isBlank()) return ToolResult(false, "缺少应用名")
        val pm = appContext.packageManager
        val installed = pm.getInstalledApplications(0)
        val target = installed.firstOrNull { it.packageName == name }
            ?: installed.firstOrNull {
                pm.getApplicationLabel(it).toString().contains(name, ignoreCase = true) &&
                    pm.getLaunchIntentForPackage(it.packageName) != null
            }
            ?: return ToolResult(false, "没有找到应用：$name")
        val intent = pm.getLaunchIntentForPackage(target.packageName)
            ?: return ToolResult(false, "应用没有启动入口：${target.packageName}")
        appContext.startActivity(intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        return ToolResult(true, "已打开：${pm.getApplicationLabel(target)}")
    }

    private fun openUrl(url: String): ToolResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(false, "只支持 http/https 地址")
        }
        appContext.startActivity(
            Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return ToolResult(true, "已用浏览器打开：$url")
    }

    private fun clipboardRead(): ToolResult {
        val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ToolResult(false, "剪贴板不可用")
        val clip = manager.primaryClip ?: return ToolResult(true, "(剪贴板为空)")
        val text = (0 until clip.itemCount).mapNotNull {
            clip.getItemAt(it).coerceToText(appContext)?.toString()
        }.joinToString("\n")
        return ToolResult(true, text.ifBlank { "(剪贴板没有文本)" }.take(20_000))
    }

    private fun clipboardWrite(text: String): ToolResult {
        if (text.isEmpty()) return ToolResult(false, "文本为空")
        val manager = appContext.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
            ?: return ToolResult(false, "剪贴板不可用")
        manager.setPrimaryClip(ClipData.newPlainText("Kite", text))
        return ToolResult(true, "已写入剪贴板（${text.length} 字符）")
    }

    private fun shareText(text: String): ToolResult {
        if (text.isBlank()) return ToolResult(false, "文本为空")
        val send = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_TEXT, text)
        }
        appContext.startActivity(
            Intent.createChooser(send, "分享").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return ToolResult(true, "已打开分享面板")
    }

    private fun notify(title: String, text: String): ToolResult {
        if (title.isBlank() && text.isBlank()) return ToolResult(false, "标题和内容为空")
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(false, "没有通知权限（个人版可在全部权限里开启）")
        }
        val manager = appContext.getSystemService(Context.NOTIFICATION_SERVICE) as? NotificationManager
            ?: return ToolResult(false, "通知服务不可用")
        val channel = NotificationChannel("kite_alerts", "Kite 通知", NotificationManager.IMPORTANCE_DEFAULT)
        manager.createNotificationChannel(channel)
        val notification = Notification.Builder(appContext, "kite_alerts")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title.ifBlank { "Kite" })
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        manager.notify((System.currentTimeMillis() % 100_000).toInt(), notification)
        return ToolResult(true, "已发送通知")
    }

    private fun speak(text: String): ToolResult {
        if (text.isBlank()) return ToolResult(false, "文本为空")
        return ToolResult(true, TtsEngine.speak(appContext, text))
    }

    private fun getLocation(): ToolResult {
        val fine = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_FINE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        val coarse = ContextCompat.checkSelfPermission(appContext, Manifest.permission.ACCESS_COARSE_LOCATION) ==
            PackageManager.PERMISSION_GRANTED
        if (!fine && !coarse) return ToolResult(false, "没有定位权限（个人版可在全部权限里开启）")
        val manager = appContext.getSystemService(Context.LOCATION_SERVICE) as? LocationManager
            ?: return ToolResult(false, "定位服务不可用")
        val locations = listOf(
            LocationManager.GPS_PROVIDER,
            LocationManager.NETWORK_PROVIDER,
            LocationManager.PASSIVE_PROVIDER,
        ).mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        val location = locations.maxByOrNull { it.time }
            ?: return ToolResult(false, "没有可用的最后位置，请先打开定位并到室外试一下")
        val age = (System.currentTimeMillis() - location.time) / 1000
        return ToolResult(
            true,
            "纬度：${location.latitude}\n经度：${location.longitude}\n精度：${location.accuracy} 米\n" +
                "来源：${location.provider}\n距今：${age} 秒",
        )
    }

    private fun readContacts(query: String, limit: Int): ToolResult {
        if (ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CONTACTS) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            return ToolResult(false, "没有通讯录权限（个人版可在全部权限里开启）")
        }
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
        )
        val selection = if (query.isBlank()) {
            null
        } else {
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} LIKE ? OR " +
                "${ContactsContract.CommonDataKinds.Phone.NUMBER} LIKE ?"
        }
        val selectionArgs = if (query.isBlank()) null else arrayOf("%$query%", "%$query%")
        val rows = mutableListOf<String>()
        appContext.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME} ASC",
        )?.use { cursor ->
            val nameIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIndex = cursor.getColumnIndexOrThrow(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext() && rows.size < limit.coerceIn(1, 30)) {
                rows += "${cursor.getString(nameIndex)}: ${cursor.getString(numberIndex)}"
            }
        }
        return ToolResult(true, if (rows.isEmpty()) "(没有匹配的联系人)" else rows.joinToString("\n"))
    }

    private fun composeSms(number: String, body: String): ToolResult {
        if (number.isBlank()) return ToolResult(false, "缺少号码")
        val intent = Intent(Intent.ACTION_SENDTO, Uri.parse("smsto:${Uri.encode(number)}")).apply {
            putExtra("sms_body", body)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        return ToolResult(true, "已打开短信编辑器，请用户确认发送")
    }

    private fun dialNumber(number: String): ToolResult {
        if (number.isBlank()) return ToolResult(false, "缺少号码")
        appContext.startActivity(
            Intent(Intent.ACTION_DIAL, Uri.parse("tel:${Uri.encode(number)}"))
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return ToolResult(true, "已打开拨号界面，请用户确认拨打")
    }

    private fun createCalendarEvent(title: String, begin: Long, minutes: Int, location: String): ToolResult {
        if (title.isBlank()) return ToolResult(false, "缺少标题")
        val start = if (begin > 0) begin else System.currentTimeMillis() + 3_600_000
        val end = start + minutes.coerceIn(5, 24 * 60) * 60_000L
        val intent = Intent(Intent.ACTION_INSERT).apply {
            data = CalendarContract.Events.CONTENT_URI
            putExtra(CalendarContract.Events.TITLE, title)
            putExtra(CalendarContract.EXTRA_EVENT_BEGIN_TIME, start)
            putExtra(CalendarContract.EXTRA_EVENT_END_TIME, end)
            if (location.isNotBlank()) putExtra(CalendarContract.Events.EVENT_LOCATION, location)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        return ToolResult(true, "已打开日历新建界面，请用户确认保存")
    }

    private fun setAlarm(hour: Int, minute: Int, label: String): ToolResult {
        if (hour !in 0..23 || minute !in 0..59) return ToolResult(false, "时间不合法")
        val intent = Intent(AlarmClock.ACTION_SET_ALARM).apply {
            putExtra(AlarmClock.EXTRA_HOUR, hour)
            putExtra(AlarmClock.EXTRA_MINUTES, minute)
            if (label.isNotBlank()) putExtra(AlarmClock.EXTRA_MESSAGE, label)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        appContext.startActivity(intent)
        return ToolResult(true, "已打开闹钟界面，请用户确认")
    }

    private suspend fun browserOpen(url: String): ToolResult {
        if (!url.startsWith("http://") && !url.startsWith("https://")) {
            return ToolResult(false, "只支持 http/https 地址")
        }
        return runCatching {
            ToolResult(true, BrowserController.open(appContext, url).take(20_000))
        }.getOrElse {
            ToolResult(false, "浏览器打开失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    private suspend fun browserRead(): ToolResult = runCatching {
        ToolResult(true, BrowserController.read().take(20_000))
    }.getOrElse {
        ToolResult(false, "读取页面失败：${it.message ?: it.javaClass.simpleName}")
    }

    private suspend fun browserClick(selector: String): ToolResult {
        if (selector.isBlank()) return ToolResult(false, "缺少 selector")
        return runCatching {
            ToolResult(true, BrowserController.click(selector).take(20_000))
        }.getOrElse {
            ToolResult(false, "点击失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    private suspend fun browserType(selector: String, text: String): ToolResult {
        if (selector.isBlank()) return ToolResult(false, "缺少 selector")
        return runCatching {
            ToolResult(true, BrowserController.type(selector, text))
        }.getOrElse {
            ToolResult(false, "输入失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    private suspend fun browserBack(): ToolResult = runCatching {
        ToolResult(true, BrowserController.back().take(20_000))
    }.getOrElse {
        ToolResult(false, "后退失败：${it.message ?: it.javaClass.simpleName}")
    }

    private fun browserClose(): ToolResult {
        BrowserController.close()
        return ToolResult(true, "浏览器已关闭")
    }

    private fun local(block: () -> String): ToolResult = runCatching {
        ToolResult(true, block().take(40_000))
    }.getOrElse {
        ToolResult(false, "本地文件操作失败：${it.message ?: it.javaClass.simpleName}")
    }

    private suspend fun a11y(block: suspend () -> String): ToolResult {
        if (!BuildConfig.PERSONAL) {
            return ToolResult(false, "无障碍自动化只在个人版提供")
        }
        if (!A11y.connected()) {
            return ToolResult(false, "无障碍服务未开启：设置 → 全部权限 → 无障碍自动化")
        }
        return runCatching {
            ToolResult(true, block().take(20_000))
        }.getOrElse {
            ToolResult(false, "屏幕操作失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    private fun JsonObject.string(key: String, fallback: String): String =
        if (has(key) && !get(key).isJsonNull) get(key).asString else fallback

    private fun JsonObject.int(key: String, fallback: Int): Int =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asInt }.getOrDefault(fallback) else fallback

    private fun JsonObject.long(key: String, fallback: Long): Long =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asLong }.getOrDefault(fallback) else fallback

    private fun JsonObject.bool(key: String, fallback: Boolean): Boolean =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asBoolean }.getOrDefault(fallback) else fallback
}

private object TtsEngine {
    private var tts: TextToSpeech? = null
    private var ready = false

    @Synchronized
    fun speak(context: Context, text: String): String {
        if (text.isBlank()) return "文本为空"
        if (tts == null) {
            val latch = CountDownLatch(1)
            var success = false
            tts = TextToSpeech(context.applicationContext) { status ->
                success = status == TextToSpeech.SUCCESS
                latch.countDown()
            }
            latch.await(5, TimeUnit.SECONDS)
            ready = success
            if (ready) {
                val chinese = text.any { it.code in 0x4E00..0x9FFF }
                tts?.language = if (chinese) Locale.CHINA else Locale.US
            }
        }
        if (!ready) return "TTS 初始化失败"
        val result = tts?.speak(text.take(1_000), TextToSpeech.QUEUE_FLUSH, null, "kite")
        return if (result == TextToSpeech.SUCCESS) "已开始朗读" else "朗读失败"
    }
}
