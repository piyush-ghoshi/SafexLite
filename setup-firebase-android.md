# Setup Android App in SafexLite Firebase Project

## Step 1: Add Android App to Firebase Console

1. Go to: https://console.firebase.google.com/project/safexlite
2. Click "Add app" → Android (🤖)
3. **Android package name**: `com.campus.panicbutton`
4. **App nickname**: `Campus Panic Button` or `SafexLite Mobile`
5. Click "Register app"
6. **Download the `google-services.json` file**

## Step 2: Replace Configuration File

After downloading, replace the current placeholder file:
- Replace: `app/google-services.json`
- With: Your downloaded `google-services.json`

## Step 3: Enable Required Services

In your SafexLite Firebase project, enable:

### Authentication
- Go to Authentication → Sign-in method
- Enable **Email/Password**

### Firestore Database
- Should already be enabled
- If not: Create database in production mode

### Cloud Messaging (FCM)
- Should be automatically enabled
- Check: Cloud Messaging section

### Cloud Functions
- Should already be enabled for SafexLite
- If not: Upgrade to Blaze plan

## Step 4: Deploy Functions and Rules

```bash
# Deploy cloud functions
firebase deploy --only functions

# Deploy Firestore rules
firebase deploy --only firestore:rules
```

## Step 5: Test the Setup

```bash
# Build the app
./gradlew assembleDebug

# Run tests
./gradlew test
```

## Important Notes

- The current `google-services.json` is a placeholder
- You must download the real one from Firebase Console
- Package name must be exactly: `com.campus.panicbutton`
- This will be part of your existing SafexLite project