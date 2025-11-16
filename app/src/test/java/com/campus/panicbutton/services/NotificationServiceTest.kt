package com.campus.panicbutton.services

import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.CampusBlock
import com.google.firebase.Timestamp
import com.google.firebase.firestore.GeoPoint
import com.google.firebase.messaging.RemoteMessage
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.Date

class NotificationServiceTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockNotificationManager: NotificationManager
    
    @Mock
    private lateinit var mockRemoteMessage: RemoteMessage
    
    @Mock
    private lateinit var mockRemoteMessageData: Map<String, String>
    
    private lateinit var notificationService: NotificationService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        whenever(mockContext.getSystemService(Context.NOTIFICATION_SERVICE))
            .thenReturn(mockNotificationManager)
        
        notificationService = NotificationService()
        
        // Use reflection to set context
        val contextField = NotificationService::class.java.getDeclaredField("context")
        contextField.isAccessible = true
        contextField.set(notificationService, mockContext)
    }

    @Test
    fun `createNotificationChannel creates channel correctly`() {
        notificationService.createNotificationChannel()
        
        verify(mockNotificationManager).createNotificationChannel(any())
    }

    @Test
    fun `displayAlertNotification shows notification`() {
        val alert = Alert(
            id = "alert123",
            guardId = "guard456",
            guardName = "John Doe",
            timestamp = Timestamp(Date()),
            location = CampusBlock(
                id = "main",
                name = "Main Building",
                coordinates = GeoPoint(40.7128, -74.0060)
            ),
            message = "Emergency in progress",
            status = AlertStatus.ACTIVE
        )

        notificationService.displayAlertNotification(alert)
        
        verify(mockNotificationManager).notify(eq(alert.id.hashCode()), any())
    }

    @Test
    fun `handleIncomingNotification processes new alert`() {
        val notificationData = mapOf(
            "type" to "new_alert",
            "alertId" to "alert123",
            "guardName" to "John Doe",
            "location" to "Main Building",
            "message" to "Emergency situation",
            "timestamp" to "1234567890"
        )

        whenever(mockRemoteMessage.data).thenReturn(notificationData)
        
        notificationService.handleIncomingNotification(mockRemoteMessage)
        
        verify(mockNotificationManager).notify(any<Int>(), any())
    }

    @Test
    fun `handleIncomingNotification processes status update`() {
        val notificationData = mapOf(
            "type" to "status_update",
            "alertId" to "alert123",
            "status" to "IN_PROGRESS",
            "acceptedBy" to "Jane Smith"
        )

        whenever(mockRemoteMessage.data).thenReturn(notificationData)
        
        notificationService.handleIncomingNotification(mockRemoteMessage)
        
        verify(mockNotificationManager).notify(any<Int>(), any())
    }

    @Test
    fun `handleIncomingNotification ignores invalid data`() {
        val invalidData = mapOf(
            "invalid" to "data"
        )

        whenever(mockRemoteMessage.data).thenReturn(invalidData)
        
        notificationService.handleIncomingNotification(mockRemoteMessage)
        
        verify(mockNotificationManager, never()).notify(any<Int>(), any())
    }

    @Test
    fun `notification click intent contains correct data`() {
        val alert = Alert(
            id = "alert123",
            guardId = "guard456",
            guardName = "John Doe"
        )

        val intent = notificationService.createNotificationIntent(alert)
        
        assertNotNull("Should create intent", intent)
        assertEquals("Should have alert ID extra", "alert123", intent.getStringExtra("alertId"))
        assertTrue("Should have correct flags", intent.flags and Intent.FLAG_ACTIVITY_NEW_TASK != 0)
    }

    @Test
    fun `notification priority set correctly for emergency`() {
        val alert = Alert(
            id = "alert123",
            guardId = "guard456",
            guardName = "John Doe",
            status = AlertStatus.ACTIVE
        )

        val notification = notificationService.buildNotification(alert)
        
        assertEquals("Should have high priority", android.app.Notification.PRIORITY_HIGH, notification.priority)
        assertTrue("Should have sound", notification.defaults and android.app.Notification.DEFAULT_SOUND != 0)
        assertTrue("Should have vibration", notification.defaults and android.app.Notification.DEFAULT_VIBRATE != 0)
    }

    @Test
    fun `notification content formatted correctly`() {
        val alert = Alert(
            id = "alert123",
            guardId = "guard456",
            guardName = "John Doe",
            location = CampusBlock(name = "Main Building"),
            message = "Fire emergency",
            status = AlertStatus.ACTIVE
        )

        val notification = notificationService.buildNotification(alert)
        
        assertTrue("Title should contain guard name", 
            notification.extras.getString("android.title")?.contains("John Doe") == true)
        assertTrue("Content should contain location", 
            notification.extras.getString("android.text")?.contains("Main Building") == true)
        assertTrue("Content should contain message", 
            notification.extras.getString("android.text")?.contains("Fire emergency") == true)
    }

    @Test
    fun `notification actions for different alert statuses`() {
        val activeAlert = Alert(
            id = "alert123",
            status = AlertStatus.ACTIVE
        )

        val inProgressAlert = Alert(
            id = "alert456",
            status = AlertStatus.IN_PROGRESS
        )

        val activeNotification = notificationService.buildNotification(activeAlert)
        val inProgressNotification = notificationService.buildNotification(inProgressAlert)

        // Active alerts should have "Accept" action
        assertTrue("Active alert should have accept action", 
            activeNotification.actions?.any { it.title == "Accept" } == true)

        // In-progress alerts should have "Resolve" action
        assertTrue("In-progress alert should have resolve action", 
            inProgressNotification.actions?.any { it.title == "Resolve" } == true)
    }

    @Test
    fun `notification grouping for multiple alerts`() {
        val alerts = listOf(
            Alert(id = "alert1", guardName = "Guard 1"),
            Alert(id = "alert2", guardName = "Guard 2"),
            Alert(id = "alert3", guardName = "Guard 3")
        )

        alerts.forEach { alert ->
            notificationService.displayAlertNotification(alert)
        }

        // Should create group notification when multiple alerts
        verify(mockNotificationManager, times(alerts.size + 1)).notify(any<Int>(), any())
    }

    @Test
    fun `notification cancellation when alert resolved`() {
        val alert = Alert(
            id = "alert123",
            status = AlertStatus.RESOLVED
        )

        notificationService.cancelNotification(alert.id)
        
        verify(mockNotificationManager).cancel(alert.id.hashCode())
    }

    @Test
    fun `notification sound and vibration patterns`() {
        val urgentAlert = Alert(
            id = "alert123",
            message = "URGENT: Multiple casualties",
            status = AlertStatus.ACTIVE
        )

        val regularAlert = Alert(
            id = "alert456",
            message = "Minor incident",
            status = AlertStatus.ACTIVE
        )

        val urgentNotification = notificationService.buildNotification(urgentAlert)
        val regularNotification = notificationService.buildNotification(regularAlert)

        // Urgent alerts should have different sound/vibration pattern
        assertNotEquals("Urgent and regular alerts should have different patterns",
            urgentNotification.sound, regularNotification.sound)
    }

    @Test
    fun `notification persistence across app restarts`() {
        val alert = Alert(
            id = "alert123",
            guardName = "John Doe",
            status = AlertStatus.ACTIVE
        )

        notificationService.displayAlertNotification(alert)
        
        // Notification should be ongoing for active alerts
        val notification = notificationService.buildNotification(alert)
        assertTrue("Active alert notification should be ongoing", 
            notification.flags and android.app.Notification.FLAG_ONGOING_EVENT != 0)
    }

    @Test
    fun `notification large text style for long messages`() {
        val longMessage = "This is a very long emergency message that should be displayed " +
                "in expanded form when the user expands the notification to see all details " +
                "about the emergency situation that is currently happening on campus."

        val alert = Alert(
            id = "alert123",
            guardName = "John Doe",
            message = longMessage,
            status = AlertStatus.ACTIVE
        )

        val notification = notificationService.buildNotification(alert)
        
        assertNotNull("Should have big text style for long messages", 
            notification.extras.get("android.bigText"))
    }

    @Test
    fun `notification LED and heads up display`() {
        val alert = Alert(
            id = "alert123",
            guardName = "John Doe",
            status = AlertStatus.ACTIVE
        )

        val notification = notificationService.buildNotification(alert)
        
        assertTrue("Should have LED light", 
            notification.flags and android.app.Notification.FLAG_SHOW_LIGHTS != 0)
        assertEquals("Should have red LED color", 
            android.graphics.Color.RED, notification.ledARGB)
        assertTrue("Should show heads up", 
            notification.priority >= android.app.Notification.PRIORITY_HIGH)
    }
}