package app.kite.agent.access

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.Display
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import java.io.File
import java.io.FileOutputStream
import java.util.ArrayDeque
import kotlin.coroutines.resume
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext

class KiteAccessibilityService : AccessibilityService() {

    override fun onServiceConnected() {
        super.onServiceConnected()
        instance = this
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) = Unit

    override fun onInterrupt() = Unit

    override fun onDestroy() {
        if (instance === this) instance = null
        super.onDestroy()
    }

    companion object {
        @Volatile
        var instance: KiteAccessibilityService? = null
            private set

        fun connected(): Boolean = instance != null
    }
}

/** Screen-level automation through the user-enabled accessibility service. */
object A11y {

    fun connected(): Boolean = KiteAccessibilityService.connected()

    suspend fun readScreen(): String = withContext(Dispatchers.Main) {
        val service = service()
        val root = service.rootInActiveWindow
            ?: throw IllegalStateException("当前没有活动窗口")
        val builder = StringBuilder()
        builder.appendLine("包名：${root.packageName ?: "?"}")
        builder.appendLine("窗口：${root.className ?: "?"}")
        val nodes = walk(root)
        nodes.take(220).forEachIndexed { index, node ->
            val label = label(node)
            if (label.isBlank()) return@forEachIndexed
            val rect = Rect().also { node.getBoundsInScreen(it) }
            val flags = buildList {
                if (node.isClickable) add("可点")
                if (node.isEditable) add("输入")
                if (node.isScrollable) add("滚动")
            }.joinToString(",")
            builder.append("[$index] ")
            if (flags.isNotBlank()) builder.append("($flags) ")
            builder.append(label.take(180))
            builder.append(" @(${rect.left},${rect.top},${rect.right},${rect.bottom})")
            builder.appendLine()
        }
        builder.toString().take(7_000)
    }

    suspend fun tap(query: String, index: Int = 0): String = withContext(Dispatchers.Main) {
        val service = service()
        val nodes = walk(service.rootInActiveWindow ?: throw IllegalStateException("没有活动窗口"))
        val matches = nodes.filter { node ->
            val label = label(node)
            label.isNotBlank() && (label.equals(query, true) || label.contains(query, true))
        }
        val node = matches.getOrNull(index)
            ?: throw IllegalStateException("没有找到「$query」")
        val clicked = node.performAction(AccessibilityNodeInfo.ACTION_CLICK)
        if (!clicked) {
            val rect = Rect().also { node.getBoundsInScreen(it) }
            tapAtInternal(service, rect.centerX().toFloat(), rect.centerY().toFloat())
        }
        delay(450)
        readScreen()
    }

    suspend fun tapAt(x: Float, y: Float): String = withContext(Dispatchers.Main) {
        val service = service()
        tapAtInternal(service, x, y)
        delay(350)
        readScreen()
    }

    suspend fun type(text: String): String = withContext(Dispatchers.Main) {
        val service = service()
        val root = service.rootInActiveWindow ?: throw IllegalStateException("没有活动窗口")
        val nodes = walk(root)
        val field = nodes.firstOrNull { it.isFocused && it.isEditable }
            ?: nodes.firstOrNull { it.isEditable }
            ?: throw IllegalStateException("没有找到输入框")
        field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
        val args = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, text)
        }
        val ok = field.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, args)
        if (!ok) {
            val clipboard = service.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
            clipboard.setPrimaryClip(ClipData.newPlainText("kite", text))
            field.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
            field.performAction(AccessibilityNodeInfo.ACTION_PASTE)
        }
        delay(350)
        readScreen()
    }

    suspend fun scroll(forward: Boolean): String = withContext(Dispatchers.Main) {
        val service = service()
        val root = service.rootInActiveWindow ?: throw IllegalStateException("没有活动窗口")
        val scrollable = walk(root).firstOrNull { it.isScrollable }
        val action = if (forward) {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        }
        val ok = scrollable?.performAction(action) ?: false
        if (!ok) {
            val metrics = service.resources.displayMetrics
            val x = metrics.widthPixels / 2f
            val from = metrics.heightPixels * if (forward) 0.72f else 0.28f
            val to = metrics.heightPixels * if (forward) 0.28f else 0.72f
            swipe(service, x, from, x, to)
        }
        delay(450)
        readScreen()
    }

    suspend fun back() = global(AccessibilityService.GLOBAL_ACTION_BACK)

    suspend fun home() = global(AccessibilityService.GLOBAL_ACTION_HOME)

    suspend fun recents() = global(AccessibilityService.GLOBAL_ACTION_RECENTS)

    suspend fun screenshot(target: File): String = withContext(Dispatchers.Main) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            throw IllegalStateException("系统低于 Android 11，请用系统截图")
        }
        val service = service()
        val shot = suspendCancellableCoroutine<AccessibilityService.ScreenshotResult?> { continuation ->
            val callback = object : AccessibilityService.TakeScreenshotCallback {
                override fun onSuccess(result: AccessibilityService.ScreenshotResult) {
                    if (continuation.isActive) continuation.resume(result)
                }

                override fun onFailure(errorCode: Int) {
                    if (continuation.isActive) continuation.resume(null)
                }
            }
            service.takeScreenshot(Display.DEFAULT_DISPLAY, service.mainExecutor, callback)
        } ?: throw IllegalStateException("截图失败，可能被系统限制")
        val buffer = shot.hardwareBuffer ?: throw IllegalStateException("截图没有返回像素")
        val bitmap = runCatching {
            Bitmap.wrapHardwareBuffer(buffer, shot.colorSpace)?.copy(Bitmap.Config.ARGB_8888, false)
        }.getOrNull()
        buffer.close()
        if (bitmap == null) throw IllegalStateException("截图转 Bitmap 失败")
        target.parentFile?.mkdirs()
        FileOutputStream(target).use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        bitmap.recycle()
        "截图已保存：${target.absolutePath}"
    }

    private suspend fun global(action: Int): String = withContext(Dispatchers.Main) {
        val service = service()
        val ok = service.performGlobalAction(action)
        if (!ok) throw IllegalStateException("系统操作失败")
        delay(400)
        "已执行系统操作"
    }

    private fun service(): KiteAccessibilityService =
        KiteAccessibilityService.instance
            ?: throw IllegalStateException("无障碍服务未开启，请到 设置 → 全部权限 → 无障碍自动化 开启")

    private fun tapAtInternal(service: AccessibilityService, x: Float, y: Float) {
        val path = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 60))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun swipe(service: AccessibilityService, x1: Float, y1: Float, x2: Float, y2: Float) {
        val path = Path().apply {
            moveTo(x1, y1)
            lineTo(x2, y2)
        }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(path, 0, 260))
            .build()
        service.dispatchGesture(gesture, null, null)
    }

    private fun walk(root: AccessibilityNodeInfo): List<AccessibilityNodeInfo> {
        val result = ArrayList<AccessibilityNodeInfo>(160)
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(root)
        var guard = 0
        while (queue.isNotEmpty() && result.size < 260 && guard++ < 700) {
            val node = queue.removeFirst()
            result.add(node)
            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { queue.add(it) }
            }
        }
        return result
    }

    private fun label(node: AccessibilityNodeInfo): String {
        val values = listOfNotNull(
            node.text?.toString(),
            node.contentDescription?.toString(),
            node.hintText?.toString(),
        ).map { it.trim() }.filter { it.isNotBlank() && it != "null" }
        return values.distinct().joinToString(" / ")
    }
}
