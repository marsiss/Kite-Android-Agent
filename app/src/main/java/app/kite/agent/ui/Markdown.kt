package app.kite.agent.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.kite.agent.ui.theme.Ember
import app.kite.agent.ui.theme.Line
import app.kite.agent.ui.theme.Mint
import app.kite.agent.ui.theme.Muted
import app.kite.agent.ui.theme.Panel2
import app.kite.agent.ui.theme.Paper

private data class Block(val code: Boolean, val lang: String, val text: String)

private fun parseBlocks(source: String): List<Block> {
    val blocks = mutableListOf<Block>()
    val prose = StringBuilder()
    var code: StringBuilder? = null
    var lang = ""

    fun flushProse() {
        val text = prose.toString().trimEnd()
        if (text.isNotBlank()) blocks += Block(false, "", text)
        prose.clear()
    }

    source.lines().forEach { line ->
        val trimmed = line.trimStart()
        if (trimmed.startsWith("```")) {
            if (code == null) {
                flushProse()
                code = StringBuilder()
                lang = trimmed.removePrefix("```").trim()
            } else {
                blocks += Block(true, lang, code.toString().trimEnd())
                code = null
                lang = ""
            }
        } else {
            if (code != null) code.appendLine(line) else prose.appendLine(line)
        }
    }
    if (code != null) {
        blocks += Block(true, lang, code.toString().trimEnd())
    } else {
        flushProse()
    }
    return blocks
}

@Composable
fun MarkdownText(text: String, modifier: Modifier = Modifier) {
    val blocks = remember(text) { parseBlocks(text) }
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(8.dp)) {
        blocks.forEach { block ->
            if (block.code) {
                CodeBlock(block.lang, block.text)
            } else {
                ProseBlock(block.text)
            }
        }
    }
}

@Composable
private fun ProseBlock(text: String) {
    SelectionContainer {
        Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
            text.lines().forEach { raw ->
                val line = raw.trimEnd()
                when {
                    line.isBlank() -> Spacer(Modifier.height(4.dp))
                    line.startsWith("### ") -> Paragraph(line.removePrefix("### "), 15.sp, true)
                    line.startsWith("## ") -> Paragraph(line.removePrefix("## "), 17.sp, true)
                    line.startsWith("# ") -> Paragraph(line.removePrefix("# "), 19.sp, true)
                    line.startsWith("- ") || line.startsWith("* ") -> Bullet(line.drop(2))
                    Regex("^\\d+\\.\\s+.*").matches(line) -> Bullet(line, numbered = true)
                    else -> Text(
                        text = inline(line),
                        color = Paper.copy(alpha = 0.94f),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                }
            }
        }
    }
}

@Composable
private fun Paragraph(text: String, size: androidx.compose.ui.unit.TextUnit, bold: Boolean) {
    Text(
        text = inline(text),
        color = Paper,
        fontSize = size,
        fontWeight = if (bold) FontWeight.Bold else FontWeight.Normal,
        lineHeight = size * 1.35f,
        modifier = Modifier.padding(top = 4.dp),
    )
}

@Composable
private fun Bullet(text: String, numbered: Boolean = false) {
    Row(modifier = Modifier.padding(start = 2.dp)) {
        Text(
            text = if (numbered) text.substringBefore(' ') else "•",
            color = Ember,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.width(18.dp),
        )
        Text(
            text = inline(if (numbered) text.substringAfter(' ') else text),
            color = Paper.copy(alpha = 0.94f),
            style = MaterialTheme.typography.bodyLarge,
        )
    }
}

private fun inline(source: String): AnnotatedString {
    val pattern = Regex("(\\*\\*[^*]+\\*\\*|`[^`]+`|\\[[^]]+]\\([^)]+\\))")
    return buildAnnotatedString {
        var cursor = 0
        pattern.findAll(source).forEach { match ->
            if (match.range.first > cursor) append(source.substring(cursor, match.range.first))
            val token = match.value
            when {
                token.startsWith("**") -> withStyle(SpanStyle(fontWeight = FontWeight.Bold)) {
                    append(token.removeSurrounding("**"))
                }

                token.startsWith("`") -> withStyle(
                    SpanStyle(fontFamily = FontFamily.Monospace, background = Panel2, color = Ember),
                ) {
                    append(" " + token.removeSurrounding("`") + " ")
                }

                else -> {
                    val label = token.substringAfter('[').substringBefore(']')
                    withStyle(SpanStyle(color = Ember, fontWeight = FontWeight.SemiBold)) {
                        append(label)
                    }
                }
            }
            cursor = match.range.last + 1
        }
        if (cursor < source.length) append(source.substring(cursor))
    }
}

@Composable
private fun CodeBlock(lang: String, code: String) {
    val clipboard = LocalClipboardManager.current
    Surface(
        color = Color(0xFF07090C),
        shape = RoundedCornerShape(14.dp),
        border = BorderStroke(1.dp, Line),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = lang.ifBlank { "code" },
                    color = Mint.copy(alpha = 0.75f),
                    fontSize = 10.sp,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 0.6.sp,
                )
                Spacer(Modifier.weight(1f))
                IconButton(
                    onClick = { clipboard.setText(AnnotatedString(code)) },
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        Icons.Outlined.ContentCopy,
                        contentDescription = "复制代码",
                        tint = Muted,
                        modifier = Modifier.size(14.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 12.dp, end = 14.dp, bottom = 12.dp, top = 2.dp),
            ) {
                Text(
                    text = code,
                    color = Paper.copy(alpha = 0.9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.5.sp,
                    lineHeight = 17.sp,
                )
            }
        }
    }
}
