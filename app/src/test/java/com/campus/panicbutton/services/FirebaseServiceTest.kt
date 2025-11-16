package com.campus.panicbutton.services

import com.campus.panicbutton.models.*
import com.google.android.gms.tasks.Task
import com.google.android.gms.tasks.Tasks
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.firestore.*
import com.google.firebase.Timestamp
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.*
import java.util.Date

class FirebaseServiceTest {

    @Mock
    private lateinit var mockFirestore: FirebaseFirestore
    
    @Mock
    private lateinit var mockAuth: FirebaseAuth
    
    @Mock
    private lateinit var mockUser: FirebaseUser
    
    @Mock
    private lateinit var mockCollection: CollectionReference
    
    @Mock
    private lateinit var mockDocument: DocumentReference
    
    @Mock
    private lateinit var mockDocumentSnapshot: DocumentSnapshot
    
    @Mock
    private lateinit var mockQuery: Query
    
    private lateinit var firebaseService: FirebaseService

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        firebaseService = FirebaseService()
        
        // Use reflection to set private fields for testing
        val firestoreField = FirebaseService::class.java.getDeclaredField("firestore")
        firestoreField.isAccessible = true
        firestoreField.set(firebaseService, mockFirestore)
        
        val authField = FirebaseService::class.java.getDeclaredField("auth")
        authField.isAccessible = true
        authField.set(firebaseService, mockAuth)
    }

    @Test
    fun `createAlert success`() {
        val alert = Alert(
            guardId = "guard123",
            guardName = "John Doe",
            timestamp = Timestamp(Date()),
            message = "Emergency"
        )

        whenever(mockFirestore.collection("alerts")).thenReturn(mockCollection)
        whenever(mockCollection.add(any())).thenReturn(Tasks.forResult(mockDocument))
        whenever(mockDocument.id).thenReturn("alert123")

        val result = firebaseService.createAlert(alert)
        
        assertTrue(result.isSuccessful)
        verify(mockCollection).add(any())
    }

    @Test
    fun `createAlert failure`() {
        val alert = Alert(
            guardId = "guard123",
            guardName = "John Doe"
        )
        val exception = Exception("Network error")

        whenever(mockFirestore.collection("alerts")).thenReturn(mockCollection)
        whenever(mockCollection.add(any())).thenReturn(Tasks.forException(exception))

        val result = firebaseService.createAlert(alert)
        
        assertFalse(result.isSuccessful)
        assertEquals(exception, result.exception)
    }

    @Test
    fun `updateAlertStatus success`() {
        val alertId = "alert123"
        val status = AlertStatus.IN_PROGRESS
        val updates = mapOf(
            "status" to status.name,
            "acceptedBy" to "guard456",
            "acceptedAt" to FieldValue.serverTimestamp()
        )

        whenever(mockFirestore.collection("alerts")).thenReturn(mockCollection)
        whenever(mockCollection.document(alertId)).thenReturn(mockDocument)
        whenever(mockDocument.update(updates)).thenReturn(Tasks.forResult(null))

        val result = firebaseService.updateAlertStatus(alertId, status, "guard456")
        
        assertTrue(result.isSuccessful)
        verify(mockDocument).update(updates)
    }

    @Test
    fun `getUserRole success`() {
        val userId = "user123"
        val userData = mapOf("role" to "ADMIN")

        whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
        whenever(mockCollection.document(userId)).thenReturn(mockDocument)
        whenever(mockDocument.get()).thenReturn(Tasks.forResult(mockDocumentSnapshot))
        whenever(mockDocumentSnapshot.exists()).thenReturn(true)
        whenever(mockDocumentSnapshot.getString("role")).thenReturn("ADMIN")

        val result = firebaseService.getUserRole(userId)
        
        assertTrue(result.isSuccessful)
        assertEquals(UserRole.ADMIN, result.result)
    }

    @Test
    fun `getUserRole user not found`() {
        val userId = "nonexistent"

        whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
        whenever(mockCollection.document(userId)).thenReturn(mockDocument)
        whenever(mockDocument.get()).thenReturn(Tasks.forResult(mockDocumentSnapshot))
        whenever(mockDocumentSnapshot.exists()).thenReturn(false)

        val result = firebaseService.getUserRole(userId)
        
        assertFalse(result.isSuccessful)
        assertNotNull(result.exception)
    }

    @Test
    fun `getAllAlerts returns query`() {
        whenever(mockFirestore.collection("alerts")).thenReturn(mockCollection)
        whenever(mockCollection.orderBy("timestamp", Query.Direction.DESCENDING)).thenReturn(mockQuery)

        val result = firebaseService.getAllAlerts()
        
        assertEquals(mockQuery, result)
        verify(mockCollection).orderBy("timestamp", Query.Direction.DESCENDING)
    }

    @Test
    fun `getActiveAlerts returns filtered query`() {
        whenever(mockFirestore.collection("alerts")).thenReturn(mockCollection)
        whenever(mockCollection.whereIn("status", listOf("ACTIVE", "IN_PROGRESS"))).thenReturn(mockQuery)
        whenever(mockQuery.orderBy("timestamp", Query.Direction.DESCENDING)).thenReturn(mockQuery)

        val result = firebaseService.getActiveAlerts()
        
        assertEquals(mockQuery, result)
        verify(mockCollection).whereIn("status", listOf("ACTIVE", "IN_PROGRESS"))
    }

    @Test
    fun `getCurrentUser returns authenticated user`() {
        whenever(mockAuth.currentUser).thenReturn(mockUser)
        whenever(mockUser.uid).thenReturn("user123")

        val result = firebaseService.getCurrentUser()
        
        assertEquals(mockUser, result)
        assertEquals("user123", result?.uid)
    }

    @Test
    fun `getCurrentUser returns null when not authenticated`() {
        whenever(mockAuth.currentUser).thenReturn(null)

        val result = firebaseService.getCurrentUser()
        
        assertNull(result)
    }

    @Test
    fun `updateFcmToken success`() {
        val userId = "user123"
        val token = "fcm_token_456"
        val updates = mapOf(
            "fcmToken" to token,
            "lastSeen" to FieldValue.serverTimestamp()
        )

        whenever(mockFirestore.collection("users")).thenReturn(mockCollection)
        whenever(mockCollection.document(userId)).thenReturn(mockDocument)
        whenever(mockDocument.update(updates)).thenReturn(Tasks.forResult(null))

        val result = firebaseService.updateFcmToken(userId, token)
        
        assertTrue(result.isSuccessful)
        verify(mockDocument).update(updates)
    }
}