package app.kite.agent.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import java.io.IOException

/**
 * User-granted local folder through the Storage Access Framework.
 * This is the Play-compatible path: no MANAGE_EXTERNAL_STORAGE, no root.
 */
object LocalFiles {

    fun tree(context: Context): DocumentFile? {
        val raw = Settings(context).localTree()
        if (raw.isBlank()) return null
        return runCatching { DocumentFile.fromTreeUri(context, Uri.parse(raw)) }.getOrNull()
    }

    fun list(context: Context, path: String): String {
        val dir = resolve(context, path, createDirs = false)
            ?: throw IOException("目录不存在：$path")
        if (!dir.isDirectory) throw IOException("不是目录：$path")
        val children = dir.listFiles().sortedWith(compareBy({ !it.isDirectory }, { it.name.orEmpty() }))
        if (children.isEmpty()) return "(空目录)"
        return children.joinToString("\n") { file ->
            val kind = if (file.isDirectory) "dir " else "file"
            val size = if (file.isDirectory) "" else " ${file.length()}"
            "$kind ${file.name.orEmpty()}$size"
        }
    }

    fun read(context: Context, path: String): String {
        val file = resolve(context, path, createDirs = false)
            ?: throw IOException("文件不存在：$path")
        if (file.isDirectory) throw IOException("这是目录：$path")
        val text = context.contentResolver.openInputStream(file.uri)?.bufferedReader()?.use { it.readText() }
            ?: throw IOException("打不开文件")
        return text.take(200_000)
    }

    fun write(context: Context, path: String, content: String): String {
        val (parent, name) = parentAndName(path)
        val dir = resolve(context, parent, createDirs = true)
            ?: throw IOException("无法创建目录：$parent")
        val existing = dir.findFile(name)
        val file = existing ?: dir.createFile(mimeFor(name), name)
            ?: throw IOException("无法创建文件：$name")
        context.contentResolver.openOutputStream(file.uri, "wt")?.use { it.write(content.toByteArray()) }
            ?: throw IOException("无法写入：$name")
        return "已写入 ${file.uri}"
    }

    fun mkdir(context: Context, path: String): String {
        val dir = resolve(context, path, createDirs = true)
            ?: throw IOException("无法创建目录：$path")
        return "已创建目录 ${dir.uri}"
    }

    fun delete(context: Context, path: String): String {
        val file = resolve(context, path, createDirs = false)
            ?: throw IOException("不存在：$path")
        if (!file.delete()) throw IOException("删除失败：$path")
        return "已删除 $path"
    }

    private fun resolve(context: Context, path: String, createDirs: Boolean): DocumentFile? {
        var current = tree(context) ?: throw IOException("还没有选择本地文件夹。设置 → 本地文件夹 → 选择")
        val parts = path.split('/').filter { it.isNotBlank() && it != "." }
        for (part in parts) {
            if (part == "..") throw IOException("不支持 ..")
            val next = current.findFile(part)
            current = when {
                next != null -> next
                createDirs -> current.createDirectory(part)
                    ?: throw IOException("无法创建目录：$part")
                else -> return null
            }
        }
        return current
    }

    private fun parentAndName(path: String): Pair<String, String> {
        val clean = path.trim().trim('/')
        if (clean.isBlank()) throw IOException("路径为空")
        val index = clean.lastIndexOf('/')
        return if (index < 0) "" to clean else clean.substring(0, index) to clean.substring(index + 1)
    }

    private fun mimeFor(name: String): String = when {
        name.endsWith(".json") -> "application/json"
        name.endsWith(".md") || name.endsWith(".txt") || name.endsWith(".log") -> "text/plain"
        name.endsWith(".html") -> "text/html"
        name.endsWith(".csv") -> "text/csv"
        name.endsWith(".png") -> "image/png"
        name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
        else -> "application/octet-stream"
    }
}
