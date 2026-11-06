package app.kite.agent.ui

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Visibility
import androidx.compose.material.icons.outlined.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.kite.agent.AppViewModel
import app.kite.agent.BuildConfig
import app.kite.agent.data.ProviderConfig
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Mint
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper

private data class Preset(val label: String, val baseUrl: String, val model: String)

private val presets = listOf(
    Preset("自定义", "", ""),
    Preset("DeepSeek", "https://api.deepseek.com/v1", "deepseek-chat"),
    Preset("OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    Preset("OpenRouter", "https://openrouter.ai/api/v1", "openai/gpt-4o-mini"),
    Preset("硅基流动", "https://api.siliconflow.cn/v1", "deepseek-ai/DeepSeek-V3"),
    Preset("智谱", "https://open.bigmodel.cn/api/paas/v4", "glm-4-flash"),
    Preset("Ollama", "http://192.168.1.20:11434/v1", "qwen2.5"),
)

@Composable
fun SettingsScreen(
    vm: AppViewModel,
    onBack: () -> Unit,
    onAccess: () -> Unit,
    onMcp: () -> Unit,
    onLicenses: () -> Unit,
    onSkills: () -> Unit,
    onScheduled: () -> Unit,
) {
    val provider by vm.provider.collectAsStateWithLifecycle()
    val shell by vm.shellEnabled.collectAsStateWithLifecycle()
    val approval by vm.approvalMode.collectAsStateWithLifecycle()
    val workspace by vm.workspace.collectAsStateWithLifecycle()
    val mcpServers by vm.mcpServers.collectAsStateWithLifecycle()
    val models by vm.models.collectAsStateWithLifecycle()
    val modelsLoading by vm.modelsLoading.collectAsStateWithLifecycle()
    val localTree by vm.localTree.collectAsStateWithLifecycle()

    var baseUrl by rememberSaveable { mutableStateOf(provider.baseUrl) }
    var model by rememberSaveable { mutableStateOf(provider.model) }
    var apiKey by rememberSaveable { mutableStateOf(provider.apiKey) }
    var effort by rememberSaveable { mutableStateOf(provider.reasoningEffort.ifBlank { "off" }) }
    var showKey by rememberSaveable { mutableStateOf(false) }
    var showModels by rememberSaveable { mutableStateOf(false) }
    var showPrivacy by rememberSaveable { mutableStateOf(false) }

    fun presetSelected(preset: Preset): Boolean {
        val current = baseUrl.trim().trimEnd('/')
        if (preset.baseUrl.isBlank()) {
            return presets.none {
                it.baseUrl.isNotBlank() && current == it.baseUrl.trimEnd('/')
            }
        }
        return current == preset.baseUrl.trimEnd('/')
    }

    val context = LocalContext.current
    val localTreePicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                )
            }
            vm.setLocalTree(uri.toString())
        }
    }
    val version = runCatching {
        context.packageManager.getPackageInfo(context.packageName, 0).versionName
    }.getOrNull() ?: "0.1.0"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
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
            Text(
                text = "设置",
                color = Paper,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
            )
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp)

        Column(
            modifier = Modifier
                .fillMaxSize()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(start = 20.dp, end = 20.dp, top = 14.dp, bottom = 40.dp),
        ) {
            SectionLabel("模型服务")

            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        presets.forEach { preset ->
                            FilterChip(
                                selected = presetSelected(preset),
                                onClick = {
                                    if (preset.baseUrl.isBlank()) {
                                        if (baseUrl.isBlank()) baseUrl = "http://"
                                    } else {
                                        baseUrl = preset.baseUrl
                                        model = preset.model
                                    }
                                },
                                label = { Text(preset.label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Ember.copy(alpha = 0.16f),
                                    selectedLabelColor = Ember,
                                    containerColor = Panel2,
                                    labelColor = Muted,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = presetSelected(preset),
                                    borderColor = Line,
                                    selectedBorderColor = Ember.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    KiteField(
                        label = "接口地址（含端口）",
                        value = baseUrl,
                        onValueChange = { baseUrl = it },
                        keyboardType = KeyboardType.Uri,
                    )
                    Text(
                        text = "预设只是快捷填充，地址可改；任意 OpenAI 兼容服务都行，带端口就写全，例如 http://192.168.1.20:11434/v1。",
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                    )
                    Spacer(Modifier.height(10.dp))
                    KiteField(
                        label = "API Key",
                        value = apiKey,
                        onValueChange = { apiKey = it },
                        keyboardType = KeyboardType.Password,
                        visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                        trailing = {
                            IconButton(onClick = { showKey = !showKey }) {
                                Icon(
                                    if (showKey) Icons.Outlined.VisibilityOff else Icons.Outlined.Visibility,
                                    contentDescription = null,
                                    tint = Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                    Spacer(Modifier.height(10.dp))
                    KiteField(
                        label = "模型名",
                        value = model,
                        onValueChange = { model = it },
                        trailing = {
                            IconButton(
                                onClick = {
                                    showModels = true
                                    vm.fetchModels(baseUrl, apiKey)
                                },
                            ) {
                                Icon(
                                    Icons.Outlined.Refresh,
                                    contentDescription = "获取模型列表",
                                    tint = if (modelsLoading) Ember else Muted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        },
                    )
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = if (modelsLoading) "正在获取模型列表…" else "从 /models 拉取，点右边选择。",
                            color = Muted,
                            fontSize = 10.sp,
                            lineHeight = 15.sp,
                            modifier = Modifier.weight(1f).padding(start = 4.dp, top = 5.dp),
                        )
                        TextButton(
                            onClick = {
                                showModels = true
                                vm.fetchModels(baseUrl, apiKey)
                            },
                            enabled = !modelsLoading,
                        ) {
                            Text("获取模型", color = Ember, fontSize = 10.sp)
                        }
                    }
                    Spacer(Modifier.height(14.dp))
                    Text("思考模式", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(6.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(
                            "off" to "关",
                            "low" to "低",
                            "medium" to "中",
                            "high" to "高",
                        ).forEach { (value, label) ->
                            FilterChip(
                                selected = effort == value,
                                onClick = { effort = value },
                                label = { Text(label, fontSize = 11.sp) },
                                colors = FilterChipDefaults.filterChipColors(
                                    selectedContainerColor = Ember.copy(alpha = 0.16f),
                                    selectedLabelColor = Ember,
                                    containerColor = Panel2,
                                    labelColor = Muted,
                                ),
                                border = FilterChipDefaults.filterChipBorder(
                                    enabled = true,
                                    selected = effort == value,
                                    borderColor = Line,
                                    selectedBorderColor = Ember.copy(alpha = 0.5f),
                                ),
                            )
                        }
                    }
                    Text(
                        text = "对支持 reasoning_effort 的模型生效（OpenAI、OpenRouter 等）；返回 400 就改回关。deepseek-reasoner 自己会思考。",
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                        modifier = Modifier.padding(start = 4.dp, top = 5.dp),
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(
                            onClick = {
                                vm.saveProvider(
                                    ProviderConfig(
                                        baseUrl = baseUrl,
                                        model = model,
                                        apiKey = apiKey,
                                        reasoningEffort = effort,
                                    ),
                                )
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = Ember,
                                contentColor = Ink,
                            ),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                        ) {
                            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("保存", fontWeight = FontWeight.Bold)
                        }
                        OutlinedButton(
                            onClick = {
                                vm.saveProvider(
                                    ProviderConfig(
                                        baseUrl = baseUrl,
                                        model = model,
                                        apiKey = apiKey,
                                        reasoningEffort = effort,
                                    ),
                                )
                                vm.testConnection()
                            },
                            shape = RoundedCornerShape(14.dp),
                            border = BorderStroke(1.dp, Line),
                            colors = ButtonDefaults.outlinedButtonColors(contentColor = Paper),
                            modifier = Modifier
                                .weight(1f)
                                .height(46.dp),
                        ) {
                            Icon(Icons.Outlined.Bolt, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("测试")
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = "Key 用 Android Keystore 加密，只保存在这台设备。",
                        color = Muted,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                    )
                }
            }

            SectionLabel("工具", top = 24.dp)

            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setShellEnabled(!shell) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "允许执行 Shell",
                                color = Paper,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "在应用沙盒里运行 /system/bin/sh 命令。关闭时只能用文件工具。",
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        Switch(
                            checked = shell,
                            onCheckedChange = { vm.setShellEnabled(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Ember,
                                uncheckedThumbColor = Muted,
                                uncheckedTrackColor = Panel2,
                                uncheckedBorderColor = Line,
                            ),
                        )
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { vm.setApprovalMode(!approval) }
                            .padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "危险操作前确认",
                                color = Paper,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "Shell、写文件、删除、移动、发短信前弹窗确认；文件改动可用 undo_last 撤销一级。",
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        Switch(
                            checked = approval,
                            onCheckedChange = { vm.setApprovalMode(it) },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = Ink,
                                checkedTrackColor = Ember,
                                uncheckedThumbColor = Muted,
                                uncheckedTrackColor = Panel2,
                                uncheckedBorderColor = Line,
                            ),
                        )
                    }
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = "工作区",
                            color = Paper,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = workspace,
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp,
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "文件工具只能读写这个目录。",
                            color = Muted.copy(alpha = 0.8f),
                            fontSize = 10.5.sp,
                        )
                    }
                }
            }

            SectionLabel("本地文件", top = 24.dp)
            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val folderName = remember(localTree) {
                        if (localTree.isBlank()) ""
                        else runCatching {
                            DocumentFile.fromTreeUri(context, Uri.parse(localTree))?.name
                        }.getOrNull().orEmpty()
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                "本地文件夹",
                                color = Paper,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                if (localTree.isBlank()) "未选择；不用所有文件权限，系统只授权这个文件夹"
                                else folderName.ifBlank { "已授权" },
                                color = if (localTree.isBlank()) Muted else Mint,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        TextButton(onClick = { localTreePicker.launch(null) }) {
                            Text(if (localTree.isBlank()) "选择" else "更换", color = Ember, fontSize = 11.sp)
                        }
                    }
                    if (localTree.isNotBlank()) {
                        Text(
                            localTree.take(80),
                            color = Muted.copy(alpha = 0.65f),
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                        TextButton(onClick = { vm.setLocalTree("") }) {
                            Text("清除授权", color = Muted, fontSize = 10.sp)
                        }
                    }
                    Text(
                        "Agent 通过 local_list / local_read / local_write 访问这个文件夹；推广版不需要 root。",
                        color = Muted,
                        fontSize = 10.sp,
                        lineHeight = 15.sp,
                    )
                }
            }

            SectionLabel("扩展", top = 24.dp)
            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onSkills),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("技能", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "SKILL.md 技能库；Agent 先读正文再执行",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Text("管理", color = Ember, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onScheduled),
            ) {
                Row(modifier = Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("定时任务", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "列表、取消、重复执行；后台跑完整 Agent",
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Text("管理", color = Ember, fontSize = 11.sp)
                }
            }
            Spacer(Modifier.height(10.dp))
            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onMcp),
            ) {
                Row(
                    modifier = Modifier.padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("MCP 服务器", color = Paper, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        Text(
                            "接入外部工具：API、数据库、浏览器、文件系统",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 16.sp,
                        )
                    }
                    Text(
                        "${mcpServers.size} 个",
                        color = if (mcpServers.isEmpty()) Muted else Ember,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }

            if (BuildConfig.PERSONAL) {
                SectionLabel("个人版", top = 24.dp)
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(22.dp),
                    border = BorderStroke(1.dp, Ember.copy(alpha = 0.35f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAccess),
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = "全部权限",
                                color = Paper,
                                fontSize = 14.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                text = "所有文件、Root、Shizuku、运行时权限",
                                color = Muted,
                                fontSize = 11.sp,
                                lineHeight = 16.sp,
                            )
                        }
                        Text("管理", color = Ember, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            SectionLabel("关于", top = 24.dp)

            Surface(
                color = Panel,
                shape = RoundedCornerShape(22.dp),
                border = BorderStroke(1.dp, Line),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column {
                    InfoRow("版本", version)
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    InfoRow("隐私说明", "查看", onClick = { showPrivacy = true })
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    InfoRow("开源许可", "查看", onClick = onLicenses)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = "Kite 是一个本地优先的移动智能体。会话、文件、Key 都在这台设备上，只有模型请求会发到你配置的服务。",
                color = Muted.copy(alpha = 0.7f),
                fontSize = 10.5.sp,
                lineHeight = 17.sp,
            )
        }
    }

    if (showModels) {
        AlertDialog(
            onDismissRequest = { showModels = false },
            containerColor = Panel,
            title = { Text("选择模型", color = Paper) },
            text = {
                when {
                    modelsLoading -> Text(
                        "正在请求 /models…",
                        color = Muted,
                        fontSize = 13.sp,
                    )

                    models.isEmpty() -> Text(
                        "没有获取到模型。\n检查接口地址是否带 /v1，以及 Key 是否有权限。",
                        color = Muted,
                        fontSize = 13.sp,
                        lineHeight = 20.sp,
                    )

                    else -> Column(
                        modifier = Modifier
                            .heightIn(max = 380.dp)
                            .verticalScroll(rememberScrollState()),
                    ) {
                        models.forEach { item ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable {
                                        model = item
                                        showModels = false
                                    }
                                    .padding(vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    text = item,
                                    color = if (item == model) Ember else Paper,
                                    fontSize = 12.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.weight(1f),
                                )
                                if (item == model) {
                                    Icon(
                                        Icons.Outlined.Check,
                                        contentDescription = null,
                                        tint = Ember,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                            HorizontalDivider(color = Line, thickness = 0.5.dp)
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showModels = false }) { Text("关闭", color = Ember) }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        vm.fetchModels(baseUrl, apiKey)
                    },
                    enabled = !modelsLoading,
                ) { Text("重试", color = Muted) }
            },
        )
    }

    if (showPrivacy) {
        AlertDialog(
            onDismissRequest = { showPrivacy = false },
            containerColor = Panel,
            title = { Text("隐私说明", color = Paper) },
            text = {
                Text(
                    text = "Kite 不收集个人数据，也不含统计 SDK。\n\n" +
                        "• 会话和工作区文件保存在本机应用目录。\n" +
                        "• API Key 使用 Android Keystore 加密保存。\n" +
                        "• 模型请求会发送到你配置的接口地址，受该服务商的隐私政策约束。\n" +
                        "• Shell 命令仅在你手动开启后执行，且限制在应用沙盒内。",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacy = false }) { Text("知道了", color = Ember) }
            },
        )
    }
}

@Composable
private fun SectionLabel(text: String, top: androidx.compose.ui.unit.Dp = 4.dp) {
    Text(
        text = text.uppercase(),
        color = Muted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        letterSpacing = 2.sp,
        modifier = Modifier.padding(top = top, bottom = 8.dp, start = 2.dp),
    )
}

@Composable
private fun KiteField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    keyboardType: KeyboardType = KeyboardType.Text,
    visualTransformation: VisualTransformation = VisualTransformation.None,
    trailing: @Composable (() -> Unit)? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, fontSize = 11.sp) },
        singleLine = true,
        visualTransformation = visualTransformation,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
        trailingIcon = trailing,
        shape = RoundedCornerShape(14.dp),
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

@Composable
private fun InfoRow(label: String, value: String, onClick: (() -> Unit)? = null) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, color = Paper, fontSize = 13.sp)
        Spacer(Modifier.weight(1f))
        Text(value, color = if (onClick != null) Ember else Muted, fontSize = 12.sp)
    }
}
