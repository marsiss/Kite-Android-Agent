package app.kite.agent.agent

import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.IOException
import java.util.concurrent.TimeUnit
import okhttp3.OkHttpClient
import okhttp3.Request

/** Reads /models from any OpenAI-compatible endpoint. */
object ModelCatalog {

    private val http: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(20, TimeUnit.SECONDS)
        .build()

    fun list(baseUrl: String, apiKey: String): List<String> {
        val base = baseUrl.trim()
        if (base.isEmpty()) throw IOException("接口地址为空")
        val endpoint = when {
            base.endsWith("/chat/completions") -> base.removeSuffix("/chat/completions") + "/models"
            base.endsWith("/models") -> base
            else -> base.trimEnd('/') + "/models"
        }
        val request = Request.Builder()
            .url(endpoint)
            .header("Accept", "application/json")
            .apply {
                if (apiKey.isNotBlank()) header("Authorization", "Bearer ${apiKey.trim()}")
            }
            .build()

        return http.newCall(request).execute().use { response ->
            val body = response.body?.string().orEmpty()
            if (!response.isSuccessful) {
                throw IOException("HTTP ${response.code}: ${body.take(500)}")
            }
            val root = runCatching { JsonParser.parseString(body) }.getOrElse {
                throw IOException("返回不是 JSON：${body.take(200)}")
            }
            val array: JsonArray = when {
                root.isJsonArray -> root.asJsonArray
                root.isJsonObject -> {
                    val obj = root.asJsonObject
                    obj.getAsJsonArray("data")
                        ?: obj.getAsJsonArray("models")
                        ?: throw IOException(
                            obj.get("error")?.toString()?.take(300) ?: "响应里没有 data/models",
                        )
                }
                else -> throw IOException("无法识别的响应")
            }
            array.mapNotNull { element ->
                when {
                    element.isJsonPrimitive -> element.asString
                    element.isJsonObject -> {
                        val obj = element.asJsonObject
                        obj.get("id")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("name")?.takeIf { !it.isJsonNull }?.asString
                            ?: obj.get("model")?.takeIf { !it.isJsonNull }?.asString
                    }
                    else -> null
                }
            }.filter { it.isNotBlank() }.distinct().sorted()
        }
    }
}
