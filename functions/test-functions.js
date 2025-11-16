/**
 * Test script for Firebase Cloud Functions
 * Run this script to test the notification functions locally
 * 
 * Usage:
 * 1. Start Firebase emulators: npm run serve
 * 2. In another terminal: node test-functions.js
 */

const admin = require('firebase-admin');

// Initialize Firebase Admin SDK for testing
// Make sure to set FIREBASE_AUTH_EMULATOR_HOST=localhost:9099 if using auth emulator
admin.initializeApp({
  projectId: 'campus-panic-button-test',
  // Use emulator for testing
});

const db = admin.firestore();

async function testNotificationFunctions() {
  console.log('🧪 Testing Firebase Cloud Functions...\n');

  try {
    // Step 1: Create test users
    console.log('1️⃣ Creating test users...');
    await createTestUsers();
    console.log('✅ Test users created\n');

    // Step 2: Create test campus blocks
    console.log('2️⃣ Creating test campus blocks...');
    await createTestCampusBlocks();
    console.log('✅ Test campus blocks created\n');

    // Step 3: Create a test alert (should trigger sendAlertNotifications)
    console.log('3️⃣ Creating test alert...');
    const alertId = await createTestAlert();
    console.log(`✅ Test alert created with ID: ${alertId}\n`);

    // Wait a moment for the function to process
    console.log('⏳ Waiting for notification function to process...');
    await new Promise(resolve => setTimeout(resolve, 3000));

    // Step 4: Update alert status (should trigger notifyStatusUpdate)
    console.log('4️⃣ Updating alert status to IN_PROGRESS...');
    await updateTestAlertStatus(alertId, 'IN_PROGRESS', 'test-guard-456');
    console.log('✅ Alert status updated\n');

    // Wait a moment for the function to process
    console.log('⏳ Waiting for status update function to process...');
    await new Promise(resolve => setTimeout(resolve, 3000));

    // Step 5: Close the alert
    console.log('5️⃣ Closing the alert...');
    await updateTestAlertStatus(alertId, 'CLOSED', 'test-admin-789');
    console.log('✅ Alert closed\n');

    console.log('🎉 All tests completed successfully!');
    console.log('📋 Check the Firebase Functions logs to see the notification processing.');
    console.log('💡 In production, FCM notifications would be sent to actual devices.');

  } catch (error) {
    console.error('❌ Test failed:', error);
  }
}

async function createTestUsers() {
  const testUsers = [
    {
      email: 'guard1@campus.edu',
      name: 'Test Guard 1',
      role: 'GUARD',
      isActive: true,
      fcmToken: 'test-token-guard-1',
      lastSeen: admin.firestore.Timestamp.now(),
    },
    {
      email: 'guard2@campus.edu',
      name: 'Test Guard 2',
      role: 'GUARD',
      isActive: true,
      fcmToken: 'test-token-guard-2',
      lastSeen: admin.firestore.Timestamp.now(),
    },
    {
      email: 'admin@campus.edu',
      name: 'Test Admin',
      role: 'ADMIN',
      isActive: true,
      fcmToken: 'test-token-admin-1',
      lastSeen: admin.firestore.Timestamp.now(),
    },
  ];

  const batch = db.batch();
  
  testUsers.forEach((user, index) => {
    const userRef = db.collection('users').doc(`test-user-${index + 1}`);
    batch.set(userRef, user);
  });

  await batch.commit();
}

async function createTestCampusBlocks() {
  const testBlocks = [
    {
      name: 'Main Building',
      description: 'Primary academic building',
      coordinates: new admin.firestore.GeoPoint(40.7128, -74.0060),
      radius: 50,
    },
    {
      name: 'Library',
      description: 'Campus library building',
      coordinates: new admin.firestore.GeoPoint(40.7130, -74.0058),
      radius: 30,
    },
  ];

  const batch = db.batch();
  
  testBlocks.forEach((block, index) => {
    const blockRef = db.collection('campus_blocks').doc(`test-block-${index + 1}`);
    batch.set(blockRef, block);
  });

  await batch.commit();
}

async function createTestAlert() {
  const testAlert = {
    guardId: 'test-user-1',
    guardName: 'Test Guard 1',
    timestamp: admin.firestore.Timestamp.now(),
    location: {
      blockId: 'test-block-1',
      blockName: 'Main Building',
      coordinates: new admin.firestore.GeoPoint(40.7128, -74.0060),
    },
    message: 'Test emergency alert for function testing',
    status: 'ACTIVE',
  };

  const docRef = await db.collection('alerts').add(testAlert);
  return docRef.id;
}

async function updateTestAlertStatus(alertId, newStatus, acceptedBy) {
  const updateData = {
    status: newStatus,
  };

  if (newStatus === 'IN_PROGRESS' && acceptedBy) {
    updateData.acceptedBy = acceptedBy;
    updateData.acceptedByName = 'Test Responder';
    updateData.acceptedAt = admin.firestore.Timestamp.now();
  } else if (newStatus === 'CLOSED') {
    updateData.closedBy = acceptedBy || 'test-admin';
    updateData.closedByName = 'Test Admin';
    updateData.closedAt = admin.firestore.Timestamp.now();
  }

  await db.collection('alerts').doc(alertId).update(updateData);
}

// Run the tests
testNotificationFunctions();