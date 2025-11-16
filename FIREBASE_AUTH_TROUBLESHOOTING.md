# Firebase Authentication Troubleshooting

## "Service is not available" Error Fix

This error typically occurs when Firebase Authentication isn't properly configured. Here's how to fix it:

### Step 1: Enable Firebase Authentication

1. Go to [Firebase Console](https://console.firebase.google.com/project/safexlite)
2. Click **Authentication** in the left sidebar
3. Click **Get started** if you haven't set it up yet
4. Go to **Sign-in method** tab
5. Enable **Email/Password** provider:
   - Click on "Email/Password"
   - Toggle **Enable** to ON
   - Click **Save**

### Step 2: Create Test Users

Since you don't have user registration in the app, create test users manually:

1. Go to **Authentication** → **Users** tab
2. Click **Add user**
3. Create test accounts:
   - **Guard User**: `guard@test.com` / `password123`
   - **Admin User**: `admin@test.com` / `password123`

### Step 3: Add User Profiles to Firestore

After creating users, add their profiles to Firestore:

1. Go to **Firestore Database**
2. Create collection: `users`
3. For each user, create a document with their UID as document ID:

**Guard User Document:**
```json
{
  "email": "guard@test.com",
  "name": "Test Guard",
  "role": "GUARD",
  "campusId": "main-campus",
  "isActive": true,
  "createdAt": "2024-01-01T00:00:00Z"
}
```

**Admin User Document:**
```json
{
  "email": "admin@test.com",
  "name": "Test Admin", 
  "role": "ADMIN",
  "campusId": "main-campus",
  "isActive": true,
  "createdAt": "2024-01-01T00:00:00Z"
}
```

### Step 4: Check Network Connection

Make sure your device has:
- ✅ Internet connection
- ✅ Google Play Services installed
- ✅ Date/time set correctly

### Step 5: Verify SHA-1 Certificate (For Release)

If testing on a physical device with a release build:
1. Generate SHA-1 certificate: `keytool -list -v -keystore ~/.android/debug.keystore -alias androiddebugkey -storepass android -keypass android`
2. Add it to Firebase Console → Project Settings → Your apps → SHA certificate fingerprints

### Step 6: Test Login

Try logging in with:
- **Email**: `guard@test.com` or `admin@test.com`
- **Password**: `password123`
- **Role**: Select Guard or Admin respectively

### Common Issues:

1. **Authentication not enabled**: Enable Email/Password in Firebase Console
2. **No test users**: Create users manually in Firebase Console
3. **Missing user profiles**: Add user documents to Firestore
4. **Network issues**: Check internet connection
5. **Wrong role selected**: Make sure role matches user's actual role in Firestore

### Debug Steps:

1. Check Android logs: `adb logcat | grep Firebase`
2. Verify Firebase project ID in `google-services.json`
3. Ensure package name matches: `com.campus.panicbutton`
4. Test with Firebase Auth emulator for local development

### Quick Fix Commands:

```bash
# Check if Firebase is properly initialized
adb logcat | grep "FirebaseApp"

# Check authentication errors
adb logcat | grep "FirebaseAuth"
```

If the issue persists, the problem is likely in the Firebase Console configuration rather than the app code.