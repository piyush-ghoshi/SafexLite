@echo off
echo ========================================
echo Campus Panic Button Integration Tests
echo ========================================

echo.
echo Starting Firebase Emulator Suite...
start /B firebase emulators:start --only firestore,auth,functions

echo Waiting for emulators to start...
timeout /t 10 /nobreak > nul

echo.
echo Running Integration Test Suite...
echo ========================================

echo.
echo 1. Running Comprehensive Integration Tests...
call gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.ComprehensiveIntegrationTest

if %ERRORLEVEL% neq 0 (
    echo ERROR: Comprehensive Integration Tests failed!
    goto :error
)

echo.
echo 2. Running Cloud Functions Integration Tests...
call gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.CloudFunctionsIntegrationTest

if %ERRORLEVEL% neq 0 (
    echo ERROR: Cloud Functions Integration Tests failed!
    goto :error
)

echo.
echo 3. Running Performance Load Tests...
call gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.PerformanceLoadTest

if %ERRORLEVEL% neq 0 (
    echo ERROR: Performance Load Tests failed!
    goto :error
)

echo.
echo 4. Running Offline Sync Integration Tests...
call gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.OfflineSyncIntegrationTest

if %ERRORLEVEL% neq 0 (
    echo ERROR: Offline Sync Integration Tests failed!
    goto :error
)

echo.
echo 5. Running Role-Based Access Integration Tests...
call gradlew connectedAndroidTest -Pandroid.testInstrumentationRunnerArguments.class=com.campus.panicbutton.integration.RoleBasedAccessIntegrationTest

if %ERRORLEVEL% neq 0 (
    echo ERROR: Role-Based Access Integration Tests failed!
    goto :error
)

echo.
echo ========================================
echo ALL INTEGRATION TESTS PASSED!
echo ========================================

echo.
echo Generating test reports...
call gradlew createDebugCoverageReport

echo.
echo Test reports available at:
echo - app/build/reports/androidTests/connected/index.html
echo - app/build/reports/coverage/debug/index.html

echo.
echo Stopping Firebase Emulator...
taskkill /F /IM node.exe /T > nul 2>&1

echo.
echo Integration testing completed successfully!
goto :end

:error
echo.
echo ========================================
echo INTEGRATION TESTS FAILED!
echo ========================================
echo.
echo Stopping Firebase Emulator...
taskkill /F /IM node.exe /T > nul 2>&1
echo.
echo Check the test reports for details:
echo - app/build/reports/androidTests/connected/index.html
exit /b 1

:end
pause