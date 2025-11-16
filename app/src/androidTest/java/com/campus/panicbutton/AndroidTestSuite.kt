package com.campus.panicbutton

import com.campus.panicbutton.e2e.EndToEndFlowTest
import com.campus.panicbutton.ui.*
import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Android Test Suite for Campus Panic Button App
 * 
 * This suite runs all UI tests and end-to-end tests.
 * To run: ./gradlew connectedAndroidTest
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    // UI Tests
    LoginActivityTest::class,
    GuardDashboardTest::class,
    AlertFlowTest::class,
    
    // End-to-End Tests
    EndToEndFlowTest::class
)
class AndroidTestSuite

/**
 * Base class for Android UI tests
 */
abstract class BaseUITest {
    
    protected fun waitForElement(timeoutMs: Long = 5000) {
        Thread.sleep(timeoutMs)
    }
    
    protected fun typeTextSafely(text: String): String {
        // Remove any characters that might cause issues in UI tests
        return text.replace("\n", " ").trim()
    }
    
    companion object {
        const val TEST_EMAIL_GUARD = "guard@test.campus.edu"
        const val TEST_EMAIL_ADMIN = "admin@test.campus.edu"
        const val TEST_PASSWORD = "test123"
        const val DEFAULT_TIMEOUT = 5000L
    }
}