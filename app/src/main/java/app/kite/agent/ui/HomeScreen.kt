package app.kite.agent.ui

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowForward
import androidx.compose.material.icons.outlined.ArrowUpward
import androidx.compose.material.icons.outlined.AutoAwesome
import androidx.compose.material.icons.outlined.Mic
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.agent.R
import app.kite.agent.data.SessionSummary
import app.kite.agent.ui.theme.Chrome
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper
import app.kite.agent.ui.theme.Scanner

@Composable
fun HomeScreen(
    sessions: List<SessionSummary>,
    modelName: String,
    configured: Boolean,
    onOpen: (String) -> Unit,
    onSubmit: (String) -> Unit,
    onSettings: () -> Unit,
    onDelete: (String) -> Unit,
    onRename: (String, String) -> Unit,
) {
    var text by rememberSaveable { mutableStateOf("") }
    var pendingDelete by remember { mutableStateOf<SessionSummary?>(null) }
    var renameTarget by remember { mutableStateOf<SessionSummary?>(null) }
    var renameText by remember { mutableStateOf("") }
    var query by rememberSaveable { mutableStateOf("") }
    val filtered = remember(sessions, query) {
        if (query.isBlank()) sessions else sessions.filter {
            it.title.contains(query, true) || it.preview.contains(query, true)
        }
    }
    val density = LocalDensity.current
    val imeVisible = WindowInsets.ime.getBottom(density) > 0
    val context = LocalContext.current
    val voiceLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                text = listOf(text.trim(), spoken.trim()).filter { it.isNotBlank() }.joinToString(" ")
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

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Ink),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.radialGradient(
                        colors = listOf(Ember.copy(alpha = 0.13f), Color.Transparent),
                        center = Offset(980f, 60f),
                        radius = 1200f,
                    ),
                ),
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 22.dp, end = 12.dp, top = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScannerMark(modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = "KITE",
                        color = Paper,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Black,
                        letterSpacing = 4.sp,
                    )
                    Text(
                        text = "MOBILE AGENT",
                        color = Muted,
                        fontSize = 7.sp,
                        letterSpacing = 1.8.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onSettings) {
                    Icon(Icons.Outlined.Settings, contentDescription = "设置", tint = Muted)
                }
            }

            Column(modifier = Modifier.padding(horizontal = 22.dp)) {
                if (!imeVisible) {
                    Spacer(Modifier.height(18.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(6.dp)
                                .background(Scanner, CircleShape),
                        )
                        Spacer(Modifier.width(7.dp))
                        Text(
                            text = "AGENT ONLINE",
                            color = Scanner,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.Bold,
                            letterSpacing = 2.2.sp,
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = "把想法",
                        style = MaterialTheme.typography.displayLarge,
                        color = Paper,
                    )
                    Row {
                        Text(
                            text = "交给它",
                            style = MaterialTheme.typography.displayLarge,
                            color = Paper,
                        )
                        Text(
                            text = "。",
                            style = MaterialTheme.typography.displayLarge,
                            color = Scanner,
                        )
                    }
                    Spacer(Modifier.height(14.dp))
                    Text(
                        text = "读文件、查网页、跑命令。\nKite 会把事情做完再回来。",
                        style = MaterialTheme.typography.bodyMedium,
                        color = Muted,
                        lineHeight = 22.sp,
                    )
                    Spacer(Modifier.height(14.dp))
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        QuickChip("写代码") { text = "帮我写一个能直接运行的网页小工具" }
                        QuickChip("查资料") { text = "搜索一下今天值得关注的 AI 新闻" }
                        QuickChip("整理文件") { text = "看看工作区里有什么，整理成一份清单" }
                    }
                    if (!configured) {
                        Spacer(Modifier.height(14.dp))
                    Surface(
                        color = Ember.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, Ember.copy(alpha = 0.35f)),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable(onClick = onSettings),
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = "先填一个 API Key，Kite 才能开始工作",
                                color = Ember,
                                fontSize = 12.sp,
                                modifier = Modifier.weight(1f),
                            )
                            Icon(
                                Icons.AutoMirrored.Outlined.ArrowForward,
                                contentDescription = null,
                                tint = Ember,
                                modifier = Modifier.size(16.dp),
                            )
                        }
                    }
                }
                Spacer(Modifier.height(26.dp))
                } else {
                    Spacer(Modifier.height(10.dp))
                }
                ComposerCard(
                    value = text,
                    modelName = modelName,
                    onValueChange = { text = it },
                    onSend = {
                        if (text.isNotBlank()) {
                            onSubmit(text)
                            text = ""
                        }
                    },
                    onVoice = startVoice,
                )
                if (!imeVisible) {
                    Spacer(Modifier.height(28.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "最近",
                            color = Muted,
                            fontSize = 11.sp,
                            letterSpacing = 2.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.weight(1f))
                        Text(
                            text = if (sessions.isEmpty()) "" else sessions.size.toString(),
                            color = Muted,
                            fontSize = 11.sp,
                        )
                    }
                    Spacer(Modifier.height(4.dp))
                    Surface(
                        color = Panel,
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, Line),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 22.dp, vertical = 4.dp),
                    ) {
                        BasicTextField(
                            value = query,
                            onValueChange = { query = it },
                            textStyle = MaterialTheme.typography.bodySmall.copy(color = Paper),
                            cursorBrush = SolidColor(Ember),
                            singleLine = true,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            decorationBox = { inner ->
                                Box {
                                    if (query.isEmpty()) {
                                        Text(
                                            text = "搜索任务…",
                                            color = Muted.copy(alpha = 0.7f),
                                            fontSize = 12.sp,
                                        )
                                    }
                                    inner()
                                }
                            },
                        )
                    }
                }
            }

            if (!imeVisible) {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(horizontal = 22.dp, vertical = 4.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(filtered, key = { it.id }) { session ->
                        SessionRow(
                            session = session,
                            onClick = { onOpen(session.id) },
                            onLongClick = {
                                renameTarget = session
                                renameText = session.title
                            },
                        )
                    }
                    if (filtered.isEmpty()) {
                        item {
                            Text(
                                text = if (sessions.isEmpty()) "还没有任务。写下第一件事，Kite 会从这里开始。"
                                else "没有匹配的任务",
                                color = Muted.copy(alpha = 0.75f),
                                fontSize = 13.sp,
                                lineHeight = 21.sp,
                                modifier = Modifier.padding(top = 12.dp, bottom = 24.dp),
                            )
                        }
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
        }
    }

    pendingDelete?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            containerColor = Panel,
            title = { Text("删除任务？", color = Paper) },
            text = {
                Text(
                    text = "只删除手机上的会话记录，工作区文件不受影响。",
                    color = Muted,
                    fontSize = 13.sp,
                    lineHeight = 20.sp,
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    pendingDelete = null
                }) {
                    Text("删除", color = Ember)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) {
                    Text("取消", color = Muted)
                }
            },
        )
    }

    renameTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { renameTarget = null },
            containerColor = Panel,
            title = { Text("任务", color = Paper) },
            text = {
                OutlinedTextField(
                    value = renameText,
                    onValueChange = { renameText = it },
                    label = { Text("重命名") },
                    singleLine = true,
                    colors = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
                        focusedTextColor = Paper,
                        unfocusedTextColor = Paper,
                        focusedContainerColor = Panel2,
                        unfocusedContainerColor = Panel2,
                        cursorColor = Ember,
                        focusedBorderColor = Ember,
                        unfocusedBorderColor = Line,
                    ),
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    onRename(target.id, renameText)
                    renameTarget = null
                }) { Text("保存", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDelete(target.id)
                    renameTarget = null
                }) { Text("删除", color = Muted) }
            },
        )
    }
}

@Composable
private fun ComposerCard(
    value: String,
    modelName: String,
    onValueChange: (String) -> Unit,
    onSend: () -> Unit,
    onVoice: () -> Unit,
) {
    Surface(
        shape = RoundedCornerShape(26.dp),
        color = Panel,
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column(modifier = Modifier.padding(start = 16.dp, end = 12.dp, top = 14.dp, bottom = 12.dp)) {
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = Paper),
                cursorBrush = SolidColor(Ember),
                maxLines = 5,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(58.dp),
                decorationBox = { inner ->
                    Box {
                        if (value.isEmpty()) {
                            Text(
                                text = "描述你想做的事，或贴一段代码…",
                                color = Muted.copy(alpha = 0.8f),
                                style = MaterialTheme.typography.bodyLarge,
                            )
                        }
                        inner()
                    }
                },
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    shape = RoundedCornerShape(50),
                    color = Panel2,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.AutoAwesome,
                            contentDescription = null,
                            tint = Ember,
                            modifier = Modifier.size(13.dp),
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = modelName.substringAfterLast('/').take(28),
                            color = Paper.copy(alpha = 0.85f),
                            fontSize = 11.sp,
                            fontFamily = FontFamily.Monospace,
                        )
                    }
                }
                Spacer(Modifier.weight(1f))
                IconButton(onClick = onVoice, modifier = Modifier.size(40.dp)) {
                    Icon(
                        Icons.Outlined.Mic,
                        contentDescription = "语音输入",
                        tint = Muted,
                        modifier = Modifier.size(19.dp),
                    )
                }
                Spacer(Modifier.width(2.dp))
                FilledIconButton(
                    onClick = onSend,
                    enabled = value.isNotBlank(),
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SessionRow(session: SessionSummary, onClick: () -> Unit, onLongClick: () -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = session.title,
                        color = Paper,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f),
                    )
                    Spacer(Modifier.width(10.dp))
                    Text(
                        text = timeAgo(session.updatedAt),
                        color = Muted,
                        fontSize = 10.5.sp,
                    )
                }
                if (session.preview.isNotBlank()) {
                    Spacer(Modifier.height(3.dp))
                    Text(
                        text = session.preview,
                        color = Muted.copy(alpha = 0.8f),
                        fontSize = 11.5.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(8.dp))
            Icon(
                Icons.AutoMirrored.Outlined.ArrowForward,
                contentDescription = null,
                tint = Muted.copy(alpha = 0.7f),
                modifier = Modifier.size(15.dp),
            )
        }
    }
}

@Composable
private fun QuickChip(label: String, onClick: () -> Unit) {
    Surface(
        color = Panel,
        shape = RoundedCornerShape(50),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.clickable(onClick = onClick),
    ) {
        Text(
            text = label,
            color = Chrome,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
        )
    }
}
