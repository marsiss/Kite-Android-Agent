package app.kite.agent.ui

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

fun timeAgo(millis: Long): String {
    val now = System.currentTimeMillis()
    val diff = (now - millis).coerceAtLeast(0)
    val minutes = diff / 60_000
    if (minutes < 1) return "刚刚"
    if (minutes < 60) return "${minutes} 分钟前"
    val hours = minutes / 60
    if (hours < 24) return "${hours} 小时前"
    val calendar = Calendar.getInstance().apply { timeInMillis = millis }
    val today = Calendar.getInstance()
    val yesterday = Calendar.getInstance().apply { add(Calendar.DAY_OF_YEAR, -1) }
    if (calendar.get(Calendar.YEAR) == yesterday.get(Calendar.YEAR) &&
        calendar.get(Calendar.DAY_OF_YEAR) == yesterday.get(Calendar.DAY_OF_YEAR)
    ) {
        return "昨天"
    }
    if (calendar.get(Calendar.YEAR) == today.get(Calendar.YEAR)) {
        return SimpleDateFormat("M月d日", Locale.CHINA).format(Date(millis))
    }
    return SimpleDateFormat("yyyy年M月d日", Locale.CHINA).format(Date(millis))
}
