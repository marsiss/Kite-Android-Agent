package app.kite.agent.mcp

import app.kite.agent.agent.ToolResult
import app.kite.agent.data.McpServerConfig
import app.kite.agent.data.McpStore
import app.kite.agent.data.McpToolInfo
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.concurrent.ConcurrentHashMap
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class McpManager(context: Context) {

    private val store = McpStore(context.applicationContext)
    private val clients = ConcurrentHashMap<String, McpClient>()

    @Volatile
    private var tools: List<McpToolInfo> = emptyList()

    @Volatile
    private var registry: Map<String, McpToolInfo> = emptyMap()

    @Volatile
    private var status: Map<String, String> = emptyMap()

    fun servers(): List<McpServerConfig> = store.list()

    fun saveServers(servers: List<McpServerConfig>) {
        store.save(servers)
    }

    fun currentStatus(): Map<String, String> = status

    fun currentTools(): List<McpToolInfo> = tools

    suspend fun refresh(): Map<String, String> = withContext(Dispatchers.IO) {
        closeClients()
        val configs = store.list().filter { it.enabled && it.url.isNotBlank() }
        val collected = mutableListOf<McpToolInfo>()
        val newStatus = mutableMapOf<String, String>()
        val usedNames = mutableSetOf<String>()

        configs.forEach { config ->
            try {
                val client = McpClient(config)
                val list = client.listTools()
                clients[config.id] = client
                list.forEach { info ->
                    var exposed = exposedName(config.name, info.name)
                    var suffix = 2
                    while (!usedNames.add(exposed)) {
                        exposed = exposedName(config.name, info.name).take(56) + "_$suffix"
                        suffix++
                    }
                    collected += info.copy(exposedName = exposed)
                }
                newStatus[config.id] = "ok:${list.size}"
            } catch (error: Throwable) {
                newStatus[config.id] = "error:${error.message ?: error.javaClass.simpleName}"
            }
        }

        tools = collected
        registry = collected.associateBy { it.exposedName }
        status = newStatus
        newStatus
    }

    fun toolDefs(): JsonArray {
        val array = JsonArray()
        tools.forEach { info ->
            val schema = sanitizeSchema(info.inputSchema)
            array.add(function(info.exposedName, "[MCP: ${info.serverName}] ${info.description}", schema))
        }
        if (clients.isNotEmpty()) {
            array.add(
                function(
                    "mcp_list_resources",
                    "列出 MCP 服务器提供的资源。",
                    schema(
                        mapOf("server" to property("string", "服务器名称，空则全部")),
                        emptyList(),
                    ),
                ),
            )
            array.add(
                function(
                    "mcp_read_resource",
                    "读取 MCP 资源内容。",
                    schema(
                        mapOf(
                            "uri" to property("string", "资源 URI"),
                            "server" to property("string", "服务器名称，空则自动尝试"),
                        ),
                        listOf("uri"),
                    ),
                ),
            )
            array.add(
                function(
                    "mcp_list_prompts",
                    "列出 MCP 服务器提供的提示词模板。",
                    schema(
                        mapOf("server" to property("string", "服务器名称，空则全部")),
                        emptyList(),
                    ),
                ),
            )
            array.add(
                function(
                    "mcp_get_prompt",
                    "获取 MCP 提示词模板的内容。",
                    schema(
                        mapOf(
                            "name" to property("string", "提示词名称"),
                            "arguments" to property("string", "可选，JSON 对象字符串"),
                            "server" to property("string", "服务器名称，空则自动尝试"),
                        ),
                        listOf("name"),
                    ),
                ),
            )
        }
        return array
    }

    fun listResources(server: String): ToolResult {
        val targets = targets(server)
        if (targets.isEmpty()) return ToolResult(false, "没有匹配的 MCP 服务器")
        val lines = mutableListOf<String>()
        var firstError = ""
        targets.forEach { (config, client) ->
            runCatching {
                client.listResources().forEach { resource ->
                    val uri = resource.get("uri")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val name = resource.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val mime = resource.get("mimeType")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    lines += "${config.name} | $name | $uri | $mime"
                }
            }.onFailure { firstError = it.message.orEmpty() }
        }
        return if (lines.isEmpty()) {
            ToolResult(false, firstError.ifBlank { "服务器没有返回资源（可能不支持 resources）" })
        } else {
            ToolResult(true, lines.joinToString("\n"))
        }
    }

    fun readResource(uri: String, server: String): ToolResult {
        if (uri.isBlank()) return ToolResult(false, "缺少 uri")
        val targets = targets(server)
        if (targets.isEmpty()) return ToolResult(false, "没有匹配的 MCP 服务器")
        var firstError = ""
        targets.forEach { (config, client) ->
            val result = runCatching { client.readResource(uri) }
            if (result.isSuccess) {
                return ToolResult(true, "[${config.name}]\n${result.getOrThrow()}".take(40_000))
            }
            firstError = result.exceptionOrNull()?.message.orEmpty()
        }
        return ToolResult(false, "读取资源失败：$firstError")
    }

    fun listPrompts(server: String): ToolResult {
        val targets = targets(server)
        if (targets.isEmpty()) return ToolResult(false, "没有匹配的 MCP 服务器")
        val lines = mutableListOf<String>()
        var firstError = ""
        targets.forEach { (config, client) ->
            runCatching {
                client.listPrompts().forEach { prompt ->
                    val name = prompt.get("name")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    val desc = prompt.get("description")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    lines += "${config.name} | $name | $desc"
                }
            }.onFailure { firstError = it.message.orEmpty() }
        }
        return if (lines.isEmpty()) {
            ToolResult(false, firstError.ifBlank { "服务器没有返回提示词（可能不支持 prompts）" })
        } else {
            ToolResult(true, lines.joinToString("\n"))
        }
    }

    fun getPrompt(name: String, argumentsJson: String, server: String): ToolResult {
        if (name.isBlank()) return ToolResult(false, "缺少 name")
        val arguments = runCatching {
            if (argumentsJson.isBlank()) JsonObject() else JsonParser.parseString(argumentsJson).asJsonObject
        }.getOrElse { return ToolResult(false, "arguments 不是合法 JSON") }
        val targets = targets(server)
        if (targets.isEmpty()) return ToolResult(false, "没有匹配的 MCP 服务器")
        var firstError = ""
        targets.forEach { (config, client) ->
            val result = runCatching { client.getPrompt(name, arguments) }
            if (result.isSuccess) {
                return ToolResult(true, "[${config.name}]\n${result.getOrThrow()}".take(40_000))
            }
            firstError = result.exceptionOrNull()?.message.orEmpty()
        }
        return ToolResult(false, "获取提示词失败：$firstError")
    }

    fun call(exposedName: String, arguments: JsonObject): ToolResult {
        val info = registry[exposedName]
            ?: return ToolResult(false, "MCP 工具未找到：$exposedName（可能服务器已断开，请到设置里刷新）")
        val client = clients[info.serverId]
            ?: return ToolResult(false, "MCP 服务器未连接：${info.serverName}")
        return runCatching {
            val (ok, text) = client.callTool(info.name, arguments)
            ToolResult(ok, text)
        }.getOrElse {
            ToolResult(false, "MCP 调用失败：${it.message ?: it.javaClass.simpleName}")
        }
    }

    fun test(config: McpServerConfig): String {
        return runCatching {
            val client = McpClient(config)
            try {
                val tools = client.listTools()
                "连接成功，发现 ${tools.size} 个工具：${tools.take(5).joinToString { it.name }}"
            } finally {
                client.close()
            }
        }.getOrElse { "连接失败：${it.message ?: it.javaClass.simpleName}" }
    }

    fun close() {
        closeClients()
    }

    private fun closeClients() {
        clients.values.forEach { runCatching { it.close() } }
        clients.clear()
    }

    private fun targets(server: String): List<Pair<McpServerConfig, McpClient>> {
        val configs = store.list().associateBy { it.id }
        return clients.mapNotNull { (id, client) ->
            configs[id]?.let { it to client }
        }.filter { (config, _) ->
            server.isBlank() || config.name.equals(server, ignoreCase = true) || config.id == server
        }
    }

    private fun function(name: String, description: String, parameters: JsonObject): JsonObject =
        JsonObject().apply {
            addProperty("type", "function")
            add(
                "function",
                JsonObject().apply {
                    addProperty("name", name)
                    addProperty("description", description.take(900))
                    add("parameters", parameters)
                },
            )
        }

    private fun schema(properties: Map<String, JsonObject>, required: List<String>): JsonObject =
        JsonObject().apply {
            addProperty("type", "object")
            add(
                "properties",
                JsonObject().apply { properties.forEach { (key, value) -> add(key, value) } },
            )
            add(
                "required",
                JsonArray().apply { required.forEach { add(it) } },
            )
        }

    private fun property(type: String, description: String): JsonObject =
        JsonObject().apply {
            addProperty("type", type)
            addProperty("description", description)
        }

    private fun exposedName(server: String, tool: String): String {
        val raw = "mcp__${slug(server)}__${slug(tool)}"
        return raw.take(60)
    }

    private fun slug(value: String): String {
        val cleaned = value.lowercase().map { if (it.isLetterOrDigit() || it == '_' || it == '-') it else '_' }
        return cleaned.joinToString("").ifBlank { "tool" }
    }

    private fun sanitizeSchema(source: JsonObject): JsonObject {
        val schema = runCatching { JsonParser.parseString(source.toString()).asJsonObject }.getOrNull()
            ?: JsonObject()
        if (!schema.has("type")) schema.addProperty("type", "object")
        if (!schema.has("properties")) schema.add("properties", JsonObject())
        return schema
    }
}
