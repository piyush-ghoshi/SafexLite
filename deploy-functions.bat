@echo off
echo ========================================
echo Campus Panic Button - Deploy Functions
echo ========================================
echo.

echo Step 1: Installing function dependencies...
cd functions
call npm install
if %errorlevel% neq 0 (
    echo ERROR: Failed to install dependencies
    cd ..
    pause
    exit /b 1
)
cd ..
echo Dependencies installed successfully!
echo.

echo Step 2: Deploying Cloud Functions...
call firebase deploy --only functions
if %errorlevel% neq 0 (
    echo ERROR: Failed to deploy functions
    pause
    exit /b 1
)
echo.

echo Step 3: Deploying Firestore Rules...
call firebase deploy --only firestore:rules
if %errorlevel% neq 0 (
    echo ERROR: Failed to deploy Firestore rules
    pause
    exit /b 1
)
echo.

echo ========================================
echo Deployment Complete!
echo ========================================
echo.
echo Your Cloud Functions are now live!
echo Notifications will now work on all devices.
echo.
echo To view logs:
echo   firebase functions:log
echo.
echo To test:
echo   1. Install app on 2 devices
echo   2. Login on both devices
echo   3. Press panic button on one device
echo   4. Check notification on other device
echo.
pause
