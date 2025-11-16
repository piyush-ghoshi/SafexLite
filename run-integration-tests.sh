#!/bin/bash

echo "========================================"
echo "Campus Panic Button Integration Tests"
echo "========================================"

# Function to check if command succeeded
check_result() {
    if [ $? -ne 0 ]; then
        echo "ERROR: $1 failed!"
        echo "Stopping Firebase Emulator..."
        pkill -f "firebase emulators"
        echo "Check the test reports for details:"
        echo "- app/build/reports/androidTests/connected/index.html"
        exit 1
    fi
}

echo ""
echo "Starting Firebase Emulator Suite..."
firebase emulators:start --only firestore,auth,functions &
EMULATOR_PID=$!

echo "Waiting for emulators to start..."
sleep 10

echo ""
echo "Running Integration Test Suite..."
echo "========================================"

echo ""
echo "1. Running Comprehensive Integration Tests..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.ComprehensiveIntegrationTest
check_result "Comprehensive Integration Tests"

echo ""
echo "2. Running Cloud Functions Integration Tests..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.CloudFunctionsIntegrationTest
check_result "Cloud Functions Integration Tests"

echo ""
echo "3. Running Performance Load Tests..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.PerformanceLoadTest
check_result "Performance Load Tests"

echo ""
echo "4. Running Offline Sync Integration Tests..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.OfflineSyncIntegrationTest
check_result "Offline Sync Integration Tests"

echo ""
echo "5. Running Role-Based Access Integration Tests..."
./gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.RoleBasedAccessIntegrationTest
check_result "Role-Based Access Integration Tests"

echo ""
echo "========================================"
echo "ALL INTEGRATION TESTS PASSED!"
echo "========================================"

echo ""
echo "Generating test reports..."
./gradlew createDebugCoverageReport

echo ""
echo "Test reports available at:"
echo "- app/build/reports/androidTests/connected/index.html"
echo "- app/build/reports/coverage/debug/index.html"

echo ""
echo "Stopping Firebase Emulator..."
kill $EMULATOR_PID 2>/dev/null

echo ""
echo "Integration testing completed successfully!"