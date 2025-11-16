# Campus Panic Button - Testing Documentation

## Overview

This document describes the comprehensive testing suite for the Campus Panic Button Android application. The testing strategy covers unit tests, integration tests, UI tests, and end-to-end scenarios to ensure the reliability and security of the emergency response system.

## Test Structure

### Unit Tests (`app/src/test/`)

#### Model Tests
- **AlertTest.kt**: Tests Alert data model validation and properties
- **UserTest.kt**: Tests User data model and role validation
- **CampusBlockTest.kt**: Tests CampusBlock model and coordinate validation

#### Service Tests
- **FirebaseServiceTest.kt**: Tests Firebase operations with mocked dependencies
- **LocationServiceTest.kt**: Tests location detection and campus block mapping
- **LocationServiceMockTest.kt**: Tests location services with comprehensive mocking
- **NotificationServiceTest.kt**: Tests FCM notification handling and display

#### Utility Tests
- **ValidationUtilsTest.kt**: Tests input validation and data sanitization

#### Security Tests
- **RoleBasedAccessTest.kt**: Tests role-based access control and security measures

#### Integration Tests
- **FirebaseIntegrationTest.kt**: Tests Firebase operations using emulator

### Android Tests (`app/src/androidTest/`)

#### UI Tests
- **LoginActivityTest.kt**: Tests login interface and authentication flow
- **GuardDashboardTest.kt**: Tests guard dashboard functionality
- **AlertFlowTest.kt**: Tests alert creation, acceptance, and resolution flows

#### End-to-End Tests
- **EndToEndFlowTest.kt**: Tests complete user workflows and system integration

## Running Tests

### Prerequisites

1. **Firebase Emulator Setup** (for integration tests):
   ```bash
   npm install -g firebase-tools
   firebase login
   firebase init emulators
   firebase emulators:start --only firestore
   ```

2. **Android Device/Emulator** (for UI tests):
   - Physical device with USB debugging enabled, OR
   - Android emulator running API level 24+

### Running Unit Tests

```bash
# Run all unit tests
./gradlew testDebugUnitTest

# Run specific test class
./gradlew testDebugUnitTest --tests "*.AlertTest"

# Run tests with coverage
./gradlew testDebugUnitTestCoverage
```

### Running Integration Tests

```bash
# Start Firebase emulator first
firebase emulators:start --only firestore

# Run integration tests
./gradlew testDebugUnitTest --tests "*IntegrationTest*"
```

### Running Android UI Tests

```bash
# Run all UI tests
./gradlew connectedAndroidTest

# Run specific UI test
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.ui.LoginActivityTest
```

### Running Complete Test Suite

```bash
# Run all tests
./gradlew check
```

## Test Coverage

### Requirements Coverage

The test suite covers all requirements from the specification:

#### Requirement 1.1 (Emergency Alert System)
- ✅ Alert creation with panic button
- ✅ Location detection and mapping
- ✅ Optional message handling
- ✅ Firebase storage
- ✅ Cloud function triggers

#### Requirement 2.1 (Real-time Notifications)
- ✅ FCM notification delivery
- ✅ Notification content and actions
- ✅ Background notification handling
- ✅ Multiple alert notifications

#### Requirement 3.1 (Alert Management for Guards)
- ✅ Alert acceptance flow
- ✅ Status updates and tracking
- ✅ Real-time synchronization
- ✅ Alert resolution

#### Requirement 6.3 (Role-Based Authentication)
- ✅ User authentication
- ✅ Role validation
- ✅ Access control enforcement
- ✅ Session management

### Code Coverage Targets

- **Unit Tests**: 80% line coverage minimum
- **Integration Tests**: All Firebase operations
- **UI Tests**: Critical user paths
- **Security Tests**: All access control points

## Test Data Management

### Test Users
```kotlin
// Guard user for testing
val TEST_GUARD = User(
    id = "test_guard_123",
    email = "guard@test.campus.edu",
    name = "Test Guard",
    role = UserRole.GUARD
)

// Admin user for testing
val TEST_ADMIN = User(
    id = "test_admin_123", 
    email = "admin@test.campus.edu",
    name = "Test Admin",
    role = UserRole.ADMIN
)
```

### Test Campus Blocks
```kotlin
val TEST_BLOCKS = listOf(
    CampusBlock(
        id = "main_building",
        name = "Main Building",
        coordinates = GeoPoint(40.7128, -74.0060),
        radius = 50.0
    )
    // Additional test blocks...
)
```

## Mocking Strategy

### Firebase Services
- Use Mockito for unit tests
- Use Firebase Emulator for integration tests
- Mock authentication and Firestore operations

### Location Services
- Mock FusedLocationProviderClient
- Simulate GPS accuracy scenarios
- Test location permission handling

### Notification Services
- Mock NotificationManager
- Test notification creation and display
- Verify notification actions and intents

## Security Testing

### Input Validation
- Test XSS prevention
- Test SQL injection prevention
- Test path traversal prevention
- Test command injection prevention

### Access Control
- Test role-based permissions
- Test session validation
- Test rate limiting
- Test audit logging

### Data Sanitization
- Test HTML encoding
- Test special character handling
- Test length validation
- Test format validation

## Performance Testing

### Location Services
- Test GPS acquisition time
- Test battery usage optimization
- Test location accuracy validation

### Firebase Operations
- Test query performance
- Test concurrent operations
- Test offline synchronization

### UI Responsiveness
- Test loading states
- Test error handling
- Test network timeouts

## Continuous Integration

### GitHub Actions / CI Pipeline
```yaml
# Example CI configuration
- name: Run Unit Tests
  run: ./gradlew testDebugUnitTest

- name: Run Integration Tests  
  run: |
    firebase emulators:exec --only firestore "./gradlew testDebugUnitTest --tests '*IntegrationTest*'"

- name: Run UI Tests
  run: ./gradlew connectedAndroidTest
```

### Test Reports
- JUnit XML reports generated in `app/build/test-results/`
- Coverage reports in `app/build/reports/coverage/`
- Android test reports in `app/build/reports/androidTests/`

## Troubleshooting

### Common Issues

1. **Firebase Emulator Connection**
   - Ensure emulator is running on correct port
   - Check network connectivity to emulator
   - Verify emulator configuration

2. **Location Permission Tests**
   - Grant permissions in test setup
   - Use @Rule GrantPermissionRule
   - Mock location services for consistent results

3. **UI Test Flakiness**
   - Add proper wait conditions
   - Use IdlingResource for async operations
   - Increase timeout values if needed

4. **Network-dependent Tests**
   - Mock network operations
   - Use test doubles for external services
   - Implement retry mechanisms

### Debug Tips

1. **Enable Test Logging**
   ```kotlin
   @Before
   fun enableLogging() {
       Log.d("TEST", "Starting test: ${testName.methodName}")
   }
   ```

2. **Screenshot on Failure**
   ```kotlin
   @Rule
   val screenshotRule = ScreenshotTestRule()
   ```

3. **Test Data Cleanup**
   ```kotlin
   @After
   fun cleanup() {
       clearTestData()
   }
   ```

## Best Practices

1. **Test Isolation**: Each test should be independent
2. **Descriptive Names**: Use clear, descriptive test method names
3. **AAA Pattern**: Arrange, Act, Assert structure
4. **Mock External Dependencies**: Don't rely on external services
5. **Test Edge Cases**: Include boundary conditions and error scenarios
6. **Maintain Test Data**: Keep test data current and realistic
7. **Regular Test Review**: Review and update tests with code changes

## Metrics and Reporting

### Test Metrics to Track
- Test execution time
- Test pass/fail rates
- Code coverage percentages
- Flaky test identification
- Performance benchmarks

### Reporting Tools
- JaCoCo for coverage reports
- Allure for detailed test reporting
- Firebase Test Lab for device testing
- SonarQube for code quality analysis