@echo off
echo ========================================
echo Deploying Firestore Security Rules
echo ========================================
echo.

echo Deploying rules to Firebase...
firebase deploy --only firestore:rules

if %errorlevel% neq 0 (
    echo.
    echo ERROR: Failed to deploy Firestore rules
    echo.
    echo Please deploy manually:
    echo 1. Go to Firebase Console
    echo 2. Navigate to Firestore Database
    echo 3. Click on "Rules" tab
    echo 4. Copy the content from firestore.rules file
    echo 5. Paste and publish
    echo.
    pause
    exit /b 1
)

echo.
echo ========================================
echo Firestore Rules Deployed Successfully!
echo ========================================
echo.
echo The new rules allow:
echo - Users to create their profile during signup
echo - Users to read/update their own profile
echo - Admins to read all profiles
echo - Guards and Admins to manage alerts
echo.
pause
