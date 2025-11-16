package com.campus.panicbutton.models

import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import org.junit.Test
import org.junit.Assert.*
import java.util.Date

class AlertTest {

    @Test
    fun `alert creation with default values`() {
        val alert = Alert()
        
        assertEquals("", alert.id)
        assertEquals("", alert.guardId)
        assertEquals("", alert.guardName)
        assertNotNull(alert.timestamp)
        assertNull(alert.location)
        assertNull(alert.message)
        assertEquals(AlertStatus.ACTIVE, alert.status)
        assertNull(alert.acceptedBy)
        assertNull(alert.acceptedAt)
        assertNull(alert.resolvedAt)
        assertNull(alert.closedBy)
        assertNull(alert.closedAt)
    }

    @Test
    fun `alert creation with all parameters`() {
        val timestamp = Timestamp(Date())
        val location = CampusBlock(
            id = "block1",
            name = "Main Building",
            description = "Main campus building",
            coordinates = GeoPoint(40.7128, -74.0060),
            radius = 50.0
        )
        val acceptedAt = Timestamp(Date())
        val resolvedAt = Timestamp(Date())
        val closedAt = Timestamp(Date())

        val alert = Alert(
            id = "alert123",
            guardId = "guard456",
            guardName = "John Doe",
            timestamp = timestamp,
            location = location,
            message = "Emergency in progress",
            status = AlertStatus.IN_PROGRESS,
            acceptedBy = "guard789",
            acceptedAt = acceptedAt,
            resolvedAt = resolvedAt,
            closedBy = "admin123",
            closedAt = closedAt
        )

        assertEquals("alert123", alert.id)
        assertEquals("guard456", alert.guardId)
        assertEquals("John Doe", alert.guardName)
        assertEquals(timestamp, alert.timestamp)
        assertEquals(location, alert.location)
        assertEquals("Emergency in progress", alert.message)
        assertEquals(AlertStatus.IN_PROGRESS, alert.status)
        assertEquals("guard789", alert.acceptedBy)
        assertEquals(acceptedAt, alert.acceptedAt)
        assertEquals(resolvedAt, alert.resolvedAt)
        assertEquals("admin123", alert.closedBy)
        assertEquals(closedAt, alert.closedAt)
    }

    @Test
    fun `alert status enum values`() {
        assertEquals(4, AlertStatus.values().size)
        assertTrue(AlertStatus.values().contains(AlertStatus.ACTIVE))
        assertTrue(AlertStatus.values().contains(AlertStatus.IN_PROGRESS))
        assertTrue(AlertStatus.values().contains(AlertStatus.RESOLVED))
        assertTrue(AlertStatus.values().contains(AlertStatus.CLOSED))
    }

    @Test
    fun `alert validation - guard information required`() {
        val alert = Alert(
            guardId = "",
            guardName = ""
        )
        
        assertTrue(alert.guardId.isEmpty())
        assertTrue(alert.guardName.isEmpty())
    }

    @Test
    fun `alert message validation - optional field`() {
        val alertWithMessage = Alert(message = "Test message")
        val alertWithoutMessage = Alert(message = null)
        
        assertEquals("Test message", alertWithMessage.message)
        assertNull(alertWithoutMessage.message)
    }
}