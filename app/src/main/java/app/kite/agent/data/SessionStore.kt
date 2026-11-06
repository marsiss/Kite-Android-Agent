package app.kite.agent.data

import com.google.gson.Gson
import com.google.gson.GsonBuilder
import java.io.File

class SessionStore(context: android.content.Context) {

    private val gson: Gson = GsonBuilder().setPrettyPrinting().create()
    private val dir = File(context.filesDir, "sessions").apply { mkdirs() }

    @Synchronized
    fun list(): List<SessionSummary> {
        val sessions = dir.listFiles { file -> file.isFile && file.name.endsWith(".json") }
            ?.mapNotNull { file ->
                runCatching { gson.fromJson(file.readText(), Session::class.java) }.getOrNull()
            }
            ?: emptyList()
        return sessions
            .sortedByDescending { it.updatedAt }
            .map { session ->
                val last = session.messages.lastOrNull { it.role == Role.ASSISTANT || it.role == Role.USER }
                SessionSummary(
                    id = session.id,
                    title = session.title,
                    updatedAt = session.updatedAt,
                    preview = previewText(last?.content.orEmpty()),
                )
            }
    }

    private fun previewText(raw: String): String {
        var text = raw.replace(Regex("```[\\s\\S]*?```"), " [代码] ")
        text = text.replace(Regex("(?m)^\\s*[-*#]+\\s*"), " ")
        text = text.replace(Regex("[`*_>#]"), "")
        text = text.replace(Regex("\\s+"), " ").trim()
        return if (text.length > 72) text.take(72) + "…" else text
    }

    @Synchronized
    fun load(id: String): Session? {
        val file = File(dir, "$id.json")
        if (!file.exists()) return null
        return runCatching { gson.fromJson(file.readText(), Session::class.java) }.getOrNull()
    }

    @Synchronized
    fun save(session: Session) {
        val file = File(dir, "${session.id}.json")
        val tmp = File(dir, "${session.id}.tmp")
        tmp.writeText(gson.toJson(session))
        if (!tmp.renameTo(file)) {
            file.writeText(tmp.readText())
            tmp.delete()
        }
    }

    @Synchronized
    fun delete(id: String) {
        File(dir, "$id.json").delete()
    }

    @Synchronized
    fun rename(id: String, title: String): Session? {
        val session = load(id) ?: return null
        val updated = session.copy(title = title.trim().ifBlank { session.title }, updatedAt = System.currentTimeMillis())
        save(updated)
        return updated
    }

    fun newSession(): Session = Session()
}
