package com.shikeji.reminder.alarm

import android.app.NotificationManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.shikeji.reminder.R
import com.shikeji.reminder.data.EventStore

class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val eventId = intent.getLongExtra(EXTRA_EVENT_ID, -1L)
        if (eventId <= 0) return

        val store = EventStore.get(context)
        val event = store.byId(eventId) ?: return

        when (intent.action) {
            ACTION_SNOOZE -> {
                NotificationHelper.cancel(context, event)
                AlarmScheduler.snooze(context, eventId, SNOOZE_MINUTES)
                return
            }
            ACTION_DONE -> {
                store.setDone(eventId, true)
                AlarmScheduler.cancel(context, eventId)
                NotificationHelper.cancel(context, event)
                return
            }
        }

        if (event.done) return

        // 已经提醒过，把时间推进到下一次（仅重复事件）
        val isSnooze = intent.getBooleanExtra(EXTRA_IS_SNOOZE, false)
        if (!isSnooze) {
            NotificationHelper.show(context, event)
            val next = AlarmScheduler.nextOccurrence(event.triggerAt, event.repeat)
            if (next != null) {
                event.triggerAt = next
                store.upsert(event)
                AlarmScheduler.schedule(context, event)
            }
        } else {
            NotificationHelper.show(context, event)
        }
    }

    companion object {
        const val ACTION_REMIND = "com.shikeji.reminder.ACTION_REMIND"
        const val ACTION_SNOOZE = "com.shikeji.reminder.ACTION_SNOOZE"
        const val ACTION_DONE = "com.shikeji.reminder.ACTION_DONE"
        const val EXTRA_EVENT_ID = "extra_event_id"
        const val EXTRA_IS_SNOOZE = "extra_is_snooze"
        const val SNOOZE_MINUTES = 10
    }
}

/** 通知构建：一条提醒 = 一个独立通知，带「稍后提醒」「完成」两个操作 */
object NotificationHelper {

    private const val CHANNEL_ID = "event_reminders"

    private fun ensureChannel(context: Context) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.O) return
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        val channel = android.app.NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.channel_name),
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = context.getString(R.string.channel_desc)
            enableVibration(true)
            enableLights(true)
        }
        nm.createNotificationChannel(channel)
    }

    fun notificationId(eventId: Long): Int = (10_000 + eventId).toInt()

    fun show(context: Context, event: com.shikeji.reminder.data.EventItem) {
        ensureChannel(context)
        val nm = context.getSystemService(NotificationManager::class.java) ?: return

        val openIntent = Intent(context, com.shikeji.reminder.MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra(EXTRA_OPEN_EVENT, event.id)
        }
        val openPi = android.app.PendingIntent.getActivity(
            context,
            event.id.toInt(),
            openIntent,
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val snoozePi = android.app.PendingIntent.getBroadcast(
            context,
            20_000 + event.id.toInt(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_SNOOZE
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val donePi = android.app.PendingIntent.getBroadcast(
            context,
            30_000 + event.id.toInt(),
            Intent(context, ReminderReceiver::class.java).apply {
                action = ReminderReceiver.ACTION_DONE
                putExtra(ReminderReceiver.EXTRA_EVENT_ID, event.id)
            },
            android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
        )

        val builder = androidx.core.app.NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(event.title)
            .setContentText(event.note.ifBlank { context.getString(R.string.notify_default_text) })
            .setStyle(androidx.core.app.NotificationCompat.BigTextStyle().bigText(
                buildString {
                    event.note.takeIf { it.isNotBlank() }?.let { append(it).append('\n') }
                    append("提醒时间：")
                    append(com.shikeji.reminder.util.TimeFormat.full(event.triggerAt))
                    if (event.repeat != com.shikeji.reminder.data.RepeatMode.NONE) {
                        append("（").append(event.repeat.label).append("）")
                    }
                }
            ))
            .setPriority(androidx.core.app.NotificationCompat.PRIORITY_HIGH)
            .setCategory(androidx.core.app.NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(openPi)
            .addAction(0, context.getString(R.string.action_snooze), snoozePi)
            .addAction(0, context.getString(R.string.action_done), donePi)

        runCatching { nm.notify(notificationId(event.id), builder.build()) }
    }

    fun cancel(context: Context, event: com.shikeji.reminder.data.EventItem) {
        val nm = context.getSystemService(NotificationManager::class.java) ?: return
        runCatching { nm.cancel(notificationId(event.id)) }
    }

    const val EXTRA_OPEN_EVENT = "extra_open_event"
}
