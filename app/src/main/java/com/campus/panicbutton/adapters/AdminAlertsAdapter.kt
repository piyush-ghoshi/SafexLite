package com.campus.panicbutton.adapters

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.campus.panicbutton.R
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import java.text.SimpleDateFormat
import java.util.*

/**
 * RecyclerView adapter for displaying alerts in the Admin Dashboard
 * Handles alert display with admin-specific actions and comprehensive information
 */
class AdminAlertsAdapter(
    private val onCloseAlert: (Alert) -> Unit,
    private val onReopenAlert: (Alert) -> Unit,
    private val onAlertClick: (Alert) -> Unit
) : ListAdapter<Alert, AdminAlertsAdapter.AdminAlertViewHolder>(AlertDiffCallback()) {

    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
        private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AdminAlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_admin_alert, parent, false)
        return AdminAlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AdminAlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AdminAlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvGuardName: TextView = itemView.findViewById(R.id.tvGuardName)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val tvAcceptedBy: TextView = itemView.findViewById(R.id.tvAcceptedBy)
        private val tvResponseTime: TextView = itemView.findViewById(R.id.tvResponseTime)
        private val tvResolutionTime: TextView = itemView.findViewById(R.id.tvResolutionTime)
        private val btnClose: Button = itemView.findViewById(R.id.btnClose)
        private val btnReopen: Button = itemView.findViewById(R.id.btnReopen)

        fun bind(alert: Alert) {
            // Set basic alert information
            tvGuardName.text = alert.guardName
            tvLocation.text = alert.location?.name ?: "Unknown Location"
            tvTimestamp.text = dateFormat.format(alert.timestamp.toDate())
            
            // Set message or hide if empty
            if (!alert.message.isNullOrBlank()) {
                tvMessage.text = alert.message
                tvMessage.visibility = View.VISIBLE
            } else {
                tvMessage.visibility = View.GONE
            }

            // Set status with appropriate color
            tvStatus.text = alert.status.name.replace("_", " ")
            val statusColor = when (alert.status) {
                AlertStatus.ACTIVE -> ContextCompat.getColor(itemView.context, R.color.status_active)
                AlertStatus.IN_PROGRESS -> ContextCompat.getColor(itemView.context, R.color.status_in_progress)
                AlertStatus.RESOLVED -> ContextCompat.getColor(itemView.context, R.color.status_resolved)
                AlertStatus.CLOSED -> ContextCompat.getColor(itemView.context, R.color.status_closed)
            }
            tvStatus.setTextColor(statusColor)

            // Show accepted by information
            if (!alert.acceptedBy.isNullOrBlank() && alert.status != AlertStatus.ACTIVE) {
                tvAcceptedBy.text = "Accepted by: ${alert.acceptedBy}"
                tvAcceptedBy.visibility = View.VISIBLE
            } else {
                tvAcceptedBy.visibility = View.GONE
            }

            // Show response time (time from creation to acceptance)
            if (alert.acceptedAt != null) {
                val responseTimeMs = alert.acceptedAt.toDate().time - alert.timestamp.toDate().time
                val responseTimeMinutes = responseTimeMs / (1000 * 60)
                tvResponseTime.text = "Response time: ${responseTimeMinutes}m"
                tvResponseTime.visibility = View.VISIBLE
            } else {
                tvResponseTime.visibility = View.GONE
            }

            // Show resolution time (time from acceptance to resolution)
            if (alert.resolvedAt != null && alert.acceptedAt != null) {
                val resolutionTimeMs = alert.resolvedAt.toDate().time - alert.acceptedAt.toDate().time
                val resolutionTimeMinutes = resolutionTimeMs / (1000 * 60)
                tvResolutionTime.text = "Resolution time: ${resolutionTimeMinutes}m"
                tvResolutionTime.visibility = View.VISIBLE
            } else {
                tvResolutionTime.visibility = View.GONE
            }

            // Configure admin action buttons
            configureAdminActions(alert)

            // Set click listener for the entire item
            itemView.setOnClickListener {
                onAlertClick(alert)
            }
        }

        private fun configureAdminActions(alert: Alert) {
            when (alert.status) {
                AlertStatus.ACTIVE, AlertStatus.IN_PROGRESS, AlertStatus.RESOLVED -> {
                    // Show close button for non-closed alerts
                    btnClose.visibility = View.VISIBLE
                    btnReopen.visibility = View.GONE
                    btnClose.setOnClickListener { onCloseAlert(alert) }
                }
                AlertStatus.CLOSED -> {
                    // Show reopen button for closed alerts
                    btnClose.visibility = View.GONE
                    btnReopen.visibility = View.VISIBLE
                    btnReopen.setOnClickListener { onReopenAlert(alert) }
                }
            }
        }
    }

    class AlertDiffCallback : DiffUtil.ItemCallback<Alert>() {
        override fun areItemsTheSame(oldItem: Alert, newItem: Alert): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: Alert, newItem: Alert): Boolean {
            return oldItem == newItem
        }
    }
}