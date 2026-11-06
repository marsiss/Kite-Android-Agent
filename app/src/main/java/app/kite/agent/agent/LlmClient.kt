package app.kite.agent.agent

import app.kite.agent.data.Message
import app.kite.agent.data.ProviderConfig
import app.kite.agent.data.Role
import app.kite.agent.data.ToolCall
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import okhttp3.Call
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.IOException
import java.util.UUID
import java.util.concurrent.TimeUnit

data class StreamResult(
    val content: String,
    val reasoning: String,
    val toolCalls: List<ToolCall>,
)

/**
 * OpenAI-compatible chat completions client with SSE streaming and tool calls.
 */
class LlmClient(private val config: ProviderConfig) {

    @Volatile
    private var activeCall: Call? = null

    fun cancel() {
        activeCall?.cancel()
    }

    fun stream(
        messages: List<Message>,
        tools: JsonArray,
        onDelta: (String) -> Unit,
        onReasoning: (String) -> Unit,
    ): StreamResult {
        val payload = buildPayload(messages, tools)
        val request = Request.Builder()
            .url(endpoint())
            .header("Content-Type", "application/json")
            .header("Accept", "text/event-stream")
            .apply {
                if (config.apiKey.isNotBlank()) header("Authorization", "Bearer ${config.apiKey}")
            }
            .post(payload.toString().toRequestBody(JSON))
            .build()

        val call = HTTP.newCall(request)
        activeCall = call
        try {
            call.execute().use { response ->
                if (!response.isSuccessful) {
                    val body = runCatching { response.body?.string() }.getOrNull().orEmpty()
                    throw IOException("HTTP ${response.code}: ${body.take(800)}")
                }
                val source = response.body?.source() ?: throw IOException("空响应")
                val content = StringBuilder()
                val reasoning = StringBuilder()
                val calls = sortedMapOf<Int, ToolAccumulator>()

                while (true) {
                    val line = source.readUtf8Line() ?: break
                    if (!line.startsWith("data:")) continue
                    val data = line.removePrefix("data:").trim()
                    if (data.isEmpty()) continue
                    if (data == "[DONE]") break
                    val root = runCatching { JsonParser.parseString(data).asJsonObject }.getOrNull() ?: continue
                    root.get("error")?.takeIf { !it.isJsonNull }?.let { error ->
                        val message = error.takeIf { it.isJsonObject }
                            ?.asJsonObject?.get("message")?.takeIf { it.isJsonPrimitive }?.asString
                            ?: error.toString()
                        throw IOException(message.take(800))
                    }
                    val choices = root.arrayOrNull("choices") ?: continue
                    if (choices.size() == 0) continue
                    val choice = choices.firstOrNull { it.isJsonObject }?.asJsonObject ?: continue
                    val delta = choice.objectOrNull("delta") ?: continue

                    delta.stringOrNull("content")?.let {
                        content.append(it)
                        onDelta(it)
                    }
                    delta.stringOrNull("reasoning_content")?.let {
                        reasoning.append(it)
                        onReasoning(it)
                    }
                    delta.arrayOrNull("tool_calls")?.forEach { element ->
                        val obj = element as? JsonObject ?: return@forEach
                        val index = obj.get("index")?.takeIf { it.isJsonPrimitive }?.asInt ?: 0
                        val acc = calls.getOrPut(index) { ToolAccumulator() }
                        obj.stringOrNull("id")?.let { acc.id = it }
                        obj.objectOrNull("function")?.let { fn ->
                            fn.stringOrNull("name")?.let { acc.name = it }
                            fn.stringOrNull("arguments")?.let { acc.arguments.append(it) }
                        }
                    }
                }

                val toolCalls = calls.values
                    .filter { it.name.isNotBlank() }
                    .map {
                        ToolCall(
                            id = it.id.ifBlank { "call_" + UUID.randomUUID().toString().take(8) },
                            name = it.name,
                            arguments = it.arguments.toString().ifBlank { "{}" },
                        )
                    }
                return StreamResult(content.toString(), reasoning.toString(), toolCalls)
            }
        } finally {
            activeCall = null
        }
    }

    private fun buildPayload(messages: List<Message>, tools: JsonArray): JsonObject {
        val array = JsonArray()
        messages.forEach { message ->
            val json = messageJson(message) ?: return@forEach
            array.add(json)
        }
        return JsonObject().apply {
            addProperty("model", config.model)
            addProperty("stream", true)
            addProperty("temperature", config.temperature)
            add("messages", array)
            if (tools.size() > 0) add("tools", tools)
            val effort = config.reasoningEffort.lowercase()
            if (effort in setOf("low", "medium", "high")) {
                if (config.baseUrl.contains("openrouter.ai", ignoreCase = true)) {
                    add(
                        "reasoning",
                        JsonObject().apply { addProperty("effort", effort) },
                    )
                } else {
                    addProperty("reasoning_effort", effort)
                }
            }
        }
    }

    private fun messageJson(message: Message): JsonObject? {
        return when (message.role) {
            Role.SYSTEM -> JsonObject().apply {
                addProperty("role", "system")
                addProperty("content", message.content)
            }

            Role.USER -> JsonObject().apply {
                addProperty("role", "user")
                val images = message.attachments.orEmpty()
                val files = message.files.orEmpty()
                val fileBlock = files.joinToString("\n\n") {
                    "【附件：${it.name}】\n${it.text.take(60_000)}"
                }.take(120_000)
                val text = buildString {
                    if (message.content.isNotBlank()) append(message.content)
                    if (fileBlock.isNotBlank()) {
                        if (isNotEmpty()) append("\n\n")
                        append(fileBlock)
                    }
                }.ifBlank { if (images.isNotEmpty()) "看看这张图" else "" }
                if (images.isEmpty()) {
                    addProperty("content", text)
                } else {
                    add(
                        "content",
                        JsonArray().apply {
                            add(JsonObject().apply {
                                addProperty("type", "text")
                                addProperty("text", text)
                            })
                            images.forEach { image ->
                                add(JsonObject().apply {
                                    addProperty("type", "image_url")
                                    add(
                                        "image_url",
                                        JsonObject().apply {
                                            addProperty("url", "data:${image.mime};base64,${image.base64}")
                                        },
                                    )
                                })
                            }
                        },
                    )
                }
            }

            Role.ASSISTANT -> {
                if (message.content.isBlank() && message.toolCalls.isEmpty()) return null
                JsonObject().apply {
                    addProperty("role", "assistant")
                    addProperty("content", message.content)
                    if (message.toolCalls.isNotEmpty()) {
                        add(
                            "tool_calls",
                            JsonArray().apply {
                                message.toolCalls.forEach { call ->
                                    add(JsonObject().apply {
                                        addProperty("id", call.id)
                                        addProperty("type", "function")
                                        add(
                                            "function",
                                            JsonObject().apply {
                                                addProperty("name", call.name)
                                                addProperty("arguments", call.arguments)
                                            },
                                        )
                                    })
                                }
                            },
                        )
                    }
                }
            }

            Role.TOOL -> JsonObject().apply {
                addProperty("role", "tool")
                addProperty("tool_call_id", message.toolCallId ?: "")
                addProperty("content", message.content)
            }
        }
    }

    private fun endpoint(): String {
        val base = config.baseUrl.trim().trimEnd('/')
        if (base.isEmpty()) throw IOException("接口地址为空")
        return if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
    }

    private class ToolAccumulator {
        var id: String = ""
        var name: String = ""
        val arguments: StringBuilder = StringBuilder()
    }

    private fun JsonObject.objectOrNull(key: String): JsonObject? =
        get(key)?.takeIf { it.isJsonObject }?.asJsonObject

    private fun JsonObject.arrayOrNull(key: String): JsonArray? =
        get(key)?.takeIf { it.isJsonArray }?.asJsonArray

    private fun JsonObject.stringOrNull(key: String): String? =
        get(key)?.takeIf { it.isJsonPrimitive && it.asJsonPrimitive.isString }?.asString

    companion object {
        private val JSON = "application/json; charset=utf-8".toMediaType()
        private val HTTP: OkHttpClient = OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(0, TimeUnit.SECONDS)
            .writeTimeout(30, TimeUnit.SECONDS)
            .build()
    }
}
