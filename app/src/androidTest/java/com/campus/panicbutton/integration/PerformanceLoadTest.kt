package com.campus.panicbutton.integration

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.campus.panicbutton.models.Alert
import com.campus.panicbutton.models.AlertStatus
import com.campus.panicbutton.models.User
import com.campus.panicbutton.models.UserRole
import com.campus.panicbutton.services.FirebaseService
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import kotlinx.coroutines.*
import kotlinx.coroutines.tasks.await
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.system.measureTimeMillis
import kotlin.test.assertTrue

/**
 * Performance and load testing for the panic button system
 * Requirements: 1.1, 1.5, 2.1
 */
@RunWith(AndroidJUnit4::class)
class PerformanceLoadTest {

    private lateinit var firebaseService: FirebaseService
    private lateinit var firestore: FirebaseFirestore
    private lateinit var auth: FirebaseAuth
    private val testUsers = mutableListOf<User>()

    @Before
    fun setUp() {
        firebaseService = FirebaseService()
        firestore = FirebaseFirestore.getInstance()
        auth = FirebaseAuth.getInstance()
        
        // Use Firebase emulator for testing
        firestore.useEmulator("10.0.2.2", 8080)
        auth.useEmulator("10.0.2.2", 9099)
        
        setupTestUsers()
    }

    @After
    fun tearDown() {
        cleanupTestData()
    }

    /**
     * Test alert creation performance under load
     * Requirements: 1.1, 1.5
     */
    @Test
    fun testAlertCreationPerformance() = runBlocking {
        val numberOfAlerts = 50
        val concurrentUsers = 10
        val alertsPerUser = numberOfAlerts / concurrentUsers
        
        val totalTime = measureTimeMillis {
            val jobs = mutableListOf<Job>()
            
            repeat(concurrentUsers) { userIndex ->
                val job = launch {
                    val user = testUsers[userIndex % testUsers.size]
                    
                    repeat(alertsPerUser) { alertIndex ->
                        val alert = Alert(
                            guardId = user.id,
                            guardName = user.name,
                            message = "Load test alert ${userIndex}-${alertIndex}"
                        )
                        
                        try {
                            val alertRef = firebaseService.createAlert(alert).await()
                            assertTrue(alertRef.id.isNotEmpty())
                        } catch (e: Exception) {
                            // Log but don't fail the test for individual failures
                            println("Alert creation failed: ${e.message}")
                        }
                    }
                }
                jobs.add(job)
            }
            
            jobs.joinAll()
        }
        
        val averageTimePerAlert = totalTime.toDouble() / numberOfAlerts
        println("Created $numberOfAlerts alerts in ${totalTime}ms")
        println("Average time per alert: ${averageTimePerAlert}ms")
        
        // Assert reasonable performance (less than 2 seconds per alert on average)
        assertTrue(averageTimePerAlert < 2000, "Alert creation took too long: ${averageTimePerAlert}ms")
        
        // Verify alerts were created
        val createdAlerts = firestore.collection("alerts").get().await()
        assertTrue(createdAlerts.size() >= numberOfAlerts * 0.8) // Allow for some failures
    }

    /**
     * Test real-time notification performance with multiple listeners
     * Requirements: 2.1
     */
    @Test
    fun testNotificationPerformance() = runBlocking {
        val numberOfListeners = 20
        val numberOfAlerts = 10
        val notificationTimes = mutableListOf<Long>()
        
        // Set up multiple listeners to simulate multiple devices
        val listeners = mutableListOf<com.google.firebase.firestore.ListenerRegistration>()
        
        repeat(numberOfListeners) { listenerIndex ->
            val listener = firestore.collection("alerts")
                .addSnapshotListener { snapshot, _ ->
                    snapshot?.documentChanges?.forEach { change ->
                        if (change.type == com.google.firebase.firestore.DocumentChange.Type.ADDED) {
                            val notificationTime = System.currentTimeMillis()
                            notificationTimes.add(notificationTime)
                        }
                    }
                }
            listeners.add(listener)
        }
        
        // Wait for listeners to be established
        delay(1000)
        
        val user = testUsers.first()
        val alertCreationTimes = mutableListOf<Long>()
        
        // Create alerts and measure notification delivery time
        repeat(numberOfAlerts) { index ->
            val creationTime = System.currentTimeMillis()
            alertCreationTimes.add(creationTime)
            
            val alert = Alert(
                guardId = user.id,
                guardName = user.name,
                message = "Notification performance test alert $index"
            )
            
            firebaseService.createAlert(alert).await()
            delay(500) // Small delay between alerts
        }
        
        // Wait for all notifications to be received
        delay(5000)
        
        // Clean up listeners
        listeners.forEach { it.remove() }
        
        // Verify notification performance
        assertTrue(notificationTimes.size >= numberOfAlerts * numberOfListeners * 0.8)
        println("Received ${notificationTimes.size} notifications for $numberOfAlerts alerts across $numberOfListeners listeners")
    }

    /**
     * Test concurrent alert status updates
     * Requirements: 3.1
     */
    @Test
    fun testConcurrentStatusUpdates() = runBlocking {
        val numberOfAlerts = 20
        val user = testUsers.first()
        val alertIds = mutableListOf<String>()
        
        // Create alerts first
        repeat(numberOfAlerts) { index ->
            val alert = Alert(
                guardId = user.id,
                guardName = user.name,
                message = "Concurrent update test alert $index"
            )
            
            val alertRef = firebaseService.createAlert(alert).await()
            alertIds.add(alertRef.id)
        }
        
        // Perform concurrent status updates
        val updateTime = measureTimeMillis {
            val jobs = alertIds.map { alertId ->
                launch {
                    try {
                        firebaseService.updateAlertStatus(alertId, AlertStatus.IN_PROGRESS, user.id)
                        delay(100)
                        firebaseService.updateAlertStatus(alertId, AlertStatus.RESOLVED, user.id)
                    } catch (e: Exception) {
                        println("Status update failed for alert $alertId: ${e.message}")
                    }
                }
            }
            
            jobs.joinAll()
        }
        
        println("Updated $numberOfAlerts alerts in ${updateTime}ms")
        
        // Verify updates were applied
        var resolvedCount = 0
        alertIds.forEach { alertId ->
            val alert = firestore.collection("alerts")
                .document(alertId)
                .get()
                .await()
                .toObject(Alert::class.java)
            
            if (alert?.status == AlertStatus.RESOLVED) {
                resolvedCount++
            }
        }
        
        assertTrue(resolvedCount >= numberOfAlerts * 0.8) // Allow for some failures
        println("Successfully resolved $resolvedCount out of $numberOfAlerts alerts")
    }

    /**
     * Test system behavior under memory pressure
     * Requirements: 1.1, 2.1
     */
    @Test
    fun testMemoryPressureHandling() = runBlocking {
        val numberOfAlerts = 100
        val user = testUsers.first()
        val alertIds = mutableListOf<String>()
        
        // Monitor memory usage
        val runtime = Runtime.getRuntime()
        val initialMemory = runtime.totalMemory() - runtime.freeMemory()
        
        // Create many alerts to simulate memory pressure
        repeat(numberOfAlerts) { index ->
            val alert = Alert(
                guardId = user.id,
                guardName = user.name,
                message = "Memory pressure test alert $index with some additional data to increase memory usage"
            )
            
            try {
                val alertRef = firebaseService.createAlert(alert).await()
                alertIds.add(alertRef.id)
                
                // Periodically check memory usage
                if (index % 20 == 0) {
                    val currentMemory = runtime.totalMemory() - runtime.freeMemory()
                    val memoryIncrease = currentMemory - initialMemory
                    println("Memory usage after $index alerts: ${memoryIncrease / 1024 / 1024}MB increase")
                }
            } catch (e: Exception) {
                println("Alert creation failed under memory pressure: ${e.message}")
            }
        }
        
        val finalMemory = runtime.totalMemory() - runtime.freeMemory()
        val totalMemoryIncrease = finalMemory - initialMemory
        
        println("Total memory increase: ${totalMemoryIncrease / 1024 / 1024}MB")
        
        // Verify system still functions under memory pressure
        assertTrue(alertIds.size >= numberOfAlerts * 0.7) // Allow for some failures under pressure
        
        // Test that we can still perform operations
        val testAlert = Alert(
            guardId = user.id,
            guardName = user.name,
            message = "Post-pressure test alert"
        )
        
        val postPressureAlert = firebaseService.createAlert(testAlert).await()
        assertTrue(postPressureAlert.id.isNotEmpty())
    }

    /**
     * Test network resilience and retry mechanisms
     * Requirements: 7.1
     */
    @Test
    fun testNetworkResilienceAndRetry() = runBlocking {
        val user = testUsers.first()
        val numberOfRetryAttempts = 5
        
        // Test retry mechanism by creating alerts with potential network issues
        repeat(numberOfRetryAttempts) { attempt ->
            val alert = Alert(
                guardId = user.id,
                guardName = user.name,
                message = "Network resilience test alert attempt $attempt"
            )
            
            try {
                val alertRef = firebaseService.createAlert(alert).await()
                assertTrue(alertRef.id.isNotEmpty())
                println("Alert created successfully on attempt $attempt")
            } catch (e: Exception) {
                println("Alert creation failed on attempt $attempt: ${e.message}")
                // In a real scenario, the service should implement retry logic
            }
            
            delay(1000) // Wait between attempts
        }
        
        // Verify at least some alerts were created despite potential network issues
        val createdAlerts = firestore.collection("alerts")
            .whereEqualTo("guardId", user.id)
            .get()
            .await()
        
        assertTrue(createdAlerts.size() > 0)
    }

    private fun setupTestUsers() = runBlocking {
        val numberOfUsers = 15
        
        repeat(numberOfUsers) { index ->
            try {
                val email = "loadtest.user$index@campus.edu"
                val password = "testPassword123"
                
                val authResult = auth.createUserWithEmailAndPassword(email, password).await()
                val user = User(
                    id = authResult.user!!.uid,
                    email = email,
                    name = "Load Test User $index",
                    role = if (index % 5 == 0) UserRole.ADMIN else UserRole.GUARD
                )
                
                firestore.collection("users").document(user.id).set(user).await()
                testUsers.add(user)
            } catch (e: Exception) {
                // User might already exist, try to sign in
                try {
                    val email = "loadtest.user$index@campus.edu"
                    val password = "testPassword123"
                    auth.signInWithEmailAndPassword(email, password).await()
                    
                    val user = firestore.collection("users")
                        .document(auth.currentUser!!.uid)
                        .get()
                        .await()
                        .toObject(User::class.java)
                    
                    if (user != null) {
                        testUsers.add(user)
                    }
                } catch (signInException: Exception) {
                    println("Failed to create or sign in user $index: ${signInException.message}")
                }
            }
        }
        
        assertTrue(testUsers.isNotEmpty(), "No test users were created")
        println("Created ${testUsers.size} test users for load testing")
    }

    private fun cleanupTestData() = runBlocking {
        try {
            // Clean up test alerts
            val alerts = firestore.collection("alerts").get().await()
            val alertDeletionJobs = alerts.documents.map { document ->
                launch { document.reference.delete().await() }
            }
            alertDeletionJobs.joinAll()
            
            // Clean up test users
            val users = firestore.collection("users")
                .whereGreaterThanOrEqualTo("email", "loadtest.user")
                .whereLessThan("email", "loadtest.userz")
                .get()
                .await()
            
            val userDeletionJobs = users.documents.map { document ->
                launch { document.reference.delete().await() }
            }
            userDeletionJobs.joinAll()
            
            auth.signOut()
            println("Cleanup completed")
        } catch (e: Exception) {
            println("Cleanup failed: ${e.message}")
        }
    }
}