package app.kite.agent.data

import android.content.Context
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

class McpStore(private val context: Context) {

    private val prefs = context.getSharedPreferences("kite_mcp", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun list(): List<McpServerConfig> {
        val raw = prefs.getString("servers", "") ?: ""
        if (raw.isBlank()) return emptyList()
        return runCatching {
            val json = Crypto.decrypt(raw)
            if (json.isBlank()) return emptyList()
            gson.fromJson<List<McpServerConfig>>(
                json,
                object : TypeToken<List<McpServerConfig>>() {}.type,
            ) ?: emptyList()
        }.getOrDefault(emptyList())
    }

    fun save(servers: List<McpServerConfig>) {
        val json = gson.toJson(servers)
        prefs.edit().putString("servers", Crypto.encrypt(json)).apply()
    }
}
