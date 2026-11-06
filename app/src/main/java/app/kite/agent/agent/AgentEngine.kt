package app.kite.agent.agent

import app.kite.agent.data.Message
import app.kite.agent.data.ProviderConfig
import app.kite.agent.data.Role
import app.kite.agent.data.SkillStore
import app.kite.agent.data.TodoItem
import app.kite.agent.data.ToolCall
import android.content.Context
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.channelFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.isActive

sealed interface AgentEvent {
    data class Delta(val text: String) : AgentEvent
    data class ReasoningDelta(val text: String) : AgentEvent
    data class AssistantMessage(val message: Message) : AgentEvent
    data class ToolStarted(val call: ToolCall) : AgentEvent
    data class ToolFinished(val call: ToolCall, val output: String, val ok: Boolean) : AgentEvent
    data class ToolMessage(val message: Message) : AgentEvent
    data class PlanUpdated(val items: List<TodoItem>) : AgentEvent
    data class ContextStats(val tokens: Int, val messages: Int, val compressed: Boolean = false) : AgentEvent
    data class Step(val index: Int) : AgentEvent
    data class Failure(val message: String) : AgentEvent
    data object Done : AgentEvent
}

class AgentEngine(
    private val context: Context,
    private val config: ProviderConfig,
    private val workspace: File,
    private val shellEnabled: Boolean,
    private val allowExternal: Boolean = false,
    private val shellMode: String = "normal",
    private val mcp: app.kite.agent.mcp.McpManager? = null,
    private val depth: Int = 0,
    private val approval: Boolean = false,
) {

    @Volatile
    private var client: LlmClient? = null

    fun cancel() {
        client?.cancel()
    }

    fun run(history: List<Message>): Flow<AgentEvent> = channelFlow {
        val conversation = mutableListOf<Message>()
        conversation += Message(
            role = Role.SYSTEM,
            content = systemPrompt(workspace.absolutePath, SkillStore(context, workspace).list()),
        )
        conversation += history
            .filter { it.role != Role.SYSTEM }
            .takeLast(60)

        var step = 0
        try {
            while (step < config.maxSteps && isActive) {
                step++
                trySend(AgentEvent.Step(step))
                val before = estimateTokens(conversation)
                if (before > 24_000 && conversation.size > 34) {
                    compress(conversation)
                    trySend(AgentEvent.ContextStats(estimateTokens(conversation), conversation.size, true))
                } else {
                    trySend(AgentEvent.ContextStats(before, conversation.size))
                }

                val llm = LlmClient(config)
                client = llm
                val tools = JsonArray().apply {
                    ToolDefs.definitions.forEach { element ->
                        val name = runCatching {
                            element.asJsonObject.getAsJsonObject("function").get("name").asString
                        }.getOrNull()
                        if (depth == 0 || name != "spawn_subagent") add(element)
                    }
                    mcp?.toolDefs()?.let { addAll(it) }
                }
                val result = llm.stream(
                    messages = conversation,
                    tools = tools,
                    onDelta = { text -> trySend(AgentEvent.Delta(text)) },
                    onReasoning = { text -> trySend(AgentEvent.ReasoningDelta(text)) },
                )

                val assistant = Message(
                    role = Role.ASSISTANT,
                    content = result.content,
                    reasoning = result.reasoning.ifBlank { null },
                    toolCalls = result.toolCalls,
                )
                conversation += assistant
                trySend(AgentEvent.AssistantMessage(assistant))

                if (result.toolCalls.isEmpty()) {
                    trySend(AgentEvent.Done)
                    return@channelFlow
                }

                val runner = ToolRunner(context, workspace, shellEnabled, allowExternal, shellMode, mcp, approval)
                result.toolCalls.forEach { call ->
                    if (!isActive) return@forEach
                    trySend(AgentEvent.ToolStarted(call))
                    val outcome = when (call.name) {
                        "spawn_subagent" -> runSubAgent(call)
                        "todo_write" -> {
                            val items = parseTodos(parseArgs(call))
                            if (items.isEmpty()) {
                                ToolResult(false, "计划为空，items 里至少放一项")
                            } else {
                                trySend(AgentEvent.PlanUpdated(items))
                                ToolResult(
                                    true,
                                    "计划已更新（${items.size} 项）：\n" + items.joinToString("\n") {
                                        "${if (it.status == "done") "[x]" else if (it.status == "in_progress") "[>]" else "[ ]"} ${it.text}"
                                    },
                                )
                            }
                        }
                        else -> runner.run(call)
                    }
                    trySend(AgentEvent.ToolFinished(call, outcome.output, outcome.ok))
                    val toolMessage = Message(
                        role = Role.TOOL,
                        content = outcome.output.take(80_000),
                        toolCallId = call.id,
                        toolName = call.name,
                        toolOk = outcome.ok,
                    )
                    conversation += toolMessage
                    trySend(AgentEvent.ToolMessage(toolMessage))
                }
            }
            trySend(AgentEvent.Failure("达到最大执行步数（${config.maxSteps}），任务已暂停。"))
        } catch (cancelled: CancellationException) {
            throw cancelled
        } catch (error: Throwable) {
            android.util.Log.e("KiteAgent", "agent run failed", error)
            trySend(AgentEvent.Failure(error.message ?: error.javaClass.simpleName))
        }
    }.flowOn(Dispatchers.IO)

    private suspend fun runSubAgent(call: ToolCall): ToolResult {
        if (depth > 0) return ToolResult(false, "子智能体不能再启动子智能体")
        val args = parseArgs(call)
        val task = args.string("task", "")
        if (task.isBlank()) return ToolResult(false, "缺少 task")
        val maxSteps = args.int("max_steps", 6).coerceIn(2, 10)
        val sub = AgentEngine(
            context = context,
            config = config.copy(maxSteps = maxSteps),
            workspace = workspace,
            shellEnabled = shellEnabled,
            allowExternal = allowExternal,
            shellMode = shellMode,
            mcp = mcp,
            depth = depth + 1,
            approval = approval,
        )
        var finalText = ""
        var failure = ""
        runCatching {
            sub.run(
                listOf(
                    Message(
                        role = Role.USER,
                        content = "你是 Kite 的子智能体。独立完成下面的任务，最后给出简洁的结论：\n$task",
                    ),
                ),
            ).collect { event ->
                when (event) {
                    is AgentEvent.AssistantMessage -> {
                        if (event.message.content.isNotBlank()) finalText = event.message.content
                    }
                    is AgentEvent.Failure -> failure = event.message
                    else -> Unit
                }
            }
        }.onFailure { failure = it.message ?: it.javaClass.simpleName }
        return if (finalText.isNotBlank()) {
            ToolResult(true, finalText.take(40_000))
        } else {
            ToolResult(false, failure.ifBlank { "子智能体没有返回内容" })
        }
    }

    private fun parseArgs(call: ToolCall): JsonObject =
        runCatching { JsonParser.parseString(call.arguments).asJsonObject }.getOrElse { JsonObject() }

    private fun estimateTokens(messages: List<Message>): Int =
        messages.sumOf { message ->
            message.content.length + message.reasoning.orEmpty().length +
                message.toolCalls.sumOf { it.arguments.length + it.name.length }
        } / 4

    private fun compress(conversation: MutableList<Message>) {
        val system = conversation.firstOrNull { it.role == Role.SYSTEM }
        val recent = conversation.filter { it.role != Role.SYSTEM }.takeLast(30)
        val dropped = conversation.size - recent.size - (if (system != null) 1 else 0)
        conversation.clear()
        if (system != null) conversation += system
        conversation += Message(
            role = Role.SYSTEM,
            content = "[上下文压缩] 为控制 token，已省略较早的 $dropped 条消息；保留最近 30 条和当前任务状态。需要细节时用文件/工具重新读取。",
        )
        conversation += recent
    }

    private fun parseTodos(args: JsonObject): List<TodoItem> {
        val array = args.getAsJsonArray("items") ?: return emptyList()
        return array.mapNotNull { element ->
            val obj = element as? JsonObject ?: return@mapNotNull null
            val text = obj.get("text")?.takeIf { !it.isJsonNull }?.asString?.trim().orEmpty()
            if (text.isBlank()) return@mapNotNull null
            val status = obj.get("status")?.takeIf { !it.isJsonNull }?.asString
                ?.takeIf { it in setOf("pending", "in_progress", "done") } ?: "pending"
            TodoItem(text = text, status = status)
        }
    }

    private fun JsonObject.string(key: String, fallback: String): String =
        if (has(key) && !get(key).isJsonNull) get(key).asString else fallback

    private fun JsonObject.int(key: String, fallback: Int): Int =
        if (has(key) && !get(key).isJsonNull) runCatching { get(key).asInt }.getOrDefault(fallback) else fallback
}
