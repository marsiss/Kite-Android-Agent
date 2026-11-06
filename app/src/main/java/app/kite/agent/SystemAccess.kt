package app.kite.agent

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.provider.Settings
import rikka.shizuku.Shizuku
import java.io.File

/**
 * System-level entry points that are only useful on the personal build.
 *
 * - Root: requires an actual su binary (the phone must already be rooted).
 * - Shizuku: runs commands as the ADB shell UID (2000), not as root, but can use
 *   pm / am / settings / cmd and other system commands.
 */
object SystemAccess {

    private val suCandidates = listOf(
        "/system/bin/su",
        "/system/xbin/su",
        "/sbin/su",
        "/system/sd/xbin/su",
    )

    fun suPath(): String? = suCandidates.firstOrNull { File(it).exists() }

    fun isRootAvailable(): Boolean = suPath() != null

    fun isShizukuRunning(): Boolean = runCatching { Shizuku.pingBinder() }.getOrDefault(false)

    fun isShizukuGranted(): Boolean = runCatching {
        isShizukuRunning() && Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED
    }.getOrDefault(false)

    fun requestShizukuPermission() {
        runCatching { Shizuku.requestPermission(0) }
    }

    fun shizukuVersion(): Int = runCatching { Shizuku.getVersion() }.getOrDefault(-1)

    /** Create a process that runs as the ADB shell UID through Shizuku. */
    fun newShizukuProcess(command: String, env: Array<String>, dir: String): Process {
        val method = Shizuku::class.java.getDeclaredMethod(
            "newProcess",
            Array<String>::class.java,
            Array<String>::class.java,
            String::class.java,
        )
        method.isAccessible = true
        return method.invoke(null, arrayOf("sh", "-c", command), env, dir) as Process
    }

    /** Diagnostic used by the Shizuku card: run one command and return id/output. */
    fun runShizukuCommand(command: String): String {
        if (!isShizukuRunning()) return "Shizuku 服务未运行"
        if (!isShizukuGranted()) return "Kite 还没有被授权"
        return runCatching {
            val process = newShizukuProcess(
                command,
                arrayOf("PATH=/system/bin:/system/xbin:/vendor/bin", "HOME=/"),
                "/",
            )
            val out = StringBuilder()
            val reader = Thread {
                runCatching {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { out.appendLine(it) }
                    }
                }
            }
            reader.start()
            val waiter = Thread { runCatching { process.waitFor() } }
            waiter.start()
            waiter.join(10_000)
            val done = !waiter.isAlive
            if (!done) runCatching { process.destroyForcibly() }
            reader.join(500)
            val suffix = if (done) "" else "\n[超时]"
            "UID 版本 v${shizukuVersion()}\n${out.toString().trim().ifBlank { "(无输出)" }}$suffix"
        }.getOrElse {
            "执行失败：${it.message ?: it.javaClass.simpleName}"
        }
    }

    fun isAccessibilityEnabled(context: Context): Boolean {
        val enabled = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES,
        ).orEmpty()
        return enabled.split(':').any {
            it.contains(context.packageName) && it.contains("KiteAccessibilityService")
        }
    }

    fun openAccessibilitySettings(context: Context) {
        runCatching {
            context.startActivity(
                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
        }
    }
}
