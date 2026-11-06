package app.kite.agent.data

import android.content.Context
import java.io.File

data class Skill(
    val name: String,
    val description: String,
    val body: String,
    val file: File,
)

/**
 * Kite-native SKILL.md loader. A skill is a folder under
 * <workspace>/.kite/skills/<name>/SKILL.md or <files>/skills/<name>/SKILL.md.
 *
 * The format is intentionally plain markdown + a tiny front matter:
 * ---
 * name: pdf
 * description: Fill and inspect PDFs
 * ---
 * steps...
 *
 * This is an independent implementation; it does not copy Mika/OpenCode code.
 */
class SkillStore(
    private val context: Context,
    private val workspace: File,
) {

    private val dirs = listOf(
        File(workspace, ".kite/skills"),
        File(context.filesDir, "skills"),
    )

    fun list(): List<Skill> {
        val seen = mutableSetOf<String>()
        return dirs.flatMap { dir ->
            dir.listFiles { file -> file.isDirectory }.orEmpty()
                .sortedBy { it.name }
                .mapNotNull { read(it) }
        }.filter { seen.add(it.name) }
    }

    fun get(name: String): Skill? = list().firstOrNull { it.name == name }

    fun delete(name: String): Boolean {
        val skill = get(name) ?: return false
        return skill.file.parentFile?.deleteRecursively() == true
    }

    fun save(name: String, description: String, body: String): Skill {
        val safe = name.trim().lowercase().replace(Regex("[^a-z0-9-]"), "-")
            .trim('-').ifBlank { "skill" }.take(48)
        val dir = File(dirs.first(), safe).apply { mkdirs() }
        val text = buildString {
            appendLine("---")
            appendLine("name: $safe")
            appendLine("description: ${description.replace("\n", " ").take(200)}")
            appendLine("---")
            appendLine()
            appendLine(body.trim())
        }
        File(dir, "SKILL.md").writeText(text)
        return Skill(safe, description, body.trim(), File(dir, "SKILL.md"))
    }

    private fun read(dir: File): Skill? {
        val file = File(dir, "SKILL.md")
        if (!file.isFile) return null
        val raw = runCatching { file.readText() }.getOrNull() ?: return null
        val lines = raw.replace("\r\n", "\n").split("\n")
        var name = dir.name
        var description = ""
        var bodyStart = 0
        if (lines.firstOrNull()?.trim() == "---") {
            val end = (1 until lines.size).firstOrNull { lines[it].trim() == "---" } ?: -1
            if (end > 0) {
                lines.subList(1, end).forEach { line ->
                    val colon = line.indexOf(':')
                    if (colon <= 0) return@forEach
                    val key = line.substring(0, colon).trim()
                    val value = line.substring(colon + 1).trim()
                    when (key) {
                        "name" -> if (value.isNotBlank()) name = value
                        "description" -> description = value
                    }
                }
                bodyStart = end + 1
            }
        }
        val body = lines.drop(bodyStart).joinToString("\n").trim()
        if (description.isBlank()) {
            description = body.lineSequence().firstOrNull { it.isNotBlank() }?.trim().orEmpty().take(160)
        }
        return Skill(name, description, body, file)
    }
}
