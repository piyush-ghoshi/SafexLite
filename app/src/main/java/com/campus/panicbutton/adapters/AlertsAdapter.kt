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
 * RecyclerView adapter for displaying alerts in the Guard Dashboard
 * Handles alert display and user interactions (accept/resolve)
 */
class AlertsAdapter(
    private val currentUserId: String,
    private val onAcceptAlert: (Alert) -> Unit,
    private val onResolveAlert: (Alert) -> Unit,
    private val onAlertClick: (Alert) -> Unit
) : ListAdapter<Alert, AlertsAdapter.AlertViewHolder>(AlertDiffCallback()) {

    companion object {
        private val dateFormat = SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault())
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): AlertViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_alert, parent, false)
        return AlertViewHolder(view)
    }

    override fun onBindViewHolder(holder: AlertViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class AlertViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvGuardName: TextView = itemView.findViewById(R.id.tvGuardName)
        private val tvLocation: TextView = itemView.findViewById(R.id.tvLocation)
        private val tvTimestamp: TextView = itemView.findViewById(R.id.tvTimestamp)
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        private val tvStatus: TextView = itemView.findViewById(R.id.tvStatus)
        private val btnAccept: Button = itemView.findViewById(R.id.btnAccept)
        private val btnResolve: Button = itemView.findViewById(R.id.btnResolve)
        private val tvAcceptedBy: TextView = itemView.findViewById(R.id.tvAcceptedBy)

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

            // Show/hide accepted by information
            if (!alert.acceptedBy.isNullOrBlank() && alert.status != AlertStatus.ACTIVE) {
                tvAcceptedBy.text = "Accepted by: ${alert.acceptedBy}"
                tvAcceptedBy.visibility = View.VISIBLE
            } else {
                tvAcceptedBy.visibility = View.GONE
            }

            // Configure action buttons based on alert status and current user
            configureActionButtons(alert)

            // Set click listener for the entire item
            itemView.setOnClickListener {
                onAlertClick(alert)
            }
        }

        private fun configureActionButtons(alert: Alert) {
            when (alert.status) {
                AlertStatus.ACTIVE -> {
                    // Show accept button for active alerts
                    btnAccept.visibility = View.VISIBLE
                    btnResolve.visibility = View.GONE
                    btnAccept.setOnClickListener { onAcceptAlert(alert) }
                }
                AlertStatus.IN_PROGRESS -> {
                    // Show resolve button only if current user accepted the alert
                    if (alert.acceptedBy == currentUserId) {
                        btnAccept.visibility = View.GONE
                        btnResolve.visibility = View.VISIBLE
                        btnResolve.setOnClickListener { onResolveAlert(alert) }
                    } else {
                        btnAccept.visibility = View.GONE
                        btnResolve.visibility = View.GONE
                    }
                }
                AlertStatus.RESOLVED, AlertStatus.CLOSED -> {
                    // Hide all action buttons for resolved/closed alerts
                    btnAccept.visibility = View.GONE
                    btnResolve.visibility = View.GONE
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