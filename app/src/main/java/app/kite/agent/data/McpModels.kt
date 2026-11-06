package app.kite.agent.data

import com.google.gson.JsonObject
import java.util.UUID

data class McpServerConfig(
    val id: String = UUID.randomUUID().toString(),
    val name: String = "MCP",
    val url: String = "",
    val transport: String = "http", // http | sse
    val headersJson: String = "{}",
    val enabled: Boolean = true,
)

data class McpToolInfo(
    val serverId: String,
    val serverName: String,
    val name: String,
    val exposedName: String,
    val description: String,
    val inputSchema: JsonObject,
)
