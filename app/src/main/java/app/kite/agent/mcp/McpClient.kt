package app.kite.agent.mcp

import app.kite.agent.data.McpServerConfig
import app.kite.agent.data.McpToolInfo
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.Closeable
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.LinkedBlockingQueue
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * Minimal MCP client for Android.
 *
 * Supports:
 * - Streamable HTTP (POST JSON-RPC, JSON or SSE response)
 * - Legacy SSE transport (GET /sse endpoint + POST messages)
 *
 * Only the tools capability is used; resources/prompts are future work.
 */
class McpClient(private val config: McpServerConfig) : Closeable {

    private val http = OkHttpClient.Builder()
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(20, TimeUnit.SECONDS)
        .readTimeout(60, TimeUnit.SECONDS)
        .build()

    private var nextId = 1
    private var sessionId: String? = null
    private var initialized = false
    private var sse: SseTransport? = null

    @Synchronized
    fun connect() {
        if (initialized) return
        if (config.transport == "sse") {
            val transport = SseTransport(config)
            transport.start()
            sse = transport
        }
        val params = JsonObject().apply {
            addProperty("protocolVersion", "2024-11-05")
            add("capabilities", JsonObject())
            add(
                "clientInfo",
                JsonObject().apply {
                    addProperty("name", "Kite")
                    addProperty("version", "0.1.0")
                },
            )
        }
        val reply = request("initialize", params)
        if (sse == null) sessionId = reply.sessionId ?: sessionId
        notify("notifications/initialized")
        initialized = true
    }

    @Synchronized
    fun listTools(): List<McpToolInfo> {
        connect()
        val reply = request("tools/list", JsonObject())
        val array = reply.result.arrOrNull("tools") ?: JsonArray()
        return array.mapNotNull { element ->
            val tool = element as? JsonObject ?: return@mapNotNull null
            val name = tool.get("name")?.takeIf { !it.isJsonNull }?.asString ?: return@mapNotNull null
            val schema = tool.objOrNull("inputSchema") ?: JsonObject().apply {
                addProperty("type", "object")
                add("properties", JsonObject())
            }
            McpToolInfo(
                serverId = config.id,
                serverName = config.name,
                name = name,
                exposedName = "",
                description = schemaDescription(tool, name),
                inputSchema = schema,
            )
        }
    }

    @Synchronized
    fun callTool(name: String, arguments: JsonObject): Pair<Boolean, String> {
        connect()
        val params = JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        }
        val reply = request("tools/call", params)
        val result = reply.result
        val isError = result.get("isError")?.takeIf { !it.isJsonNull }?.asBoolean ?: false
        val text = buildString {
            val content = result.arrOrNull("content")
            if (content != null) {
                content.forEach { item ->
                    val obj = item as? JsonObject ?: return@forEach
                    when (obj.get("type")?.takeIf { !it.isJsonNull }?.asString) {
                        "text" -> append(obj.get("text")?.takeIf { !it.isJsonNull }?.asString.orEmpty())
                        "image" -> append("[图片]")
                        "resource" -> {
                            val resource = obj.objOrNull("resource")
                            append(resource?.get("text")?.takeIf { !it.isJsonNull }?.asString.orEmpty())
                        }
                        else -> append(obj.toString())
                    }
                    appendLine()
                }
            }
            if (isEmpty()) append(result.toString())
        }.trim()
        return !isError to text.take(80_000)
    }

    private fun request(method: String, params: JsonObject): RpcReply {
        val id = synchronized(this) { nextId++ }
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("id", id)
            addProperty("method", method)
            add("params", params)
        }
        val transport = sse
        return if (transport != null) {
            transport.post(payload)
            val message = transport.await(id)
            parseReply(message, null)
        } else {
            post(payload)
        }
    }

    private fun notify(method: String): Unit {
        val payload = JsonObject().apply {
            addProperty("jsonrpc", "2.0")
            addProperty("method", method)
        }
        runCatching {
            val transport = sse
            if (transport != null) {
                transport.post(payload)
            } else {
                post(payload)
            }
        }
    }

    private fun post(payload: JsonObject): RpcReply {
        val builder = Request.Builder()
            .url(config.url)
            .header("Content-Type", "application/json")
            .header("Accept", "application/json, text/event-stream")
            .header("MCP-Protocol-Version", "2024-11-05")
        headers().forEach { (key, value) -> builder.header(key, value) }
        sessionId?.let { builder.header("Mcp-Session-Id", it) }
        val request = builder.post(payload.toString().toRequestBody(JSON)).build()
        http.newCall(request).execute().use { response ->
            val sid = response.header("Mcp-Session-Id")
            if (!response.isSuccessful) {
                val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                throw IOException("MCP HTTP ${response.code}: ${body.take(400)}")
            }
            val contentType = response.header("Content-Type").orEmpty()
            if (contentType.contains("text/event-stream")) {
                val message = readSseForId(response.body?.byteStream()?.bufferedReader(), payloadId(payload))
                    ?: throw IOException("MCP SSE 没有返回 id=${payloadId(payload)} 的响应")
                return parseReply(message, sid)
            }
            val text = response.body?.string().orEmpty()
            if (text.isBlank()) return RpcReply(JsonObject(), sid)
            val root = JsonParser.parseString(text).asJsonObject
            return parseReply(root, sid)
        }
    }

    private fun readSseForId(reader: java.io.BufferedReader?, id: Int): JsonObject? {
        if (reader == null) return null
        var data = StringBuilder()
        while (true) {
            val line = reader.readLine() ?: break
            if (line.isBlank()) {
                if (data.isNotEmpty()) {
                    val parsed = runCatching { JsonParser.parseString(data.toString()).asJsonObject }.getOrNull()
                    data = StringBuilder()
                    if (parsed != null && parsed.get("id")?.takeIf { !it.isJsonNull }?.asInt == id) {
                        return parsed
                    }
                }
            } else if (line.startsWith("data:")) {
                data.append(line.removePrefix("data:").trim())
            }
        }
        return null
    }

    private fun parseReply(root: JsonObject, sid: String?): RpcReply {
        val error = root.objOrNull("error")
        if (error != null) {
            val message = error.get("message")?.takeIf { !it.isJsonNull }?.asString ?: error.toString()
            throw IOException("MCP 错误：$message")
        }
        return RpcReply(root.objOrNull("result") ?: JsonObject(), sid)
    }

    private fun JsonObject.objOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrOrNull(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun payloadId(payload: JsonObject): Int =
        payload.get("id")?.takeIf { !it.isJsonNull }?.asInt ?: -1

    private fun headers(): Map<String, String> {
        if (config.headersJson.isBlank() || config.headersJson == "{}") return emptyMap()
        return runCatching {
            val obj = JsonParser.parseString(config.headersJson).asJsonObject
            obj.entrySet().associate { (key, value) -> key to value.asString }
        }.getOrDefault(emptyMap())
    }

    private fun schemaDescription(tool: JsonObject, fallback: String): String {
        val description = tool.get("description")?.takeIf { !it.isJsonNull }?.asString
        return description?.takeIf { it.isNotBlank() } ?: fallback
    }

    @Synchronized
    fun listResources(): List<JsonObject> {
        connect()
        val reply = request("resources/list", JsonObject())
        return (reply.result.getAsJsonArray("resources") ?: JsonArray())
            .mapNotNull { it as? JsonObject }
    }

    @Synchronized
    fun readResource(uri: String): String {
        connect()
        val reply = request("resources/read", JsonObject().apply { addProperty("uri", uri) })
        val contents = reply.result.getAsJsonArray("contents") ?: return "(空资源)"
        return buildString {
            contents.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val text = obj.get("text")?.takeIf { !it.isJsonNull }?.asString
                if (text != null) {
                    appendLine(text)
                } else {
                    val mime = obj.get("mimeType")?.takeIf { !it.isJsonNull }?.asString ?: "binary"
                    appendLine("[资源 $mime]")
                }
            }
        }.trim().ifBlank { "(空资源)" }
    }

    @Synchronized
    fun listPrompts(): List<JsonObject> {
        connect()
        val reply = request("prompts/list", JsonObject())
        return (reply.result.getAsJsonArray("prompts") ?: JsonArray())
            .mapNotNull { it as? JsonObject }
    }

    @Synchronized
    fun getPrompt(name: String, arguments: JsonObject): String {
        connect()
        val params = JsonObject().apply {
            addProperty("name", name)
            add("arguments", arguments)
        }
        val reply = request("prompts/get", params)
        val messages = reply.result.getAsJsonArray("messages") ?: return "(空提示词)"
        return buildString {
            messages.forEach { item ->
                val obj = item as? JsonObject ?: return@forEach
                val role = obj.get("role")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                val content = obj.get("content")
                val text = when {
                    content == null || content.isJsonNull -> ""
                    content.isJsonObject -> content.asJsonObject.get("text")?.takeIf { !it.isJsonNull }?.asString.orEmpty()
                    else -> content.asString
                }
                appendLine("[$role] $text")
            }
        }.trim().ifBlank { "(空提示词)" }
    }

    override fun close() {
        sse?.close()
        sse = null
    }

    private data class RpcReply(val result: JsonObject, val sessionId: String?)

    private inner class SseTransport(private val config: McpServerConfig) : Closeable {
        private val client = OkHttpClient.Builder()
            .connectTimeout(20, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .build()
        private val queue = LinkedBlockingQueue<JsonObject>()
        private val endpointLatch = CountDownLatch(1)

        @Volatile
        private var endpoint: String? = null

        @Volatile
        private var failure: String? = null

        private var call: okhttp3.Call? = null
        private var thread: Thread? = null

        fun start() {
            val builder = Request.Builder()
                .url(config.url)
                .header("Accept", "text/event-stream")
                .header("MCP-Protocol-Version", "2024-11-05")
            headers().forEach { (key, value) -> builder.header(key, value) }
            val request = builder.get().build()
            call = client.newCall(request)
            thread = thread(name = "mcp-sse", isDaemon = true) {
                try {
                    call?.execute()?.use { response ->
                        if (!response.isSuccessful) {
                            failure = "MCP SSE HTTP ${response.code}"
                            endpointLatch.countDown()
                            return@use
                        }
                        val reader = response.body?.byteStream()?.bufferedReader() ?: return@use
                        var event = "message"
                        val data = StringBuilder()
                        while (true) {
                            val line = reader.readLine() ?: break
                            if (line.isBlank()) {
                                dispatch(event, data.toString())
                                event = "message"
                                data.clear()
                            } else if (line.startsWith("event:")) {
                                event = line.removePrefix("event:").trim()
                            } else if (line.startsWith("data:")) {
                                data.append(line.removePrefix("data:").trim())
                            }
                        }
                    }
                } catch (error: Throwable) {
                    failure = error.message ?: error.javaClass.simpleName
                } finally {
                    endpointLatch.countDown()
                    queue.offer(JsonObject().apply { addProperty("__closed", true) })
                }
            }
            if (!endpointLatch.await(20, TimeUnit.SECONDS)) {
                throw IOException("MCP SSE 连接超时")
            }
            failure?.let { throw IOException(it) }
            if (endpoint == null) throw IOException("MCP SSE 没有返回 endpoint")
        }

        fun post(payload: JsonObject) {
            val target = endpoint ?: throw IOException("MCP SSE endpoint 未就绪")
            val builder = Request.Builder()
                .url(target)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json, text/event-stream")
                .header("MCP-Protocol-Version", "2024-11-05")
            headers().forEach { (key, value) -> builder.header(key, value) }
            val request = builder.post(payload.toString().toRequestBody(JSON)).build()
            client.newCall(request).execute().use { response ->
                if (!response.isSuccessful) {
                    val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    throw IOException("MCP SSE POST ${response.code}: ${body.take(300)}")
                }
            }
        }

        fun await(id: Int): JsonObject {
            val deadline = System.currentTimeMillis() + 60_000
            while (System.currentTimeMillis() < deadline) {
                val message = queue.poll(500, TimeUnit.MILLISECONDS) ?: continue
                if (message.has("__closed")) {
                    throw IOException(failure ?: "MCP SSE 已断开")
                }
                if (message.get("id")?.takeIf { !it.isJsonNull }?.asInt == id) return message
            }
            throw IOException("MCP SSE 等待响应超时")
        }

        private fun dispatch(event: String, data: String) {
            if (data.isBlank()) return
            if (event == "endpoint") {
                endpoint = config.url.toHttpUrlOrNull()?.resolve(data)?.toString()
                if (endpoint != null) endpointLatch.countDown()
                return
            }
            val parsed = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: return
            if (parsed.has("id") && !parsed.get("id").isJsonNull) {
                queue.offer(parsed)
            }
        }

        override fun close() {
            runCatching { call?.cancel() }
            thread?.interrupt()
        }
    }

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
    }
}
