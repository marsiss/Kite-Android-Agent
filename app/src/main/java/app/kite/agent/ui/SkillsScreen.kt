package app.kite.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
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
import androidx.compose.material.icons.outlined.Description
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
import app.kite.agent.data.Skill
import app.kite.agent.data.SkillStore
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper
import java.io.File

@Composable
fun SkillsScreen(workspace: String, onBack: () -> Unit) {
    val context = LocalContext.current
    val store = remember(workspace) { SkillStore(context, File(workspace)) }
    var skills by remember { mutableStateOf(store.list()) }
    var viewing by remember { mutableStateOf<Skill?>(null) }
    var deleting by remember { mutableStateOf<Skill?>(null) }
    var adding by remember { mutableStateOf(false) }
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var body by remember { mutableStateOf("") }

    fun reload() {
        skills = store.list()
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
                Text("技能", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(
                    "${skills.size} 个 · .kite/skills",
                    color = Muted,
                    fontSize = 9.sp,
                    fontFamily = FontFamily.Monospace,
                )
            }
            IconButton(onClick = { adding = true }) {
                Icon(Icons.Outlined.Add, contentDescription = "新建技能", tint = Ember)
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
                    "Agent 会先读技能正文再执行。技能就是 Markdown，可以手动放，也可以在这里建。",
                    color = Muted,
                    fontSize = 11.sp,
                    lineHeight = 17.sp,
                )
            }
            items(skills, key = { it.name }) { skill ->
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                    modifier = Modifier.clickable { viewing = skill },
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(
                            Icons.Outlined.Description,
                            contentDescription = null,
                            tint = Ember,
                            modifier = Modifier.size(18.dp),
                        )
                        Spacer(Modifier.width(10.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                skill.name,
                                color = Paper,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                            Text(
                                skill.description.ifBlank { "（无说明）" },
                                color = Muted,
                                fontSize = 10.5.sp,
                                lineHeight = 15.sp,
                                maxLines = 2,
                            )
                        }
                        IconButton(onClick = { deleting = skill }) {
                            Icon(
                                Icons.Outlined.DeleteOutline,
                                contentDescription = "删除技能",
                                tint = Muted,
                                modifier = Modifier.size(18.dp),
                            )
                        }
                    }
                }
            }
            if (skills.isEmpty()) {
                item {
                    Text(
                        "还没有技能。点右上角 + 建一个，例如「PDF 处理」「周报整理」。",
                        color = Muted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(vertical = 24.dp),
                    )
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }

    viewing?.let { skill ->
        AlertDialog(
            onDismissRequest = { viewing = null },
            containerColor = Panel,
            title = { Text(skill.name, color = Paper) },
            text = {
                Column {
                    Text(skill.description, color = Ember, fontSize = 11.sp)
                    Spacer(Modifier.height(8.dp))
                    Text(
                        skill.body.take(6000),
                        color = Paper.copy(alpha = 0.85f),
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 17.sp,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        skill.file.absolutePath,
                        color = Muted,
                        fontSize = 9.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { viewing = null }) { Text("关闭", color = Ember) }
            },
        )
    }

    deleting?.let { skill ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = Panel,
            title = { Text("删除技能？", color = Paper) },
            text = { Text(skill.name, color = Muted, fontSize = 12.sp) },
            confirmButton = {
                TextButton(onClick = {
                    store.delete(skill.name)
                    deleting = null
                    reload()
                }) { Text("删除", color = Ember) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("取消", color = Muted) }
            },
        )
    }

    if (adding) {
        AlertDialog(
            onDismissRequest = { adding = false },
            containerColor = Panel,
            title = { Text("新建技能", color = Paper) },
            text = {
                Column(modifier = Modifier.imePadding()) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("名字（小写/连字符）") },
                        singleLine = true,
                        colors = kiteFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("什么时候用") },
                        singleLine = true,
                        colors = kiteFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Spacer(Modifier.height(8.dp))
                    OutlinedTextField(
                        value = body,
                        onValueChange = { body = it },
                        label = { Text("步骤 / 检查项 / 示例") },
                        minLines = 5,
                        maxLines = 8,
                        colors = kiteFieldColors(),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
            },
            confirmButton = {
                TextButton(
                    enabled = name.isNotBlank() && body.isNotBlank(),
                    onClick = {
                        store.save(name, description, body)
                        adding = false
                        name = ""
                        description = ""
                        body = ""
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
private fun kiteFieldColors() = androidx.compose.material3.OutlinedTextFieldDefaults.colors(
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
