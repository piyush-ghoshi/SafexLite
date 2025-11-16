package com.campus.panicbutton.integration

import org.junit.runner.RunWith
import org.junit.runners.Suite

/**
 * Comprehensive integration test suite for final testing
 * Requirements: 1.1, 1.5, 2.1, 3.1, 4.1, 7.1
 */
@RunWith(Suite::class)
@Suite.SuiteClasses(
    ComprehensiveIntegrationTest::class,
    CloudFunctionsIntegrationTest::class,
    PerformanceLoadTest::class,
    OfflineSyncIntegrationTest::class,
    RoleBasedAccessIntegrationTest::class
)
class IntegrationTestSuite