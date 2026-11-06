package app.kite.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.agent.agent.ScheduleManager
import app.kite.agent.agent.ScheduledTask
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun ScheduledScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    var tasks by remember { mutableStateOf(ScheduleManager.list(context)) }
    var adding by remember { mutableStateOf(false) }
    var prompt by remember { mutableStateOf("") }
    var minutes by remember { mutableStateOf("10") }
    var repeat by remember { mutableStateOf("0") }
    val time = remember { SimpleDateFormat("MM-dd HH:mm", Locale.getDefault()) }

    fun reload() {
        tasks = ScheduleManager.list(context)
    }

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
                Text("定时任务", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("${tasks.size} 个 · WorkManager", color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
            }
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建定时任务", tint = Ember)
            }
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp)

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "到点后 Kite 会在后台跑一个完整 Agent 任务，结果发通知。华为省电策略可能延迟执行。",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
            items(tasks, key = { it.id }) { task ->
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Outlined.Schedule,
                            contentDescription = null,
                            tint = Ember,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                task.prompt,
                                color = Paper,
                                fontSize = 12.5.sp,
                                lineHeight = 18.sp,
                                maxLines = 3,
                            )
                            Spacer(Modifier.height(4.dp))
                            Text(
                                buildString {
                                    if (task.repeatMinutes > 0) {
                                        append("每 ${task.repeatMinutes} 分钟")
                                    } else {
                                        append("一次")
                                    }
                                    if (task.lastRun > 0) append(" · 上次 ${time.format(Date(task.lastRun))}")
                                },
                                color = Muted,
                                fontSize = 9.5.sp,
                                fontFamily = FontFamily.Monospace,
                            )
                        }
                        IconButton(onClick = {
                            ScheduleManager.cancel(context, task.id)
                            reload()
                        }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "取消定时任务",
                                tint = Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            if (tasks.isEmpty()) {
                item {
                    Text(
                        "还没有定时任务。点右上角 + 建一个，或直接对 Agent 说“10 分钟后提醒我…”。",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Panel,
            title = { Text("新建定时任务", color = Paper) },
            text = {
                Column(modifier = Modifier.imePadding()) {
                    OutlinedTextField(
                        value = prompt,
                        onValueChange = { prompt = it },
                        label = { Text("到点要做什么") },
                        minLines = 2,
                        maxLines = 4,
                        colors = scheduleFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = minutes,
                        onValueChange = { minutes = it.filter(Char::isDigit) },
                        label = { Text("多少分钟后执行") },
                        singleLine = true,
                        colors = scheduleFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = repeat,
                        onValueChange = { repeat = it.filter(Char::isDigit) },
                        label = { Text("重复间隔分钟（0 = 一次）") },
                        singleLine = true,
                        colors = scheduleFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = prompt.isNotBlank() && (minutes.toIntOrNull() ?: 0) > 0,
                    onClick = {
                        ScheduleManager.schedule(
                            context = context,
                            prompt = prompt,
                            minutes = minutes.toIntOrNull() ?: 10,
                            repeatMinutes = repeat.toIntOrNull() ?: 0,
                        )
                        prompt = ""
                        minutes = "10"
                        repeat = "0"
                        adding = false
                        reload()
                    },
                ) { Text("保存", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = { adding = false }) { Text("取消", color = Muted) }
            },
        )
    }
}

@Composable
private fun scheduleFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
    focusedTextColor = Paper,
    unfocusedTextColor = Paper,
    focusedContainerColor = Panel2,
    unfocusedContainerColor = Panel2,
    cursorColor = Ember,
    focusedBorderColor = Ember,
    unfocusedBorderColor = Line,
    focusedLabelColor = Muted,
    unfocusedLabelColor = Muted,
)
