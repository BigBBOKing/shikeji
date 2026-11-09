package com.shikeji.reminder.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** 重复方式 */
enum class RepeatMode(val code: Int, val label: String) {
    NONE(0, "不重复"),
    DAILY(1, "每天"),
    WEEKLY(2, "每周"),
    MONTHLY(3, "每月");

    companion object {
        fun fromCode(code: Int): RepeatMode = entries.firstOrNull { it.code == code } ?: NONE
    }
}

/** 一条事件记录 */
data class EventItem(
    val id: Long,
    var title: String,
    var note: String,
    /** 提醒时间，epoch 毫秒 */
    var triggerAt: Long,
    var repeat: RepeatMode,
    var done: Boolean,
    /** 创建时间，epoch 毫秒 */
    val createdAt: Long
) {
    val isPast: Boolean
        get() = triggerAt <= System.currentTimeMillis()

    /** 已过去且不重复的事件视为过期事项 */
    val isExpired: Boolean
        get() = !done && repeat == RepeatMode.NONE && isPast
}

/**
 * 事件存储：一个 JSON 文件放在应用私有目录，无第三方依赖，写入为原子替换。
 */
class EventStore private constructor(private val file: File) {

    private val items = mutableListOf<EventItem>()

    init {
        load()
    }

    private fun load() {
        items.clear()
        if (!file.exists()) return
        runCatching {
            val array = JSONArray(file.readText())
            for (i in 0 until array.length()) {
                val o = array.getJSONObject(i)
                items += EventItem(
                    id = o.optLong("id", -1L),
                    title = o.optString("title", ""),
                    note = o.optString("note", ""),
                    triggerAt = o.optLong("triggerAt", 0L),
                    repeat = RepeatMode.fromCode(o.optInt("repeat", 0)),
                    done = o.optBoolean("done", false),
                    createdAt = o.optLong("createdAt", 0L)
                )
            }
        }
    }

    private fun persist() {
        val array = JSONArray()
        items.forEach { e ->
            array.put(
                JSONObject()
                    .put("id", e.id)
                    .put("title", e.title)
                    .put("note", e.note)
                    .put("triggerAt", e.triggerAt)
                    .put("repeat", e.repeat.code)
                    .put("done", e.done)
                    .put("createdAt", e.createdAt)
            )
        }
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(array.toString())
        if (!tmp.renameTo(file)) {
            file.writeText(array.toString())
            tmp.delete()
        }
    }

    fun all(): List<EventItem> = items.sortedWith(
        compareBy<EventItem> { it.done || it.isExpired }.thenBy { it.triggerAt }
    )

    fun byId(id: Long): EventItem? = items.firstOrNull { it.id == id }

    fun upsert(item: EventItem) {
        val idx = items.indexOfFirst { it.id == item.id }
        if (idx >= 0) items[idx] = item else items.add(item)
        persist()
    }

    fun delete(id: Long) {
        items.removeAll { it.id == id }
        persist()
    }

    fun setDone(id: Long, done: Boolean) {
        byId(id)?.let {
            it.done = done
            persist()
        }
    }

    /** 下一次待触发的提醒时间，没有则返回 null */
    fun nextPendingTime(): Long? = items
        .filter { !it.done && it.triggerAt > System.currentTimeMillis() }
        .minOfOrNull { it.triggerAt }

    fun pendingCount(): Int = items.count { !it.done && !it.isExpired }

    fun newId(): Long = (items.maxOfOrNull { it.id } ?: 0L) + 1L

    companion object {
        @Volatile
        private var instance: EventStore? = null

        fun get(context: android.content.Context): EventStore =
            instance ?: synchronized(this) {
                instance ?: EventStore(File(context.filesDir, "events.json")).also { instance = it }
            }
    }
}
