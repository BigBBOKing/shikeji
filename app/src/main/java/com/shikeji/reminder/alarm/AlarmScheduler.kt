package com.shikeji.reminder.alarm

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.shikeji.reminder.data.EventItem
import com.shikeji.reminder.data.EventStore
import com.shikeji.reminder.data.RepeatMode
import java.util.Calendar

/**
 * 闹钟调度：优先使用精确闹钟（可在息屏 Doze 下准时触发），
 * 没有授权时退化为 setAndAllowWhileIdle，仍会触发但可能有几分钟偏差。
 */
object AlarmScheduler {

    private const val SNOOZE_OFFSET = 900_000

    fun canScheduleExact(context: Context): Boolean =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val am = context.getSystemService(AlarmManager::class.java)
            am?.canScheduleExactAlarms() == true
        } else {
            true
        }

    fun schedule(context: Context, event: EventItem) {
        if (event.done) {
            cancel(context, event.id)
            return
        }
        setAlarm(context, event.triggerAt, event.id.toInt()) { intent ->
            intent.putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
        }
    }

    /** 稍后提醒：同一事件另开一个 requestCode，避免覆盖重复提醒的闹钟 */
    fun snooze(context: Context, eventId: Long, minutes: Int) {
        val time = System.currentTimeMillis() + minutes * 60_000L
        setAlarm(context, time, eventId.toInt() + SNOOZE_OFFSET) { intent ->
            intent.putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            intent.putExtra(ReminderReceiver.EXTRA_IS_SNOOZE, true)
        }
    }

    private fun setAlarm(
        context: Context,
        time: Long,
        requestCode: Int,
        decorate: (Intent) -> Unit
    ) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            setAction(ReminderReceiver.ACTION_REMIND)
            decorate(this)
        }
        val pi = PendingIntent.getBroadcast(
            context,
            requestCode,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> {
                if (am.canScheduleExactAlarms()) {
                    am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                } else {
                    am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
                }
            }
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.M -> {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, time, pi)
            }
            else -> {
                am.setExact(AlarmManager.RTC_WAKEUP, time, pi)
            }
        }
    }

    fun cancel(context: Context, eventId: Long) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(PendingIntent.getBroadcast(
            context,
            eventId.toInt(),
            Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_REMIND).apply {
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, eventId)
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        ))
    }

    /** 计算重复事件的下一次触发时间；不重复或多轮之后已过期则返回 null */
    fun nextOccurrence(from: Long, repeat: RepeatMode): Long? {
        if (repeat == RepeatMode.NONE) return null
        val now = System.currentTimeMillis()
        val cal = Calendar.getInstance().apply { timeInMillis = from }
        var guard = 0
        while (cal.timeInMillis <= now && guard < 1000) {
            when (repeat) {
                RepeatMode.DAILY -> cal.add(Calendar.DAY_OF_YEAR, 1)
                RepeatMode.WEEKLY -> cal.add(Calendar.DAY_OF_YEAR, 7)
                RepeatMode.MONTHLY -> cal.add(Calendar.MONTH, 1)
                RepeatMode.NONE -> return null
            }
            guard++
        }
        return cal.timeInMillis
    }

    /** 开机 / 时间变更后，为所有未完成事件重新挂载闹钟 */
    fun rescheduleAll(context: Context) {
        val store = EventStore.get(context)
        store.all().forEach { event ->
            if (event.done) return@forEach
            if (event.triggerAt <= System.currentTimeMillis() && event.repeat == RepeatMode.NONE) return@forEach
            if (event.triggerAt <= System.currentTimeMillis()) {
                val next = nextOccurrence(event.triggerAt, event.repeat)
                if (next != null) {
                    event.triggerAt = next
                    store.upsert(event)
                }
            }
            schedule(context, event)
        }
    }
}
