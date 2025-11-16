import * as admin from "firebase-admin";

/**
 * Test helper functions for Firebase Cloud Functions
 * These functions can be used to test the notification system
 */

/**
 * Create a test alert document to trigger the sendAlertNotifications function
 */
export async function createTestAlert(): Promise<string> {
  const db = admin.firestore();
  
  const testAlert = {
    guardId: "test-guard-123",
    guardName: "Test Guard",
    timestamp: admin.firestore.Timestamp.now(),
    location: {
      blockId: "block-1",
      blockName: "Main Building",
      coordinates: new admin.firestore.GeoPoint(40.7128, -74.0060),
    },
    message: "Test emergency alert",
    status: "ACTIVE",
  };

  const docRef = await db.collection("alerts").add(testAlert);
  console.log(`Test alert created with ID: ${docRef.id}`);
  return docRef.id;
}

/**
 * Update a test alert status to trigger the notifyStatusUpdate function
 */
export async function updateTestAlertStatus(
  alertId: string, 
  newStatus: string,
  acceptedBy?: string
): Promise<void> {
  const db = admin.firestore();
  
  const updateData: any = {
    status: newStatus,
  };

  if (newStatus === "IN_PROGRESS" && acceptedBy) {
    updateData.acceptedBy = acceptedBy;
    updateData.acceptedByName = "Test Responder";
    updateData.acceptedAt = admin.firestore.Timestamp.now();
  } else if (newStatus === "CLOSED") {
    updateData.closedBy = acceptedBy || "test-admin";
    updateData.closedByName = "Test Admin";
    updateData.closedAt = admin.firestore.Timestamp.now();
  }

  await db.collection("alerts").doc(alertId).update(updateData);
  console.log(`Alert ${alertId} status updated to ${newStatus}`);
}

/**
 * Create test users with FCM tokens
 */
export async function createTestUsers(): Promise<void> {
  const db = admin.firestore();
  
  const testUsers = [
    {
      id: "test-guard-123",
      email: "guard1@campus.edu",
      name: "Test Guard 1",
      role: "GUARD",
      isActive: true,
      fcmToken: "test-token-guard-1",
      lastSeen: admin.firestore.Timestamp.now(),
    },
    {
      id: "test-guard-456",
      email: "guard2@campus.edu", 
      name: "Test Guard 2",
      role: "GUARD",
      isActive: true,
      fcmToken: "test-token-guard-2",
      lastSeen: admin.firestore.Timestamp.now(),
    },
    {
      id: "test-admin-789",
      email: "admin@campus.edu",
      name: "Test Admin",
      role: "ADMIN", 
      isActive: true,
      fcmToken: "test-token-admin-1",
      lastSeen: admin.firestore.Timestamp.now(),
    },
  ];

  const batch = db.batch();
  
  testUsers.forEach((user) => {
    const userRef = db.collection("users").doc(user.id);
    batch.set(userRef, user);
  });

  await batch.commit();
  console.log("Test users created successfully");
}

/**
 * Create test campus blocks
 */
export async function createTestCampusBlocks(): Promise<void> {
  const db = admin.firestore();
  
  const testBlocks = [
    {
      id: "block-1",
      name: "Main Building",
      description: "Primary academic building",
      coordinates: new admin.firestore.GeoPoint(40.7128, -74.0060),
      radius: 50,
    },
    {
      id: "block-2", 
      name: "Library",
      description: "Campus library building",
      coordinates: new admin.firestore.GeoPoint(40.7130, -74.0058),
      radius: 30,
    },
    {
      id: "block-3",
      name: "Student Center",
      description: "Student activities building", 
      coordinates: new admin.firestore.GeoPoint(40.7125, -74.0062),
      radius: 40,
    },
  ];

  const batch = db.batch();
  
  testBlocks.forEach((block) => {
    const blockRef = db.collection("campus_blocks").doc(block.id);
    batch.set(blockRef, block);
  });

  await batch.commit();
  console.log("Test campus blocks created successfully");
}

/**
 * Clean up test data
 */
export async function cleanupTestData(): Promise<void> {
  const db = admin.firestore();
  
  // Delete test alerts
  const alertsSnapshot = await db.collection("alerts")
    .where("guardId", "==", "test-guard-123")
    .get();
  
  const batch = db.batch();
  alertsSnapshot.forEach((doc) => {
    batch.delete(doc.ref);
  });

  // Delete test users
  const testUserIds = ["test-guard-123", "test-guard-456", "test-admin-789"];
  testUserIds.forEach((userId) => {
    batch.delete(db.collection("users").doc(userId));
  });

  // Delete test blocks
  const testBlockIds = ["block-1", "block-2", "block-3"];
  testBlockIds.forEach((blockId) => {
    batch.delete(db.collection("campus_blocks").doc(blockId));
  });

  await batch.commit();
  console.log("Test data cleaned up successfully");
}