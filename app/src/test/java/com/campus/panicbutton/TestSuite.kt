package com.campus.panicbutton

import com.campus.panicbutton.integration.FirebaseIntegrationTest
import com.campus.panicbutton.models.*
import com.campus.panicbutton.security.RoleBasedAccessTest
import com.campus.panicbutton.services.*
import com.campus.panicbutton.utils.ValidationUtilsTest
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Test Suite for Campus Panic Button App
 * 
 * This suite runs all unit tests and integration tests.
 * To run: ./gradlew testDebugUnitTest
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // Model Tests
    AlertTest::class,
    UserTest::class,
    CampusBlockTest::class,
    
    // Service Tests
    FirebaseServiceTest::class,
    LocationServiceTest::class,
    LocationServiceMockTest::class,
    NotificationServiceTest::class,
    
    // Utility Tests
    ValidationUtilsTest::class,
    
    // Security Tests
    RoleBasedAccessTest::class,
    
    // Integration Tests
    FirebaseIntegrationTest::class
)
class TestSuite

/**
 * Test configuration and utilities
 */
object TestConfig {
    const val FIREBASE_EMULATOR_HOST = "10.0.2.2"
    const val FIREBASE_EMULATOR_PORT = 8080
    const val TEST_TIMEOUT_MS = 10000L
    
    // Test data
    val TEST_GUARD_USER = User(
        id = "test_guard_123",
        email = "guard@test.campus.edu",
        name = "Test Guard",
        role = UserRole.GUARD,
        isActive = true
    )
    
    val TEST_ADMIN_USER = User(
        id = "test_admin_123",
        email = "admin@test.campus.edu",
        name = "Test Admin",
        role = UserRole.ADMIN,
        isActive = true
    )
    
    val TEST_CAMPUS_BLOCKS = listOf(
        CampusBlock(
            id = "main_building",
            name = "Main Building",
            description = "Primary academic building",
            coordinates = com.google.firebase.firestore.GeoPoint(40.7128, -74.0060),
            radius = 50.0
        ),
        CampusBlock(
            id = "library",
            name = "University Library",
            description = "Central library",
            coordinates = com.google.firebase.firestore.GeoPoint(40.7130, -74.0062),
            radius = 30.0
        ),
        CampusBlock(
            id = "student_center",
            name = "Student Center",
            description = "Student activities building",
            coordinates = com.google.firebase.firestore.GeoPoint(40.7125, -74.0058),
            radius = 40.0
        )
    )
    
    fun createTestAlert(
        guardId: String = TEST_GUARD_USER.id,
        guardName: String = TEST_GUARD_USER.name,
        status: AlertStatus = AlertStatus.ACTIVE,
        message: String? = "Test emergency alert"
    ): Alert {
        return Alert(
            id = "test_alert_${System.currentTimeMillis()}",
            guardId = guardId,
            guardName = guardName,
            timestamp = com.google.firebase.Timestamp.now(),
            location = TEST_CAMPUS_BLOCKS.first(),
            message = message,
            status = status
        )
    }
}

/**
 * Base test class with common setup
 */
abstract class BaseTest {
    protected val testConfig = TestConfig
    
    protected fun createMockLocation(
        latitude: Double = 40.7128,
        longitude: Double = -74.0060,
        accuracy: Float = 10.0f
    ): android.location.Location {
        val location = android.location.Location("test")
        location.latitude = latitude
        location.longitude = longitude
        location.accuracy = accuracy
        location.time = System.currentTimeMillis()
        return location
    }
    
    protected fun waitForAsyncOperation(timeoutMs: Long = TestConfig.TEST_TIMEOUT_MS) {
        Thread.sleep(timeoutMs)
    }
}