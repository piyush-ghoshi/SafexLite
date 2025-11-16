package com.campus.panicbutton.utils

import android.content.Context
import android.content.SharedPreferences
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.UserRole
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*
import org.mockito.Mock
import org.mockito.Mockito.*
import org.mockito.MockitoAnnotations

class SecurityManagerTest {

    @Mock
    private lateinit var mockContext: Context
    
    @Mock
    private lateinit var mockPrefs: SharedPreferences
    
    @Mock
    private lateinit var mockEditor: SharedPreferences.Editor
    
    private lateinit var securityManager: SecurityManager
    
    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        
        `when`(mockContext.getSharedPreferences(anyString(), anyInt())).thenReturn(mockPrefs)
        `when`(mockPrefs.edit()).thenReturn(mockEditor)
        `when`(mockEditor.putLong(anyString(), anyLong())).thenReturn(mockEditor)
        `when`(mockEditor.putInt(anyString(), anyInt())).thenReturn(mockEditor)
        `when`(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor)
        `when`(mockEditor.remove(anyString())).thenReturn(mockEditor)
        
        securityManager = SecurityManager(mockContext)
    }
    
    @Test
    fun `canCreateAlert allows creation when no previous alert`() {
        `when`(mockPrefs.getLong(anyString(), anyLong())).thenReturn(0L)
        
        val result = securityManager.canCreateAlert("user123")
        
        assertTrue(result.canCreate)
        assertEquals(0L, result.remainingCooldownMs)
    }
    
    @Test
    fun `canCreateAlert blocks creation during cooldown period`() {
        val currentTime = System.currentTimeMillis()
        val lastAlertTime = currentTime - 3000L // 3 seconds ago
        
        `when`(mockPrefs.getLong(anyString(), anyLong())).thenReturn(lastAlertTime)
        
        val result = securityManager.canCreateAlert("user123")
        
        assertFalse(result.canCreate)
        assertTrue(result.remainingCooldownMs > 0)
        assertTrue(result.remainingCooldownMs <= 2000L) // Should be around 2 seconds remaining
    }
    
    @Test
    fun `canCreateAlert allows creation after cooldown expires`() {
        val currentTime = System.currentTimeMillis()
        val lastAlertTime = currentTime - 6000L // 6 seconds ago (past cooldown)
        
        `when`(mockPrefs.getLong(anyString(), anyLong())).thenReturn(lastAlertTime)
        
        val result = securityManager.canCreateAlert("user123")
        
        assertTrue(result.canCreate)
        assertEquals(0L, result.remainingCooldownMs)
    }
    
    @Test
    fun `recordAlertCreation saves timestamp`() {
        securityManager.recordAlertCreation("user123")
        
        verify(mockEditor).putLong(contains("user123"), anyLong())
        verify(mockEditor).apply()
    }
    
    @Test
    fun `canUpdateStatus allows updates when under rate limit`() {
        `when`(mockPrefs.getString(anyString(), anyString())).thenReturn("[]")
        
        val result = securityManager.canUpdateStatus("user123")
        
        assertTrue(result.canUpdate)
        assertEquals(10, result.remainingUpdates) // MAX_STATUS_UPDATES_PER_MINUTE
    }
    
    @Test
    fun `recordFailedLogin increments counter`() {
        `when`(mockPrefs.getInt(anyString(), anyInt())).thenReturn(2)
        
        securityManager.recordFailedLogin("test@example.com")
        
        verify(mockEditor).putInt(anyString(), eq(3))
        verify(mockEditor).apply()
    }
    
    @Test
    fun `recordFailedLogin triggers lockout after max attempts`() {
        `when`(mockPrefs.getInt(anyString(), anyInt())).thenReturn(4) // One less than max
        
        securityManager.recordFailedLogin("test@example.com")
        
        verify(mockEditor).putInt(anyString(), eq(5)) // Should reach max
        verify(mockEditor).putLong(anyString(), anyLong()) // Should set lockout time
        verify(mockEditor).apply()
    }
    
    @Test
    fun `isLoginLocked returns false when no failed attempts`() {
        `when`(mockPrefs.getInt(anyString(), anyInt())).thenReturn(0)
        
        val result = securityManager.isLoginLocked("test@example.com")
        
        assertFalse(result.isLocked)
        assertEquals(5, result.remainingAttempts)
    }
    
    @Test
    fun `isLoginLocked returns true during lockout period`() {
        `when`(mockPrefs.getInt(anyString(), anyInt())).thenReturn(5) // Max attempts
        `when`(mockPrefs.getLong(anyString(), anyLong())).thenReturn(System.currentTimeMillis() - 60000L) // 1 minute ago
        
        val result = securityManager.isLoginLocked("test@example.com")
        
        assertTrue(result.isLocked)
        assertTrue(result.remainingLockoutMs > 0)
    }
    
    @Test
    fun `clearFailedLogins removes lockout data`() {
        securityManager.clearFailedLogins("test@example.com")
        
        verify(mockEditor, times(2)).remove(anyString()) // Should remove both count and lockout time
        verify(mockEditor).apply()
    }
    
    @Test
    fun `validateAlertPermission allows guard to create alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.GUARD, 
            AlertOperation.CREATE, 
            AlertStatus.ACTIVE
        )
        
        assertTrue(result.hasPermission)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission allows admin to create alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.ADMIN, 
            AlertOperation.CREATE, 
            AlertStatus.ACTIVE
        )
        
        assertTrue(result.hasPermission)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission allows guard to accept active alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.GUARD, 
            AlertOperation.ACCEPT, 
            AlertStatus.ACTIVE
        )
        
        assertTrue(result.hasPermission)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission blocks guard from accepting non-active alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.GUARD, 
            AlertOperation.ACCEPT, 
            AlertStatus.IN_PROGRESS
        )
        
        assertFalse(result.hasPermission)
        assertEquals("Only active alerts can be accepted", result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission allows guard to resolve in-progress alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.GUARD, 
            AlertOperation.RESOLVE, 
            AlertStatus.IN_PROGRESS
        )
        
        assertTrue(result.hasPermission)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission blocks guard from resolving non-in-progress alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.GUARD, 
            AlertOperation.RESOLVE, 
            AlertStatus.ACTIVE
        )
        
        assertFalse(result.hasPermission)
        assertEquals("Only in-progress alerts can be resolved", result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission allows admin to close alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.ADMIN, 
            AlertOperation.CLOSE, 
            AlertStatus.RESOLVED
        )
        
        assertTrue(result.hasPermission)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission blocks guard from closing alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.GUARD, 
            AlertOperation.CLOSE, 
            AlertStatus.RESOLVED
        )
        
        assertFalse(result.hasPermission)
        assertEquals("Only admins can close alerts", result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission allows admin to reopen closed alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.ADMIN, 
            AlertOperation.REOPEN, 
            AlertStatus.CLOSED
        )
        
        assertTrue(result.hasPermission)
        assertNull(result.errorMessage)
    }
    
    @Test
    fun `validateAlertPermission blocks admin from reopening non-closed alert`() {
        val result = securityManager.validateAlertPermission(
            UserRole.ADMIN, 
            AlertOperation.REOPEN, 
            AlertStatus.ACTIVE
        )
        
        assertFalse(result.hasPermission)
        assertEquals("Only closed alerts can be reopened", result.errorMessage)
    }
    
    @Test
    fun `validateStatusTransition validates correct transitions`() {
        assertTrue(securityManager.validateStatusTransition(AlertStatus.ACTIVE, AlertStatus.IN_PROGRESS))
        assertTrue(securityManager.validateStatusTransition(AlertStatus.ACTIVE, AlertStatus.CLOSED))
        assertTrue(securityManager.validateStatusTransition(AlertStatus.IN_PROGRESS, AlertStatus.RESOLVED))
        assertTrue(securityManager.validateStatusTransition(AlertStatus.IN_PROGRESS, AlertStatus.CLOSED))
        assertTrue(securityManager.validateStatusTransition(AlertStatus.RESOLVED, AlertStatus.CLOSED))
    }
    
    @Test
    fun `validateStatusTransition rejects invalid transitions`() {
        assertFalse(securityManager.validateStatusTransition(AlertStatus.IN_PROGRESS, AlertStatus.ACTIVE))
        assertFalse(securityManager.validateStatusTransition(AlertStatus.RESOLVED, AlertStatus.ACTIVE))
        assertFalse(securityManager.validateStatusTransition(AlertStatus.RESOLVED, AlertStatus.IN_PROGRESS))
        assertFalse(securityManager.validateStatusTransition(AlertStatus.CLOSED, AlertStatus.ACTIVE))
        assertFalse(securityManager.validateStatusTransition(AlertStatus.CLOSED, AlertStatus.IN_PROGRESS))
        assertFalse(securityManager.validateStatusTransition(AlertStatus.CLOSED, AlertStatus.RESOLVED))
    }
}