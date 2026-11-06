package app.kite.agent.data

import android.content.Context
import java.io.File

/**
 * Keeps one backup of the last file operation. Deliberately simple:
 * one-level undo is enough for "agent broke my file" recovery.
 */
object UndoStore {

    private const val DIR = "undo"
    private const val META = "meta.txt"

    fun backup(context: Context, target: File): Boolean {
        if (!target.exists()) return true
        return runCatching {
            val dir = File(context.filesDir, DIR).apply { mkdirs() }
            dir.listFiles()?.forEach { it.deleteRecursively() }
            val copy = File(dir, target.name.ifBlank { "file" })
            if (target.isDirectory) {
                target.copyRecursively(copy, overwrite = true)
            } else {
                target.copyTo(copy, overwrite = true)
            }
            File(dir, META).writeText(target.absolutePath)
            true
        }.getOrDefault(false)
    }

    fun restoreLast(context: Context): String? {
        return runCatching {
            val dir = File(context.filesDir, DIR)
            val meta = File(dir, META)
            if (!meta.exists()) return null
            val target = File(meta.readText())
            val source = dir.listFiles()?.firstOrNull { it.name != META } ?: return null
            if (target.exists()) target.deleteRecursively()
            target.parentFile?.mkdirs()
            if (source.isDirectory) {
                source.copyRecursively(target, overwrite = true)
            } else {
                source.copyTo(target, overwrite = true)
            }
            dir.deleteRecursively()
            target.absolutePath
        }.getOrNull()
    }

    fun hasBackup(context: Context): Boolean =
        File(context.filesDir, DIR).let { it.exists() && it.listFiles()?.any { f -> f.name != META } == true }
}
