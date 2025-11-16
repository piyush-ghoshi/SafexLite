package com.campus.panicbutton.database.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint

/**
 * Room entity for caching Alert data locally for offline support
 */
@Entity(tableName = "alerts")
data class AlertEntity(
    @PrimaryKey
    val id: String,
    val guardId: String,
    val guardName: String,
    val timestampSeconds: Long,
    val timestampNanoseconds: Int,
    val locationId: String?,
    val locationName: String?,
    val locationDescription: String?,
    val locationLatitude: Double?,
    val locationLongitude: Double?,
    val locationRadius: Double?,
    val message: String?,
    val status: String,
    val acceptedBy: String?,
    val acceptedAtSeconds: Long?,
    val acceptedAtNanoseconds: Int?,
    val resolvedAtSeconds: Long?,
    val resolvedAtNanoseconds: Int?,
    val closedBy: String?,
    val closedAtSeconds: Long?,
    val closedAtNanoseconds: Int?,
    val isSynced: Boolean = true, // Track if this alert is synced with Firebase
    val lastModified: Long = System.currentTimeMillis() // Track local modifications
) {
    /**
     * Convert AlertEntity to Alert model
     */
    fun toAlert(): Alert {
        val location = if (locationId != null && locationName != null && 
                          locationLatitude != null && locationLongitude != null) {
            CampusBlock(
                id = locationId,
                name = locationName,
                description = locationDescription ?: "",
                coordinates = GeoPoint(locationLatitude, locationLongitude),
                radius = locationRadius ?: 50.0
            )
        } else null

        return Alert(
            id = id,
            guardId = guardId,
            guardName = guardName,
            timestamp = Timestamp(timestampSeconds, timestampNanoseconds),
            location = location,
            message = message,
            status = AlertStatus.valueOf(status),
            acceptedBy = acceptedBy,
            acceptedAt = if (acceptedAtSeconds != null && acceptedAtNanoseconds != null) {
                Timestamp(acceptedAtSeconds, acceptedAtNanoseconds)
            } else null,
            resolvedAt = if (resolvedAtSeconds != null && resolvedAtNanoseconds != null) {
                Timestamp(resolvedAtSeconds, resolvedAtNanoseconds)
            } else null,
            closedBy = closedBy,
            closedAt = if (closedAtSeconds != null && closedAtNanoseconds != null) {
                Timestamp(closedAtSeconds, closedAtNanoseconds)
            } else null
        )
    }

    companion object {
        /**
         * Convert Alert model to AlertEntity
         */
        fun fromAlert(alert: Alert, isSynced: Boolean = true): AlertEntity {
            return AlertEntity(
                id = alert.id,
                guardId = alert.guardId,
                guardName = alert.guardName,
                timestampSeconds = alert.timestamp.seconds,
                timestampNanoseconds = alert.timestamp.nanoseconds,
                locationId = alert.location?.id,
                locationName = alert.location?.name,
                locationDescription = alert.location?.description,
                locationLatitude = alert.location?.coordinates?.latitude,
                locationLongitude = alert.location?.coordinates?.longitude,
                locationRadius = alert.location?.radius,
                message = alert.message,
                status = alert.status.name,
                acceptedBy = alert.acceptedBy,
                acceptedAtSeconds = alert.acceptedAt?.seconds,
                acceptedAtNanoseconds = alert.acceptedAt?.nanoseconds,
                resolvedAtSeconds = alert.resolvedAt?.seconds,
                resolvedAtNanoseconds = alert.resolvedAt?.nanoseconds,
                closedBy = alert.closedBy,
                closedAtSeconds = alert.closedAt?.seconds,
                closedAtNanoseconds = alert.closedAt?.nanoseconds,
                isSynced = isSynced
            )
        }
    }
}