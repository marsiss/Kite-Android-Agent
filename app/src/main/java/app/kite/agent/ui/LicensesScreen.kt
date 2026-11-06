package app.kite.agent.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.agent.data.KITE_ATTRIBUTION_NOTE
import app.kite.agent.data.openSourceItems
import app.kite.agent.data.referenceProjects
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Ink
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel
import app.kite.agent.ui.theme.Paper

@Composable
fun LicensesScreen(onBack: () -> Unit) {
    val uri = LocalUriHandler.current
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
            Column {
                Text("开源许可与归属", color = Paper, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text("Open source licenses & attribution", color = Muted, fontSize = 9.sp)
            }
        }
        HorizontalDivider(color = Line, thickness = 0.7.dp)

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("Kite 的归属说明", color = Ember, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            KITE_ATTRIBUTION_NOTE,
                            color = Muted,
                            fontSize = 11.5.sp,
                            lineHeight = 18.sp,
                        )
                    }
                }
            }
            item {
                Text(
                    "第三方组件",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
            items(openSourceItems) { item ->
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                color = Paper,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(item.version, color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(item.license, color = Ember, fontSize = 10.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(item.purpose, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                        TextButton(
                            onClick = { runCatching { uri.openUri(item.url) } },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text("来源 / 许可证", color = Paper.copy(alpha = 0.8f), fontSize = 10.5.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = Muted,
                                modifier = Modifier.height(12.dp),
                            )
                        }
                    }
                }
            }
            item {
                Text(
                    "参考项目（未包含代码）",
                    color = Muted,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp,
                )
            }
            items(referenceProjects) { item ->
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                item.name,
                                color = Paper,
                                fontSize = 13.5.sp,
                                fontWeight = FontWeight.SemiBold,
                                modifier = Modifier.weight(1f),
                            )
                            Text(item.version, color = Muted, fontSize = 9.sp, fontFamily = FontFamily.Monospace)
                        }
                        Spacer(Modifier.height(3.dp))
                        Text(item.license, color = Ember, fontSize = 10.sp)
                        Spacer(Modifier.height(5.dp))
                        Text(item.purpose, color = Muted, fontSize = 11.sp, lineHeight = 17.sp)
                        TextButton(
                            onClick = { runCatching { uri.openUri(item.url) } },
                            contentPadding = androidx.compose.foundation.layout.PaddingValues(0.dp),
                        ) {
                            Text("来源", color = Paper.copy(alpha = 0.8f), fontSize = 10.5.sp)
                            Spacer(Modifier.width(4.dp))
                            Icon(
                                Icons.AutoMirrored.Outlined.OpenInNew,
                                contentDescription = null,
                                tint = Muted,
                                modifier = Modifier.height(12.dp),
                            )
                        }
                    }
                }
            }
            item {
                Surface(
                    color = Panel,
                    shape = RoundedCornerShape(18.dp),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Line),
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text("完整许可证文本", color = Paper, fontSize = 12.sp, fontWeight = FontWeight.SemiBold)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Apache License 2.0 与 MIT License 的完整文本可通过上方各项目的来源链接查看。",
                            color = Muted,
                            fontSize = 11.sp,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }
            item { Spacer(Modifier.height(20.dp)) }
        }
    }
}
