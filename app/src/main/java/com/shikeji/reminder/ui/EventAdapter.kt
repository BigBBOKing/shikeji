package com.shikeji.reminder.ui

import android.graphics.Paint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.shikeji.reminder.R
import com.shikeji.reminder.data.EventItem
import com.shikeji.reminder.data.RepeatMode
import com.shikeji.reminder.databinding.ItemEventBinding
import com.shikeji.reminder.util.TimeFormat

class EventAdapter(
    private val onClick: (EventItem) -> Unit,
    private val onLongClick: (EventItem) -> Unit
) : ListAdapter<EventItem, EventAdapter.VH>(DIFF) {

    class VH(val binding: ItemEventBinding) : RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemEventBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val item = getItem(position)
        val b = holder.binding
        val context = b.root.context

        b.title.text = item.title.ifBlank { context.getString(R.string.untitled) }
        b.note.text = item.note
        b.note.visibility = if (item.note.isBlank()) View.GONE else View.VISIBLE

        b.time.text = TimeFormat.time(item.triggerAt)
        b.day.text = TimeFormat.day(item.triggerAt)

        when {
            item.done -> {
                b.status.text = context.getString(R.string.status_done)
                b.title.paintFlags = b.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.accent.setBackgroundColor(context.getColor(R.color.status_done))
                b.status.setTextColor(context.getColor(R.color.text_secondary))
                b.root.alpha = 0.72f
            }
            item.isExpired -> {
                b.status.text = context.getString(R.string.status_expired)
                b.title.paintFlags = b.title.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
                b.accent.setBackgroundColor(context.getColor(R.color.status_expired))
                b.status.setTextColor(context.getColor(R.color.text_secondary))
                b.root.alpha = 0.72f
            }
            else -> {
                b.status.text = context.getString(R.string.status_upcoming, TimeFormat.distance(item.triggerAt))
                b.title.paintFlags = b.title.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
                b.accent.setBackgroundColor(context.getColor(R.color.accent))
                b.status.setTextColor(context.getColor(R.color.accent_strong))
                b.root.alpha = 1f
            }
        }

        if (item.repeat == RepeatMode.NONE) {
            b.repeat.visibility = View.GONE
        } else {
            b.repeat.visibility = View.VISIBLE
            b.repeat.text = item.repeat.label
        }

        b.root.setOnClickListener { onClick(item) }
        b.root.setOnLongClickListener {
            onLongClick(item)
            true
        }
    }

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<EventItem>() {
            override fun areItemsTheSame(a: EventItem, b: EventItem) = a.id == b.id
            override fun areContentsTheSame(a: EventItem, b: EventItem) =
                a.title == b.title && a.note == b.note && a.triggerAt == b.triggerAt &&
                    a.repeat == b.repeat && a.done == b.done
        }
    }
}
