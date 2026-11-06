package app.kite.agent.data

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import android.os.Environment
import java.io.File

class Settings(private val context: Context) {

    private val prefs = context.getSharedPreferences("kite_settings", Context.MODE_PRIVATE)

    fun provider(): ProviderConfig {
        return ProviderConfig(
            baseUrl = prefs.getString("base_url", "https://api.deepseek.com/v1")!!,
            model = prefs.getString("model", "deepseek-chat")!!,
            apiKey = Crypto.decrypt(prefs.getString("api_key", "")!!),
            temperature = prefs.getFloat("temperature", 0.2f).toDouble(),
            maxSteps = prefs.getInt("max_steps", 12),
            reasoningEffort = prefs.getString("reasoning_effort", "off") ?: "off",
        )
    }

    fun saveProvider(config: ProviderConfig) {
        prefs.edit()
            .putString("base_url", config.baseUrl.trim())
            .putString("model", config.model.trim())
            .putString("api_key", Crypto.encrypt(config.apiKey.trim()))
            .putFloat("temperature", config.temperature.toFloat())
            .putInt("max_steps", config.maxSteps)
            .putString("reasoning_effort", config.reasoningEffort)
            .apply()
    }

    fun shellEnabled(): Boolean = prefs.getBoolean("shell_enabled", false)

    fun setShellEnabled(enabled: Boolean) {
        prefs.edit().putBoolean("shell_enabled", enabled).apply()
    }

    fun approvalMode(): Boolean = prefs.getBoolean("approval_mode", true)

    fun setApprovalMode(enabled: Boolean) {
        prefs.edit().putBoolean("approval_mode", enabled).apply()
    }

    /** normal | root | shizuku */
    fun shellMode(): String = prefs.getString("shell_mode", "normal") ?: "normal"

    fun setShellMode(mode: String) {
        prefs.edit().putString("shell_mode", mode).apply()
    }

    fun deviceStorage(): Boolean = prefs.getBoolean("device_storage", true)

    fun setDeviceStorage(enabled: Boolean) {
        prefs.edit().putBoolean("device_storage", enabled).apply()
    }

    fun allFilesGranted(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            context.checkSelfPermission(Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                PackageManager.PERMISSION_GRANTED
        }
    }

    fun useDeviceStorage(): Boolean = deviceStorage() && allFilesGranted()

    /**
     * App-private sandbox by default. On the personal build, once all-files access
     * is granted and the user enables it, the workspace becomes the shared-storage
     * root (/storage/emulated/0).
     */
    fun workspace(): String {
        val dir = if (useDeviceStorage()) {
            Environment.getExternalStorageDirectory()
        } else {
            File(context.filesDir, "workspace")
        }
        if (dir != Environment.getExternalStorageDirectory()) runCatching { dir.mkdirs() }
        return dir.absolutePath
    }

    fun allowExternalPaths(): Boolean = useDeviceStorage()

    /** Optional SAF tree chosen by the user; works without MANAGE_EXTERNAL_STORAGE. */
    fun localTree(): String = prefs.getString("local_tree", "") ?: ""

    fun setLocalTree(uri: String) {
        prefs.edit().putString("local_tree", uri).apply()
    }
}
