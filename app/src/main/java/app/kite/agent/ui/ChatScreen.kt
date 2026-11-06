package app.kite.agent.ui

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AddPhotoAlternate
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.RadioButtonChecked
import androidx.compose.material.icons.outlined.RadioButtonUnchecked
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Stop
import androidx.compose.material.icons.outlined.Terminal
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.agent.agent.ApprovalGate
import app.kite.agent.data.DocumentParser
import app.kite.agent.data.FileAttachment
import app.kite.agent.data.ImageAttachment
import app.kite.agent.data.Message
import app.kite.agent.data.Role
import app.kite.agent.data.Session
import app.kite.agent.data.TodoItem
import app.kite.agent.data.ToolCall
import app.kite.agent.ui.theme.Danger
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Mint
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper
import app.kite.agent.ui.theme.Scanner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@Composable
fun ChatScreen(
    session: Session,
    running: Boolean,
    runningTool: ToolCall?,
    modelName: String,
    contextTokens: Int,
    onSend: (String, List<ImageAttachment>, List<FileAttachment>) -> Unit,
    onStop: () -> Unit,
    onBack: () -> Unit,
    onSettings: () -> Unit,
) {
    val visible = remember(session.messages) {
        session.messages.filter { it.role != Role.TOOL }
    }
    val toolResults = remember(session.messages) {
        session.messages.filter { it.role == Role.TOOL }.associateBy { it.toolCallId }
    }
    val listState = rememberLazyListState()
    var input by rememberSaveable(session.id) { mutableStateOf("") }
    var pending by remember(session.id) { mutableStateOf<List<ImageAttachment>>(emptyList()) }
    var pendingFiles by remember(session.id) { mutableStateOf<List<FileAttachment>>(emptyList()) }
    val approval by ApprovalGate.request.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) {
            scope.launch {
                val attachment = loadImageAttachment(context, uri)
                if (attachment != null && pending.size < 4) pending = pending + attachment
            }
        }
    }
    val documentPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            scope.launch {
                val name = displayName(context, uri)
                val mime = context.contentResolver.getType(uri).orEmpty()
                val parsed = runCatching {
                    withContext(Dispatchers.IO) {
                        DocumentParser.parse(context, uri, name, mime)
                    }
                }
                parsed.onSuccess { text ->
                    if (pendingFiles.size < 3) {
                        pendingFiles = pendingFiles + FileAttachment(name = name, mime = mime, text = text)
                    }
                }.onFailure { error ->
                    Toast.makeText(
                        context,
                        error.message ?: "文件解析失败",
                        Toast.LENGTH_LONG,
                    ).show()
                }
            }
        }
    }
    val pickDocument = {
        documentPicker.launch(
            arrayOf(
                "application/pdf",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "text/*",
                "application/json",
            ),
        )
        Unit
    }
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                input = listOf(input.trim(), spoken.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            }
        }
    }
    val startVoice = {
        val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "zh-CN")
            putExtra(RecognizerIntent.EXTRA_PROMPT, "说出你想做的事")
        }
        runCatching { voiceLauncher.launch(intent) }.onFailure {
            Toast.makeText(context, "没有可用的语音识别服务", Toast.LENGTH_SHORT).show()
        }
        Unit
    }
    var continuous by remember { mutableStateOf(false) }
    val continuousVoice = remember {
        ContinuousVoice(
            context = context,
            onText = { spoken ->
                input = listOf(input.trim(), spoken.trim())
                    .filter { it.isNotBlank() }
                    .joinToString(" ")
            },
            onError = { message ->
                Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
            },
        )
    }
    val micPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            continuousVoice.start()
            continuous = true
        } else {
            Toast.makeText(context, "连续语音需要麦克风权限", Toast.LENGTH_SHORT).show()
        }
    }
    val toggleContinuous = {
        if (continuous) {
            continuousVoice.stop()
            continuous = false
        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) ==
            PackageManager.PERMISSION_GRANTED
        ) {
            continuousVoice.start()
            continuous = true
        } else {
            micPermission.launch(Manifest.permission.RECORD_AUDIO)
        }
        Unit
    }
    DisposableEffect(Unit) {
        onDispose { continuousVoice.stop() }
    }

    val last = visible.lastOrNull()
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    LaunchedEffect(
        visible.size,
        last?.content?.length,
        last?.reasoning?.length,
        running,
        pending.size,
        pendingFiles.size,
        imeVisible,
    ) {
        if (visible.isNotEmpty()) {
            listState.animateScrollToItem(visible.lastIndex)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink)
            .imePadding(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(58.dp)
                .padding(start = 6.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = "返回", tint = Paper)
            }
            ScannerMark(modifier = Modifier.size(22.dp))
            Spacer(Modifier.width(8.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.title,
                    color = Paper,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = buildString {
                        append(if (running) "正在执行" else "Kite · ${modelName.substringAfterLast('/')}")
                        if (contextTokens > 0) append(" · ~${contextTokens} tok")
                    },
                    color = if (running) Ember else Muted,
                    fontSize = 10.sp,
                    maxLines = 1,
                )
            }
            IconButton(onClick = onSettings) {
                Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Muted)
            }
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp)

        val todos = session.todos.orEmpty()
        if (todos.isNotEmpty()) {
            PlanCard(todos)
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.weight(1f),
            contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 16.dp, bottom = 16.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            items(visible, key = { it.id }) { message ->
                MessageBlock(
                    message = message,
                    toolResults = toolResults,
                    running = running,
                    runningTool = runningTool,
                    streaming = running && message.id == last?.id,
                )
            }
            if (running && (last == null || last.role == Role.USER || (last.content.isBlank() && last.toolCalls.isEmpty()))) {
                item { TypingRow() }
            }
        }

        Surface(
            color = Panel,
            shape = RoundedCornerShape(24.dp),
            border = BorderStroke(1.dp, Line),
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(start = 12.dp, end = 12.dp, top = 6.dp, bottom = 10.dp),
        ) {
            Column {
                if (pending.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pending.forEach { attachment ->
                            PendingImage(attachment) { pending = pending - attachment }
                        }
                        Text(
                            text = "图片需要视觉模型",
                            color = Muted.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                if (pendingFiles.isNotEmpty()) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 12.dp, end = 12.dp, top = 10.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        pendingFiles.forEach { file ->
                            PendingFile(file) { pendingFiles = pendingFiles - file }
                        }
                        Text(
                            text = "PDF/Office 已转成文本",
                            color = Muted.copy(alpha = 0.7f),
                            fontSize = 9.sp,
                            modifier = Modifier.align(Alignment.CenterVertically),
                        )
                    }
                }
                Row(
                    modifier = Modifier.padding(start = 6.dp, end = 8.dp, top = 4.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.Bottom,
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .pointerInput(continuous) {
                                detectTapGestures(
                                    onTap = { startVoice() },
                                    onLongPress = { toggleContinuous() },
                                )
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(
                            Icons.Outlined.Mic,
                            contentDescription = "点按语音输入，长按连续语音",
                            tint = if (continuous) Ember else Muted,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = pickDocument,
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AttachFile,
                            contentDescription = "添加 PDF/Office 文件",
                            tint = if (pendingFiles.isEmpty()) Muted else Ember,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    IconButton(
                        onClick = { picker.launch("image/*") },
                        modifier = Modifier.size(40.dp),
                    ) {
                        Icon(
                            Icons.Outlined.AddPhotoAlternate,
                            contentDescription = "添加图片",
                            tint = if (pending.isEmpty()) Muted else Ember,
                            modifier = Modifier.size(20.dp),
                        )
                    }
                    BasicTextField(
                        value = input,
                        onValueChange = { input = it },
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 40.dp, max = 130.dp)
                            .padding(vertical = 9.dp),
                        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper),
                        cursorBrush = SolidColor(Ember),
                        maxLines = 5,
                        decorationBox = { inner ->
                            Box {
                                if (input.isEmpty()) {
                                    Text(
                                        text = if (pending.isEmpty()) "继续安排…" else "说说这张图…",
                                        color = Muted.copy(alpha = 0.75f),
                                        style = MaterialTheme.typography.bodyLarge,
                                    )
                                }
                                inner()
                            }
                        },
                    )
                    Spacer(Modifier.width(8.dp))
                    if (running) {
                        FilledIconButton(
                            onClick = onStop,
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Panel2,
                                contentColor = Ember,
                            ),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(Icons.Outlined.Stop, contentDescription = "停止")
                        }
                    } else {
                        FilledIconButton(
                            onClick = {
                        if (input.isNotBlank() || pending.isNotEmpty() || pendingFiles.isNotEmpty()) {
                            onSend(input, pending, pendingFiles)
                            input = ""
                            pending = emptyList()
                            pendingFiles = emptyList()
                        }
                    },
                    enabled = input.isNotBlank() || pending.isNotEmpty() || pendingFiles.isNotEmpty(),
                            shape = CircleShape,
                            colors = IconButtonDefaults.filledIconButtonColors(
                                containerColor = Ember,
                                contentColor = Ink,
                                disabledContainerColor = Panel2,
                                disabledContentColor = Muted,
                            ),
                            modifier = Modifier.size(42.dp),
                        ) {
                            Icon(Icons.Outlined.ArrowUpward, contentDescription = "发送")
                        }
                    }
                }
            }
        }
    }

    approval?.let { request ->
        AlertDialog(
            onDismissRequest = { ApprovalGate.resolve(false) },
            containerColor = Panel,
            title = { Text("允许执行这个操作？", color = Paper) },
            text = {
                Column {
                    Text(request.tool, color = Ember, fontSize = 12.sp, fontFamily = FontFamily.Monospace)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        request.detail.ifBlank { "(无参数)" },
                        color = Muted,
                        fontSize = 12.sp,
                        lineHeight = 18.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text("这是危险操作，确认后才会执行。", color = Muted, fontSize = 11.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { ApprovalGate.resolve(true) }) {
                    Text("允许", color = Ember, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { ApprovalGate.resolve(false) }) { Text("拒绝", color = Muted) }
            },
        )
    }
}

@Composable
private fun MessageBlock(
    message: Message,
    toolResults: Map<String?, Message>,
    running: Boolean,
    runningTool: ToolCall?,
    streaming: Boolean,
) {
    when (message.role) {
        Role.USER -> Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
        ) {
            Surface(
                color = Panel2,
                shape = RoundedCornerShape(20.dp, 20.dp, 6.dp, 20.dp),
                modifier = Modifier.widthIn(max = 330.dp),
            ) {
                Column(modifier = Modifier.padding(horizontal = 10.dp, vertical = 9.dp)) {
                    message.attachments.orEmpty().forEach { attachment ->
                        val bitmap = remember(attachment.base64) { decodeAttachment(attachment) }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .clip(RoundedCornerShape(14.dp)),
                            )
                            Spacer(Modifier.height(6.dp))
                        }
                    }
                    if (message.content.isNotBlank()) {
                        Text(
                            text = message.content,
                            color = Paper,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp),
                        )
                    }
                }
            }
        }

        Role.ASSISTANT -> Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Top,
        ) {
            ScannerMark(modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(9.dp))
            Column(modifier = Modifier.weight(1f)) {
                if (!message.reasoning.isNullOrBlank()) {
                    ReasoningBlock(message.id, message.reasoning)
                    Spacer(Modifier.height(8.dp))
                }
                if (message.error) {
                    Surface(
                        color = Color(0xFF2A1518),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, Danger.copy(alpha = 0.45f)),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = message.content,
                            color = Color(0xFFFFC9C9),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(14.dp),
                        )
                    }
                } else if (message.content.isNotBlank()) {
                    MarkdownText(text = message.content)
                }
                if (streaming && message.content.isNotBlank()) {
                    Text(
                        text = "▍",
                        color = Scanner,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
                if (message.toolCalls.isNotEmpty()) {
                    Spacer(Modifier.height(if (message.content.isBlank()) 0.dp else 10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(7.dp)) {
                        message.toolCalls.forEach { call ->
                            ToolCallCard(
                                call = call,
                                result = toolResults[call.id],
                                running = running,
                                isCurrent = runningTool?.id == call.id,
                            )
                        }
                    }
                }
            }
        }

        else -> Unit
    }
}

@Composable
private fun ReasoningBlock(id: String, text: String) {
    var open by rememberSaveable(id) { mutableStateOf(false) }
    Column(modifier = Modifier.animateContentSize()) {
        Row(
            modifier = Modifier
                .clickable { open = !open }
                .padding(vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(Ember, CircleShape),
            )
            Spacer(Modifier.width(7.dp))
            Text(
                text = "思考过程",
                color = Muted,
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
            )
            Icon(
                imageVector = if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                contentDescription = null,
                tint = Muted,
                modifier = Modifier
                    .size(15.dp)
                    .padding(start = 2.dp),
            )
        }
        AnimatedVisibility(visible = open) {
            Text(
                text = text,
                color = Muted.copy(alpha = 0.85f),
                fontSize = 11.5.sp,
                lineHeight = 18.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(start = 12.dp, top = 4.dp),
            )
        }
    }
}

@Composable
private fun ToolCallCard(
    call: ToolCall,
    result: Message?,
    running: Boolean,
    isCurrent: Boolean,
) {
    var open by rememberSaveable(call.id) { mutableStateOf(false) }
    val failed = result?.toolOk == false
    val statusColor = when {
        failed -> Danger
        result == null && isCurrent -> Ember
        result == null -> Muted
        else -> Mint
    }
    val status = when {
        failed -> "失败"
        result == null && isCurrent -> "执行中"
        result == null -> if (running) "等待" else "未执行"
        else -> "完成"
    }

    Surface(
        color = Panel,
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, if (isCurrent) Ember.copy(alpha = 0.5f) else Line),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { open = !open },
    ) {
        Column {
            Row(
                modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 10.dp, bottom = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Terminal,
                    contentDescription = null,
                    tint = Ember,
                    modifier = Modifier.size(15.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = call.name,
                    color = Paper,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                if (isCurrent) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(12.dp),
                        strokeWidth = 1.5.dp,
                        color = Ember,
                    )
                } else {
                    Box(
                        modifier = Modifier
                            .size(6.dp)
                            .background(statusColor, CircleShape),
                    )
                }
                Spacer(Modifier.width(6.dp))
                Text(text = status, color = statusColor, fontSize = 10.sp)
                Icon(
                    imageVector = if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier
                        .size(16.dp)
                        .padding(start = 2.dp),
                )
            }
            AnimatedVisibility(visible = open) {
                Column {
                    HorizontalDivider(color = Line, thickness = 0.6.dp)
                    if (call.arguments.isNotBlank() && call.arguments != "{}") {
                        Text(
                            text = "→ " + call.arguments.take(500),
                            color = Muted,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 10.5.sp,
                            lineHeight = 16.sp,
                            modifier = Modifier.padding(start = 12.dp, end = 12.dp, top = 8.dp),
                        )
                    }
                    Text(
                        text = result?.content ?: "还没有输出",
                        color = Paper.copy(alpha = 0.82f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.5.sp,
                        lineHeight = 16.sp,
                        modifier = Modifier
                            .heightIn(max = 280.dp)
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun PlanCard(items: List<TodoItem>) {
    var open by rememberSaveable { mutableStateOf(true) }
    val done = items.count { it.status == "done" }
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 18.dp, end = 18.dp, top = 10.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { open = !open },
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("计划", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.width(6.dp))
                Text("$done/${items.size}", color = Muted, fontSize = 10.sp)
                Spacer(Modifier.weight(1f))
                Icon(
                    imageVector = if (open) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
                    contentDescription = null,
                    tint = Muted,
                    modifier = Modifier.size(16.dp),
                )
            }
            AnimatedVisibility(visible = open) {
                Column(
                    modifier = Modifier
                        .heightIn(max = 190.dp)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 6.dp),
                ) {
                    items.forEach { item ->
                        Row(
                            modifier = Modifier.padding(vertical = 4.dp),
                            verticalAlignment = Alignment.Top,
                        ) {
                            val icon = when (item.status) {
                                "done" -> Icons.Outlined.CheckCircle
                                "in_progress" -> Icons.Outlined.RadioButtonChecked
                                else -> Icons.Outlined.RadioButtonUnchecked
                            }
                            val tint = when (item.status) {
                                "done" -> Mint
                                "in_progress" -> Ember
                                else -> Muted
                            }
                            Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(7.dp))
                            Text(
                                text = item.text,
                                color = if (item.status == "done") Muted else Paper.copy(alpha = 0.92f),
                                fontSize = 11.5.sp,
                                lineHeight = 17.sp,
                                textDecoration = if (item.status == "done") TextDecoration.LineThrough else null,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PendingImage(attachment: ImageAttachment, onRemove: () -> Unit) {
    Box(
        modifier = Modifier
            .size(64.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(Panel2),
    ) {
        val bitmap = remember(attachment.base64) { decodeAttachment(attachment) }
        if (bitmap != null) {
            Image(
                bitmap = bitmap.asImageBitmap(),
                contentDescription = attachment.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
        IconButton(
            onClick = onRemove,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(22.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(18.dp)
                    .background(Color.Black.copy(alpha = 0.55f), CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "移除图片",
                    tint = Paper,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

@Composable
private fun PendingFile(file: FileAttachment, onRemove: () -> Unit) {
    Surface(
        color = Panel2,
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, Line),
    ) {
        Row(
            modifier = Modifier.padding(start = 10.dp, end = 4.dp, top = 6.dp, bottom = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(
                Icons.Outlined.AttachFile,
                contentDescription = null,
                tint = Ember,
                modifier = Modifier.size(14.dp),
            )
            Spacer(Modifier.width(6.dp))
            Text(
                text = file.name.take(18),
                color = Paper,
                fontSize = 10.5.sp,
                maxLines = 1,
            )
            Spacer(Modifier.width(2.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(22.dp)) {
                Icon(
                    Icons.Outlined.Close,
                    contentDescription = "移除文件",
                    tint = Muted,
                    modifier = Modifier.size(12.dp),
                )
            }
        }
    }
}

private fun displayName(context: Context, uri: Uri): String {
    return runCatching {
        context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) {
                cursor.getString(cursor.getColumnIndexOrThrow(OpenableColumns.DISPLAY_NAME))
            } else null
        }
    }.getOrNull() ?: "file"
}

@Composable
private fun TypingRow() {
    Text(
        text = "Kite 正在思考…",
        color = Muted.copy(alpha = 0.8f),
        fontSize = 12.sp,
        modifier = Modifier.padding(start = 2.dp),
    )
}
