package com.shikeji.reminder.util

import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

object TimeFormat {

    private val weekNames = arrayOf("周日", "周一", "周二", "周三", "周四", "周五", "周六")

    private fun calendar(ms: Long) = Calendar.getInstance().apply { timeInMillis = ms }

    private fun sameDay(a: Long, b: Long): Boolean {
        val ca = calendar(a)
        val cb = calendar(b)
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
            ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }

    fun time(ms: Long): String =
        SimpleDateFormat("HH:mm", Locale.CHINA).format(Date(ms))

    /** 今天 / 明天 / 昨天 / 11月9日 周日 */
    fun day(ms: Long): String {
        val now = System.currentTimeMillis()
        val c = calendar(ms)
        val tomorrow = calendar(now).apply { add(Calendar.DAY_OF_YEAR, 1) }
        val yesterday = calendar(now).apply { add(Calendar.DAY_OF_YEAR, -1) }
        return when {
            sameDay(ms, now) -> "今天"
            sameDay(ms, tomorrow.timeInMillis) -> "明天"
            sameDay(ms, yesterday.timeInMillis) -> "昨天"
            else -> {
                val m = c.get(Calendar.MONTH) + 1
                val d = c.get(Calendar.DAY_OF_MONTH)
                val w = weekNames[c.get(Calendar.DAY_OF_WEEK) - 1]
                if (c.get(Calendar.YEAR) == calendar(now).get(Calendar.YEAR)) "${m}月${d}日 $w"
                else "${c.get(Calendar.YEAR)}年${m}月${d}日"
            }
        }
    }

    fun full(ms: Long): String {
        val c = calendar(ms)
        return "${c.get(Calendar.YEAR)}年${c.get(Calendar.MONTH) + 1}月${c.get(Calendar.DAY_OF_MONTH)}日 " +
            "${weekNames[c.get(Calendar.DAY_OF_WEEK) - 1]} ${time(ms)}"
    }

    /** 还有 2 小时 15 分 / 3 天后 / 已过去 */
    fun distance(ms: Long): String {
        val diff = ms - System.currentTimeMillis()
        if (diff <= 0) return "已过去"
        val minutes = diff / 60_000
        val hours = minutes / 60
        val days = hours / 24
        return when {
            days >= 1 -> "${days}天${if (hours % 24 > 0) "${hours % 24}小时" else ""}后"
            hours >= 1 -> "${hours}小时${if (minutes % 60 > 0) "${minutes % 60}分" else ""}后"
            minutes >= 1 -> "${minutes}分钟后"
            else -> "不到1分钟"
        }
    }
}
