package app.kite.agent.agent

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import app.kite.agent.R
import app.kite.agent.data.Message
import app.kite.agent.data.Role
import app.kite.agent.data.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import java.io.File
import java.util.UUID
import java.util.concurrent.TimeUnit

data class ScheduledTask(
    val id: String = UUID.randomUUID().toString(),
    val prompt: String = "",
    val delayMinutes: Int = 10,
    val repeatMinutes: Int = 0,
    val createdAt: Long = System.currentTimeMillis(),
    val lastRun: Long = 0L,
)

class ScheduledStore(context: Context) {
    private val prefs = context.getSharedPreferences("kite_schedules", Context.MODE_PRIVATE)
    private val gson = Gson()

    fun list(): List<ScheduledTask> {
        val raw = prefs.getString("tasks", "[]") ?: "[]"
        return runCatching {
            gson.fromJson<List<ScheduledTask>>(raw, object : TypeToken<List<ScheduledTask>>() {}.type)
        }.getOrNull().orEmpty()
    }

    fun save(task: ScheduledTask) {
        val tasks = list().filterNot { it.id == task.id } + task
        prefs.edit().putString("tasks", gson.toJson(tasks)).apply()
    }

    fun update(task: ScheduledTask) {
        val tasks = list().map { if (it.id == task.id) task else it }
        prefs.edit().putString("tasks", gson.toJson(tasks)).apply()
    }

    fun remove(id: String) {
        val tasks = list().filterNot { it.id == id }
        prefs.edit().putString("tasks", gson.toJson(tasks)).apply()
    }
}

object ScheduleManager {

    fun list(context: Context): List<ScheduledTask> = ScheduledStore(context).list()

    fun schedule(context: Context, prompt: String, minutes: Int, repeatMinutes: Int = 0): ScheduledTask {
        val task = ScheduledTask(
            prompt = prompt.trim(),
            delayMinutes = minutes.coerceAtLeast(1),
            repeatMinutes = repeatMinutes.coerceAtLeast(0),
        )
        ScheduledStore(context).save(task)
        enqueue(context, task)
        return task
    }

    fun enqueue(context: Context, task: ScheduledTask) {
        val request = OneTimeWorkRequestBuilder<ScheduledAgentWorker>()
            .setInitialDelay(task.delayMinutes.coerceAtLeast(1).toLong(), TimeUnit.MINUTES)
            .setInputData(workDataOf("taskId" to task.id, "prompt" to task.prompt))
            .addTag("kite_task_${task.id}")
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }

    fun cancel(context: Context, id: String) {
        WorkManager.getInstance(context).cancelAllWorkByTag("kite_task_$id")
        ScheduledStore(context).remove(id)
    }
}

/**
 * Runs a full agent turn in the background and posts the final answer as a
 * notification. Intended for user-scheduled tasks, not always-on agents.
 */
class ScheduledAgentWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val taskId = inputData.getString("taskId").orEmpty()
        val prompt = inputData.getString("prompt")?.trim().orEmpty()
        if (prompt.isBlank()) return Result.failure()
        val settings = Settings(applicationContext)
        val config = settings.provider()
        if (config.apiKey.isBlank()) {
            notify("定时任务失败", "还没有配置 API Key")
            return Result.failure()
        }
        val engine = AgentEngine(
            context = applicationContext,
            config = config,
            workspace = File(settings.workspace()),
            shellEnabled = settings.shellEnabled(),
            allowExternal = settings.allowExternalPaths(),
            shellMode = settings.shellMode(),
            approval = false,
        )
        var finalText = ""
        var failure = ""
        runCatching {
            engine.run(listOf(Message(role = Role.USER, content = prompt))).collect { event ->
                when (event) {
                    is AgentEvent.AssistantMessage -> if (event.message.content.isNotBlank()) {
                        finalText = event.message.content
                    }
                    is AgentEvent.Failure -> failure = event.message
                    else -> Unit
                }
            }
        }.onFailure { failure = it.message.orEmpty() }

        val store = ScheduledStore(applicationContext)
        val task = store.list().firstOrNull { it.id == taskId }
        if (task != null) {
            store.update(task.copy(lastRun = System.currentTimeMillis()))
            if (task.repeatMinutes > 0) {
                ScheduleManager.enqueue(
                    applicationContext,
                    task.copy(delayMinutes = task.repeatMinutes, lastRun = System.currentTimeMillis()),
                )
            }
        }

        return if (finalText.isNotBlank()) {
            notify("Kite 定时任务完成", finalText)
            Result.success()
        } else {
            notify("Kite 定时任务失败", failure.ifBlank { "没有返回内容" })
            Result.failure()
        }
    }

    private fun notify(title: String, body: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= 26) {
            manager.createNotificationChannel(
                NotificationChannel("kite_scheduled", "Kite 定时任务", NotificationManager.IMPORTANCE_DEFAULT),
            )
        }
        val notification = NotificationCompat.Builder(applicationContext, "kite_scheduled")
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body.take(200))
            .setStyle(NotificationCompat.BigTextStyle().bigText(body.take(2000)))
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(System.currentTimeMillis().toInt(), notification) }
    }
}
