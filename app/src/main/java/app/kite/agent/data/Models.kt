package app.kite.agent.data

import java.util.UUID

enum class Role {
    SYSTEM,
    USER,
    ASSISTANT,
    TOOL,
}

data class ToolCall(
    val id: String,
    val name: String,
    val arguments: String,
)

data class ImageAttachment(
    val mime: String = "image/jpeg",
    val base64: String = "",
    val name: String = "image",
)

data class FileAttachment(
    val name: String = "file",
    val mime: String = "text/plain",
    val text: String = "",
)

data class Message(
    val id: String = UUID.randomUUID().toString(),
    val role: Role,
    val content: String = "",
    val reasoning: String? = null,
    val toolCalls: List<ToolCall> = emptyList(),
    val toolCallId: String? = null,
    val toolName: String? = null,
    val toolOk: Boolean? = null,
    val attachments: List<ImageAttachment>? = null,
    val files: List<FileAttachment>? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val error: Boolean = false,
)

data class TodoItem(
    val text: String = "",
    val status: String = "pending", // pending | in_progress | done
)

data class Session(
    val id: String = UUID.randomUUID().toString(),
    val title: String = "新任务",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val messages: List<Message> = emptyList(),
    val todos: List<TodoItem>? = null,
)

data class SessionSummary(
    val id: String,
    val title: String,
    val updatedAt: Long,
    val preview: String,
)

data class ProviderConfig(
    val baseUrl: String = "https://api.deepseek.com/v1",
    val model: String = "deepseek-chat",
    val apiKey: String = "",
    val temperature: Double = 0.2,
    val maxSteps: Int = 12,
    /** off | low | medium | high */
    val reasoningEffort: String = "off",
)

data class ToolSettings(
    val shellEnabled: Boolean = false,
    val workspace: String = "",
)
