package app.kite.agent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.agent.AppViewModel
import app.kite.agent.data.McpServerConfig
import app.kite.agent.ui.theme.Danger
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Mint
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper

@Composable
fun McpScreen(vm: AppViewModel, onBack: () -> Unit) {
    val servers by vm.mcpServers.collectAsStateWithLifecycle()
    val status by vm.mcpStatus.collectAsStateWithLifecycle()
    val tools by vm.mcpTools.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<McpServerConfig?>(null) }
    var dialogOpen by rememberSaveable { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<McpServerConfig?>(null) }

    Column(modifier = Modifier.fillMaxSize().background(Ink)) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(horizontal = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Paper)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text("MCP", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    text = "${servers.count { it.enabled }} 个服务器 · ${tools.size} 个工具",
                    color = Muted,
                    fontSize = 10.sp,
                )
            }
            IconButton(onClick = {
                editing = null
                dialogOpen = true
            }) {
                Icon(Icons.Outlined.Add, contentDescription = "添加 MCP 服务器", tint = Ember)
            }
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
        ) {
            Text(
                text = "MCP 让 Kite 接入外部工具：远程 API、数据库、浏览器、文件系统等。支持 Streamable HTTP 和旧版 SSE。",
                color = Muted,
                fontSize = 12.sp,
                lineHeight = 19.sp,
            )
            Spacer(Modifier.height(14.dp))

            if (servers.isEmpty()) {
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(20.dp),
                    border = BorderStroke(1.dp, Line),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(modifier = Modifier.padding(20.dp)) {
                        Text("还没有 MCP 服务器", color = Paper, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "添加一个 MCP 地址，Kite 会自动发现它的工具，并在对话里调用。",
                            color = Muted,
                            fontSize = 12.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }

            servers.forEach { config ->
                Spacer(Modifier.height(10.dp))
                McpServerCard(
                    config = config,
                    status = status[config.id],
                    toolCount = tools.count { it.serverId == config.id },
                    onToggle = { enabled -> vm.saveMcpServer(config.copy(enabled = enabled)) },
                    onEdit = {
                        editing = config
                        dialogOpen = true
                    },
                    onTest = { vm.testMcpServer(config) },
                    onDelete = { deleteTarget = config },
                )
            }

            Spacer(Modifier.height(14.dp))
            Button(
                onClick = {
                    editing = null
                    dialogOpen = true
                },
                shape = RoundedCornerShape(14.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink),
                modifier = Modifier.fillMaxWidth().height(46.dp),
            ) {
                Icon(Icons.Outlined.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("添加 MCP 服务器", fontWeight = FontWeight.Bold)
            }

            Spacer(Modifier.height(16.dp))
            Text(
                text = "提示：Streamable HTTP 通常形如 https://example.com/mcp；旧版 SSE 形如 https://example.com/sse。需要 Token 时在 Header JSON 里填 {\"Authorization\":\"Bearer ...\"}。",
                color = Muted.copy(alpha = 0.75f),
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
            )
        }
    }

    if (dialogOpen) {
        McpEditorDialog(
            initial = editing,
            onDismiss = { dialogOpen = false },
            onSave = { config ->
                vm.saveMcpServer(config)
                dialogOpen = false
            },
            onTest = { config -> vm.testMcpServer(config) },
        )
    }

    deleteTarget?.let { target ->
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { deleteTarget = null },
            containerColor = Panel,
            title = { Text("删除 MCP 服务器？", color = Paper) },
            text = { Text("只会删除配置，不影响服务器端。", color = Muted, fontSize = 13.sp) },
            confirmButton = {
                TextButton(onClick = {
                    vm.deleteMcpServer(target.id)
                    deleteTarget = null
                }) { Text("删除", color = Danger) }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("取消", color = Muted) }
            },
        )
    }
}

@Composable
private fun McpServerCard(
    config: McpServerConfig,
    status: String?,
    toolCount: Int,
    onToggle: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onTest: () -> Unit,
    onDelete: () -> Unit,
) {
    val ok = status?.startsWith("ok:") == true
    val error = status?.startsWith("error:") == true
    Surface(
        color = Panel,
        shape = RoundedCornerShape(18.dp),
        border = BorderStroke(1.dp, if (error) Danger.copy(alpha = 0.4f) else Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = config.name.ifBlank { "MCP" },
                        color = Paper,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = config.url,
                        color = Muted,
                        fontSize = 10.sp,
                        fontFamily = FontFamily.Monospace,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Switch(
                    checked = config.enabled,
                    onCheckedChange = onToggle,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = Ink,
                        checkedTrackColor = Ember,
                        uncheckedThumbColor = Muted,
                        uncheckedTrackColor = Panel2,
                        uncheckedBorderColor = Line,
                    ),
                )
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = when {
                        !config.enabled -> "已停用"
                        error -> status?.removePrefix("error:")?.take(60) ?: "连接失败"
                        ok -> "已连接 · $toolCount 个工具"
                        else -> "未连接"
                    },
                    color = when {
                        error -> Danger
                        ok -> Mint
                        else -> Muted
                    },
                    fontSize = 10.5.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                TextButton(onClick = onTest) { Text("测试", color = Ember, fontSize = 11.sp) }
                TextButton(onClick = onEdit) { Text("编辑", color = Paper, fontSize = 11.sp) }
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Outlined.Delete, contentDescription = "删除", tint = Muted, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}

@Composable
private fun McpEditorDialog(
    initial: McpServerConfig?,
    onDismiss: () -> Unit,
    onSave: (McpServerConfig) -> Unit,
    onTest: (McpServerConfig) -> Unit,
) {
    var name by remember { mutableStateOf(initial?.name ?: "MCP") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var transport by remember { mutableStateOf(initial?.transport ?: "http") }
    var headers by remember { mutableStateOf(initial?.headersJson ?: "{}") }
    var enabled by remember { mutableStateOf(initial?.enabled ?: true) }

    fun current() = McpServerConfig(
        id = initial?.id ?: java.util.UUID.randomUUID().toString(),
        name = name.ifBlank { "MCP" },
        url = url.trim(),
        transport = transport,
        headersJson = headers.ifBlank { "{}" },
        enabled = enabled,
    )

    Dialog(onDismissRequest = onDismiss) {
        Surface(
            color = Panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier.fillMaxWidth().heightIn(max = 620.dp),
        ) {
            Column(
                modifier = Modifier
                    .verticalScroll(rememberScrollState())
                    .padding(20.dp),
            ) {
                Text(
                    if (initial == null) "添加 MCP 服务器" else "编辑 MCP 服务器",
                    color = Paper,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(Modifier.height(14.dp))
                McpField("名称", name, { name = it })
                Spacer(Modifier.height(10.dp))
                McpField("URL", url, { url = it }, keyboard = KeyboardType.Uri)
                Spacer(Modifier.height(10.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(
                        selected = transport == "http",
                        onClick = { transport = "http" },
                        label = { Text("Streamable HTTP", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Ember.copy(alpha = 0.16f),
                            selectedLabelColor = Ember,
                            containerColor = Panel2,
                            labelColor = Muted,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = transport == "http",
                            borderColor = Line,
                            selectedBorderColor = Ember.copy(alpha = 0.5f),
                        ),
                    )
                    FilterChip(
                        selected = transport == "sse",
                        onClick = { transport = "sse" },
                        label = { Text("旧版 SSE", fontSize = 10.sp) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = Ember.copy(alpha = 0.16f),
                            selectedLabelColor = Ember,
                            containerColor = Panel2,
                            labelColor = Muted,
                        ),
                        border = FilterChipDefaults.filterChipBorder(
                            enabled = true,
                            selected = transport == "sse",
                            borderColor = Line,
                            selectedBorderColor = Ember.copy(alpha = 0.5f),
                        ),
                    )
                }
                Spacer(Modifier.height(10.dp))
                McpField("Header JSON（可选）", headers, { headers = it }, maxLines = 3)
                Spacer(Modifier.height(4.dp))
                Text("例如 {\"Authorization\":\"Bearer xxx\"}", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.height(8.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("启用", color = Paper, fontSize = 13.sp)
                    Spacer(Modifier.weight(1f))
                    Switch(
                        checked = enabled,
                        onCheckedChange = { enabled = it },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Ink,
                            checkedTrackColor = Ember,
                        ),
                    )
                }
                Spacer(Modifier.height(14.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    TextButton(
                        onClick = { onTest(current()) },
                        modifier = Modifier.weight(1f),
                    ) {
                        Text("测试连接", color = Paper)
                    }
                    Button(
                        onClick = {
                            if (url.isNotBlank()) onSave(current())
                        },
                        enabled = url.isNotBlank(),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Ember, contentColor = Ink),
                        modifier = Modifier.weight(1f).height(44.dp),
                    ) {
                        Text("保存", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
private fun McpField(
    label: String,
    value: String,
    onChange: (String) -> Unit,
    keyboard: KeyboardType = KeyboardType.Text,
    maxLines: Int = 1,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label, fontSize = 10.sp) },
        singleLine = maxLines == 1,
        maxLines = maxLines,
        keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(keyboardType = keyboard),
        shape = RoundedCornerShape(12.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedTextColor = Paper,
            unfocusedTextColor = Paper,
            focusedContainerColor = Panel2,
            unfocusedContainerColor = Panel2,
            cursorColor = Ember,
            focusedBorderColor = Ember.copy(alpha = 0.7f),
            unfocusedBorderColor = Line,
            focusedLabelColor = Ember,
            unfocusedLabelColor = Muted,
        ),
        modifier = Modifier.fillMaxWidth(),
    )
}
