package com.shikeji.reminder

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import com.shikeji.reminder.alarm.AlarmScheduler
import com.shikeji.reminder.alarm.NotificationHelper
import com.shikeji.reminder.data.EventItem
import com.shikeji.reminder.data.EventStore
import com.shikeji.reminder.databinding.ActivityMainBinding
import com.shikeji.reminder.ui.EventAdapter
import com.shikeji.reminder.ui.EventEditActivity
import com.shikeji.reminder.util.TimeFormat

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var store: EventStore
    private lateinit var adapter: EventAdapter

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { refresh() }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        store = EventStore.get(this)
        adapter = EventAdapter(onClick = { openEditor(it.id) }, onLongClick = { showItemMenu(it) })

        binding.recycler.layoutManager = LinearLayoutManager(this)
        binding.recycler.adapter = adapter
        binding.fab.setOnClickListener { openEditor(0L) }

        binding.permissionBanner.setOnClickListener { requestExactAlarmPermission() }
        binding.emptyAction.setOnClickListener { openEditor(0L) }

        requestNotificationPermissionIfNeeded()
        openEventFromIntent(intent)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openEventFromIntent(intent)
    }

    /** 从通知点进来时，直接打开对应事件的详情 */
    private fun openEventFromIntent(intent: Intent?) {
        val id = intent?.getLongExtra(NotificationHelper.EXTRA_OPEN_EVENT, 0L) ?: 0L
        if (id > 0) openEditor(id)
    }

    override fun onResume() {
        super.onResume()
        refresh()
    }

    private fun refresh() {
        val items = store.all()
        adapter.submitList(items)
        binding.emptyGroup.isVisible = items.isEmpty()

        val pending = store.pendingCount()
        val next = store.nextPendingTime()
        binding.subtitle.text = when {
            items.isEmpty() -> getString(R.string.subtitle_empty)
            pending == 0 -> getString(R.string.subtitle_no_pending, items.size)
            next != null -> getString(R.string.subtitle_pending, pending, TimeFormat.distance(next))
            else -> getString(R.string.subtitle_pending_no_time, pending)
        }

        val needsExact = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && !AlarmScheduler.canScheduleExact(this)
        binding.permissionBanner.isVisible = needsExact && pending > 0
    }

    private fun openEditor(id: Long) {
        startActivity(Intent(this, EventEditActivity::class.java).putExtra(EventEditActivity.EXTRA_ID, id))
    }

    private fun showItemMenu(item: EventItem) {
        val options = mutableListOf<String>()
        options += getString(R.string.menu_edit)
        options += if (item.done) getString(R.string.menu_mark_undone) else getString(R.string.menu_mark_done)
        if (item.isExpired) options += getString(R.string.menu_reschedule)
        options += getString(R.string.menu_delete)

        AlertDialog.Builder(this)
            .setTitle(item.title.ifBlank { getString(R.string.untitled) })
            .setItems(options.toTypedArray()) { _, which ->
                when (options[which]) {
                    getString(R.string.menu_edit) -> openEditor(item.id)
                    getString(R.string.menu_mark_done) -> markDone(item, true)
                    getString(R.string.menu_mark_undone) -> markDone(item, false)
                    getString(R.string.menu_reschedule) -> openEditor(item.id)
                    getString(R.string.menu_delete) -> confirmDelete(item)
                }
            }
            .show()
    }

    private fun markDone(item: EventItem, done: Boolean) {
        store.setDone(item.id, done)
        if (done) {
            AlarmScheduler.cancel(this, item.id)
            NotificationHelper.cancel(this, item)
        }
        // 撤销完成时，如果时间还在将来，需要重新挂闹钟
        if (!done && item.triggerAt > System.currentTimeMillis()) {
            AlarmScheduler.schedule(this, item)
        }
        refresh()
    }

    private fun confirmDelete(item: EventItem) {
        AlertDialog.Builder(this)
            .setTitle(R.string.delete_title)
            .setMessage(getString(R.string.delete_message, item.title.ifBlank { getString(R.string.untitled) }))
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.delete) { _, _ ->
                AlarmScheduler.cancel(this, item.id)
                NotificationHelper.cancel(this, item)
                store.delete(item.id)
                refresh()
            }
            .show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestExactAlarmPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            runCatching {
                startActivity(
                    Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                        .setData(Uri.parse("package:$packageName"))
                )
            }.onFailure {
                runCatching { startActivity(Intent(Settings.ACTION_SETTINGS)) }
            }
        }
    }
}
