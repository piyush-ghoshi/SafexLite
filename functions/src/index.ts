import * as functions from "firebase-functions";
import * as admin from "firebase-admin";

// Initialize Firebase Admin SDK
admin.initializeApp();

const db = admin.firestore();
const messaging = admin.messaging();

/**
 * Cloud Function triggered when a new alert is created
 * Sends FCM notifications to all guards and administrators
 */
export const sendAlertNotifications = functions.firestore
  .document("alerts/{alertId}")
  .onCreate(async (snap, context) => {
    try {
      const alertData = snap.data();
      const alertId = context.params.alertId;

      functions.logger.info(`Processing new alert: ${alertId}`, {
        guardId: alertData.guardId,
        location: alertData.location,
      });

      // Get all users except the alert creator
      const usersSnapshot = await db
        .collection("users")
        .where("isActive", "==", true)
        .get();

      const tokens: string[] = [];
      const userIds: string[] = [];

      usersSnapshot.forEach((doc) => {
        const userData = doc.data();
        // Exclude the guard who created the alert
        if (doc.id !== alertData.guardId && userData.fcmToken) {
          tokens.push(userData.fcmToken);
          userIds.push(doc.id);
        }
      });

      if (tokens.length === 0) {
        functions.logger.warn("No FCM tokens found for notification");
        return;
      }

      // Prepare notification payload
      const locationText = alertData.location?.blockName || "Unknown Location";
      const alertMessage = alertData.message || "";
      const notificationTitle = "🚨 Emergency Alert";
      const notificationBody = `Alert from ${
        alertData.guardName
      } at ${locationText}${alertMessage ? `: ${alertMessage}` : ""}`;

      const payload = {
        notification: {
          title: notificationTitle,
          body: notificationBody,
          icon: "ic_emergency",
          sound: "default",
        },
        data: {
          alertId: alertId,
          type: "new_alert",
          guardId: alertData.guardId,
          guardName: alertData.guardName,
          location: locationText,
          timestamp: alertData.timestamp.toDate().toISOString(),
          message: alertMessage,
        },
        android: {
          priority: "high" as "high",
          notification: {
            channelId: "emergency_alerts",
            priority: "max" as "max",
            defaultSound: true,
            defaultVibrateTimings: true,
          },
        },
      };

      // Send notifications to all tokens
      const message = {
        tokens: tokens,
        notification: payload.notification,
        data: payload.data,
        android: payload.android,
      };
      const response = await messaging.sendMulticast(message);

      functions.logger.info(`Notifications sent successfully`, {
        alertId: alertId,
        successCount: response.successCount,
        failureCount: response.failureCount,
        totalTokens: tokens.length,
      });

      // Handle failed tokens
      if (response.failureCount > 0) {
        const failedTokens: string[] = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            failedTokens.push(tokens[idx]);
            functions.logger.error(`Failed to send to token: ${tokens[idx]}`, {
              error: resp.error?.message,
            });
          }
        });

        // Remove invalid tokens from user documents
        await cleanupInvalidTokens(failedTokens, userIds);
      }

      return response;
    } catch (error) {
      functions.logger.error("Error sending alert notifications", {
        error: error,
        alertId: context.params.alertId,
      });
      throw error;
    }
  });

/**
 * Cloud Function triggered when an alert status is updated
 * Sends notifications about status changes to relevant users
 */
export const notifyStatusUpdate = functions.firestore
  .document("alerts/{alertId}")
  .onUpdate(async (change, context) => {
    try {
      const beforeData = change.before.data();
      const afterData = change.after.data();
      const alertId = context.params.alertId;

      // Check if status actually changed
      if (beforeData.status === afterData.status) {
        functions.logger.info(
          "No status change detected, skipping notification"
        );
        return;
      }

      functions.logger.info(`Alert status updated: ${alertId}`, {
        oldStatus: beforeData.status,
        newStatus: afterData.status,
        updatedBy: afterData.acceptedBy || afterData.closedBy,
      });

      // Get all active users
      const usersSnapshot = await db
        .collection("users")
        .where("isActive", "==", true)
        .get();

      const tokens: string[] = [];
      const userIds: string[] = [];

      usersSnapshot.forEach((doc) => {
        const userData = doc.data();
        if (userData.fcmToken) {
          tokens.push(userData.fcmToken);
          userIds.push(doc.id);
        }
      });

      if (tokens.length === 0) {
        functions.logger.warn(
          "No FCM tokens found for status update notification"
        );
        return;
      }

      // Prepare status update notification
      const locationText = afterData.location?.blockName || "Unknown Location";
      const statusText = getStatusDisplayText(afterData.status);
      const updaterName = getUpdaterName(afterData);

      const notificationTitle = "Alert Status Updated";
      const notificationBody = `Alert at ${locationText} is now ${statusText}${
        updaterName ? ` by ${updaterName}` : ""
      }`;

      const payload = {
        notification: {
          title: notificationTitle,
          body: notificationBody,
          icon: "ic_emergency",
          sound: "default",
        },
        data: {
          alertId: alertId,
          type: "status_update",
          oldStatus: beforeData.status,
          newStatus: afterData.status,
          location: locationText,
          timestamp: admin.firestore.Timestamp.now().toDate().toISOString(),
        },
        android: {
          priority: "high" as "high",
          notification: {
            channelId: "alert_updates",
            priority: "default" as "default",
            defaultSound: true,
          },
        },
      };

      // Send notifications
      const message = {
        tokens: tokens,
        notification: payload.notification,
        data: payload.data,
        android: payload.android,
      };
      const response = await messaging.sendMulticast(message);

      functions.logger.info(`Status update notifications sent`, {
        alertId: alertId,
        successCount: response.successCount,
        failureCount: response.failureCount,
        statusChange: `${beforeData.status} -> ${afterData.status}`,
      });

      // Handle failed tokens
      if (response.failureCount > 0) {
        const failedTokens: string[] = [];
        response.responses.forEach((resp, idx) => {
          if (!resp.success) {
            failedTokens.push(tokens[idx]);
            functions.logger.error(
              `Failed to send status update to token: ${tokens[idx]}`,
              {
                error: resp.error?.message,
              }
            );
          }
        });

        await cleanupInvalidTokens(failedTokens, userIds);
      }

      return response;
    } catch (error) {
      functions.logger.error("Error sending status update notifications", {
        error: error,
        alertId: context.params.alertId,
      });
      throw error;
    }
  });

/**
 * Helper function to get display text for alert status
 */
function getStatusDisplayText(status: string): string {
  switch (status) {
    case "ACTIVE":
      return "Active";
    case "IN_PROGRESS":
      return "In Progress";
    case "RESOLVED":
      return "Resolved";
    case "CLOSED":
      return "Closed";
    default:
      return status;
  }
}

/**
 * Helper function to get the name of who updated the alert
 */
function getUpdaterName(alertData: any): string | null {
  if (alertData.status === "IN_PROGRESS" && alertData.acceptedBy) {
    // Try to get the name from acceptedBy field if it contains the name
    return alertData.acceptedByName || null;
  }
  if (alertData.status === "CLOSED" && alertData.closedBy) {
    // Try to get the name from closedBy field if it contains the name
    return alertData.closedByName || null;
  }
  return null;
}

/**
 * Helper function to clean up invalid FCM tokens
 */
async function cleanupInvalidTokens(
  failedTokens: string[],
  userIds: string[]
): Promise<void> {
  try {
    const batch = db.batch();
    let batchCount = 0;

    for (const token of failedTokens) {
      // Find users with this token and remove it
      const usersWithToken = await db
        .collection("users")
        .where("fcmToken", "==", token)
        .get();

      usersWithToken.forEach((doc) => {
        batch.update(doc.ref, {
          fcmToken: admin.firestore.FieldValue.delete(),
        });
        batchCount++;
      });

      // Firestore batch limit is 500 operations
      if (batchCount >= 450) {
        await batch.commit();
        batchCount = 0;
      }
    }

    if (batchCount > 0) {
      await batch.commit();
    }

    functions.logger.info(
      `Cleaned up ${failedTokens.length} invalid FCM tokens`
    );
  } catch (error) {
    functions.logger.error("Error cleaning up invalid tokens", { error });
  }
}

/**
 * Scheduled function to clean up old resolved alerts (optional)
 * Runs daily at midnight to archive alerts older than 30 days
 */
export const cleanupOldAlerts = functions.pubsub
  .schedule("0 0 * * *")
  .timeZone("UTC")
  .onRun(async (context) => {
    try {
      const thirtyDaysAgo = new Date();
      thirtyDaysAgo.setDate(thirtyDaysAgo.getDate() - 30);

      const oldAlertsQuery = db
        .collection("alerts")
        .where("status", "==", "CLOSED")
        .where(
          "closedAt",
          "<",
          admin.firestore.Timestamp.fromDate(thirtyDaysAgo)
        );

      const snapshot = await oldAlertsQuery.get();

      if (snapshot.empty) {
        functions.logger.info("No old alerts to clean up");
        return;
      }

      const batch = db.batch();
      let count = 0;

      snapshot.forEach((doc) => {
        // Move to archived collection instead of deleting
        batch.set(db.collection("archived_alerts").doc(doc.id), doc.data());
        batch.delete(doc.ref);
        count++;
      });

      await batch.commit();

      functions.logger.info(`Archived ${count} old alerts`);
      return count;
    } catch (error) {
      functions.logger.error("Error cleaning up old alerts", { error });
      throw error;
    }
  });
