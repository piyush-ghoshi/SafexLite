# Create User Profiles in Firestore

## The Problem
Your Firebase Authentication is working, but the app can't find user profiles in Firestore Database. You need to create user documents that match your authenticated users.

## Step 1: Go to Firestore Database
1. In Firebase Console, click **Firestore Database**
2. If not created yet, click **Create database** → **Production mode**

## Step 2: Create Users Collection
1. Click **Start collection**
2. Collection ID: `users`
3. Click **Next**

## Step 3: Create Guard User Profile
1. **Document ID**: Use the User UID from Authentication tab
   - Go to Authentication → Users
   - Copy the User UID for `guard@test.com` (looks like: `5YfSzUeYKjYjRbMuQrPF5...`)

2. **Document fields**:
```
id: "5YfSzUeYKjYjRbMuQrPF5..." (same as document ID)
email: "guard@test.com"
name: "Test Guard"
role: "GUARD"
campusId: "main-campus"
isActive: true
createdAt: (timestamp) - current date/time
```

## Step 4: Create Admin User Profile  
1. **Document ID**: Use the User UID for your second user
   - Copy UID from Authentication tab

2. **Document fields**:
```
id: "97qzmWMEEVBw1oW7NzFN..." (same as document ID)  
email: "admin@test.com" (or whatever email you used)
name: "Test Admin"
role: "ADMIN"
campusId: "main-campus"
isActive: true
createdAt: (timestamp) - current date/time
```

## Step 5: Field Types in Firestore
When creating fields, use these types:
- `id`: string
- `email`: string  
- `name`: string
- `role`: string (exactly "GUARD" or "ADMIN")
- `campusId`: string
- `isActive`: boolean (true)
- `createdAt`: timestamp

## Step 6: Test Login
After creating the user profiles:
1. Try logging in with `guard@test.com` / `password@123`
2. Select "Security Guard" role
3. Should now work!

## Important Notes:
- Document ID MUST match the User UID from Authentication
- Role field MUST be exactly "GUARD" or "ADMIN" (case sensitive)
- Both users need profiles in Firestore to login successfully

## Quick Fix Alternative:
If you want to create an admin user quickly:
1. Change one of your existing users to admin role
2. Update the email in Authentication if needed
3. Create the Firestore profile with role: "ADMIN"