package com.campus.panicbutton.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.campus.panicbutton.R
import com.campus.panicbutton.models.TimelineItem
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for displaying alert timeline items
 */
class TimelineAdapter(private var timelineItems: List<TimelineItem>) :
    RecyclerView.Adapter<TimelineAdapter.TimelineViewHolder>() {

    private val dateFormat = SimpleDateFormat("MMM dd, yyyy h:mm a", Locale.getDefault())

    class TimelineViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvTimelineTitle: TextView = itemView.findViewById(R.id.tvTimelineTitle)
        val tvTimelineDescription: TextView = itemView.findViewById(R.id.tvTimelineDescription)
        val tvTimelineTime: TextView = itemView.findViewById(R.id.tvTimelineTime)
        val timelineIndicator: View = itemView.findViewById(R.id.timelineIndicator)
        val timelineLine: View = itemView.findViewById(R.id.timelineLine)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TimelineViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_timeline, parent, false)
        return TimelineViewHolder(view)
    }

    override fun onBindViewHolder(holder: TimelineViewHolder, position: Int) {
        val item = timelineItems[position]
        
        holder.tvTimelineTitle.text = item.title
        holder.tvTimelineDescription.text = item.description
        holder.tvTimelineTime.text = dateFormat.format(item.timestamp.toDate())
        
        // Set timeline indicator based on active status
        if (item.isActive) {
            holder.timelineIndicator.setBackgroundResource(R.drawable.timeline_dot_active)
        } else {
            holder.timelineIndicator.setBackgroundResource(R.drawable.timeline_dot_inactive)
        }
        
        // Hide timeline line for the last item
        if (position == timelineItems.size - 1) {
            holder.timelineLine.visibility = View.GONE
        } else {
            holder.timelineLine.visibility = View.VISIBLE
        }
    }

    override fun getItemCount(): Int = timelineItems.size

    /**
     * Update the timeline items and refresh the adapter
     */
    fun updateTimelineItems(newItems: List<TimelineItem>) {
        timelineItems = newItems
        notifyDataSetChanged()
    }
}