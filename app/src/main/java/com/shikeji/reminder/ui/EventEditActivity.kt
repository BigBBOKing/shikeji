package com.shikeji.reminder.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.os.Bundle
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.isVisible
import com.shikeji.reminder.R
import com.shikeji.reminder.alarm.AlarmScheduler
import com.shikeji.reminder.data.EventItem
import com.shikeji.reminder.data.EventStore
import com.shikeji.reminder.data.RepeatMode
import com.shikeji.reminder.databinding.ActivityEditBinding
import com.shikeji.reminder.util.TimeFormat
import java.util.Calendar

class EventEditActivity : AppCompatActivity() {

    private lateinit var binding: ActivityEditBinding
    private lateinit var store: EventStore
    private var eventId: Long = 0L
    private val picked = Calendar.getInstance()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityEditBinding.inflate(layoutInflater)
        setContentView(binding.root)
        store = EventStore.get(this)
        eventId = intent.getLongExtra(EXTRA_ID, 0L)

        val existing = if (eventId > 0) store.byId(eventId) else null
        if (existing != null) {
            picked.timeInMillis = existing.triggerAt
            binding.title.setText(existing.title)
            binding.note.setText(existing.note)
            binding.repeatGroup.check(
                when (existing.repeat) {
                    RepeatMode.NONE -> R.id.repeat_none
                    RepeatMode.DAILY -> R.id.repeat_daily
                    RepeatMode.WEEKLY -> R.id.repeat_weekly
                    RepeatMode.MONTHLY -> R.id.repeat_monthly
                }
            )
            binding.toolbar.title = getString(R.string.edit_title)
            binding.deleteButton.isVisible = true
        } else {
            // 默认：一小时后，整点
            picked.timeInMillis = System.currentTimeMillis() + 60 * 60 * 1000L
            picked.set(Calendar.SECOND, 0)
            picked.set(Calendar.MILLISECOND, 0)
            picked.set(Calendar.MINUTE, 0)
            picked.add(Calendar.HOUR_OF_DAY, 1)
            binding.toolbar.title = getString(R.string.new_title)
            binding.deleteButton.isVisible = false
        }

        binding.toolbar.setNavigationOnClickListener { finish() }
        binding.dateRow.setOnClickListener { showDatePicker() }
        binding.timeRow.setOnClickListener { showTimePicker() }
        binding.saveButton.setOnClickListener { save() }
        binding.deleteButton.setOnClickListener { confirmDelete() }

        updateDateTimeLabels()
    }

    private fun showDatePicker() {
        DatePickerDialog(
            this,
            { _, year, month, day ->
                picked.set(Calendar.YEAR, year)
                picked.set(Calendar.MONTH, month)
                picked.set(Calendar.DAY_OF_MONTH, day)
                updateDateTimeLabels()
            },
            picked.get(Calendar.YEAR),
            picked.get(Calendar.MONTH),
            picked.get(Calendar.DAY_OF_MONTH)
        ).show()
    }

    private fun showTimePicker() {
        TimePickerDialog(
            this,
            { _, hour, minute ->
                picked.set(Calendar.HOUR_OF_DAY, hour)
                picked.set(Calendar.MINUTE, minute)
                picked.set(Calendar.SECOND, 0)
                updateDateTimeLabels()
            },
            picked.get(Calendar.HOUR_OF_DAY),
            picked.get(Calendar.MINUTE),
            true
        ).show()
    }

    private fun updateDateTimeLabels() {
        val year = picked.get(Calendar.YEAR)
        val month = picked.get(Calendar.MONTH) + 1
        val day = picked.get(Calendar.DAY_OF_MONTH)
        binding.dateValue.text = "$year-$month-$day"
        binding.dateHint.text = TimeFormat.day(picked.timeInMillis)
        binding.timeValue.text = TimeFormat.time(picked.timeInMillis)
        binding.timeHint.text = if (picked.timeInMillis > System.currentTimeMillis()) {
            TimeFormat.distance(picked.timeInMillis)
        } else {
            getString(R.string.past_time)
        }
    }

    private fun selectedRepeat(): RepeatMode = when (binding.repeatGroup.checkedRadioButtonId) {
        R.id.repeat_daily -> RepeatMode.DAILY
        R.id.repeat_weekly -> RepeatMode.WEEKLY
        R.id.repeat_monthly -> RepeatMode.MONTHLY
        else -> RepeatMode.NONE
    }

    private fun save() {
        val title = binding.title.text?.toString()?.trim().orEmpty()
        if (title.isEmpty()) {
            binding.titleLayout.error = getString(R.string.error_title_required)
            binding.title.requestFocus()
            return
        }
        binding.titleLayout.error = null

        val repeat = selectedRepeat()
        val triggerAt = picked.timeInMillis
        val isPast = triggerAt <= System.currentTimeMillis()

        if (isPast && repeat == RepeatMode.NONE) {
            AlertDialog.Builder(this)
                .setTitle(R.string.past_time_title)
                .setMessage(R.string.past_time_message)
                .setNegativeButton(R.string.cancel, null)
                .setPositiveButton(R.string.save_as_record) { _, _ -> persist(title, repeat, triggerAt) }
                .show()
            return
        }
        persist(title, repeat, triggerAt)
    }

    private fun persist(title: String, repeat: RepeatMode, triggerAt: Long) {
        val note = binding.note.text?.toString()?.trim().orEmpty()
        val item = store.byId(eventId) ?: EventItem(
            id = store.newId(),
            title = title,
            note = note,
            triggerAt = triggerAt,
            repeat = repeat,
            done = false,
            createdAt = System.currentTimeMillis()
        )

        // 时间或重复规则变了，先取消旧闹钟
        AlarmScheduler.cancel(this, item.id)

        // 重复事件的设定时间已经过去时，顺延到下一次，避免闹钟一到就立刻响
        val effective = if (triggerAt <= System.currentTimeMillis() && repeat != RepeatMode.NONE) {
            AlarmScheduler.nextOccurrence(triggerAt, repeat) ?: triggerAt
        } else {
            triggerAt
        }

        item.title = title
        item.note = note
        item.triggerAt = effective
        item.repeat = repeat
        item.done = false
        store.upsert(item)

        if (effective > System.currentTimeMillis()) {
            AlarmScheduler.schedule(this, item)
            val exact = AlarmScheduler.canScheduleExact(this)
            Toast.makeText(
                this,
                if (exact) getString(R.string.saved_exact, TimeFormat.full(effective))
                else getString(R.string.saved_inexact, TimeFormat.full(effective)),
                Toast.LENGTH_LONG
            ).show()
        } else {
            Toast.makeText(this, R.string.saved_as_record_toast, Toast.LENGTH_SHORT).show()
        }
        finish()
    }

    private fun confirmDelete() {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(R.string.delete_message_short)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                val item = store.byId(eventId)
                if (item != null) {
                    AlarmScheduler.cancel(this, item.id)
                    com.shikeji.reminder.alarm.NotificationHelper.cancel(this, item)
                    store.delete(item.id)
                }
                finish()
            }
            .show()
    }

    companion object {
        const val EXTRA_ID = "extra_id"
    }
}
