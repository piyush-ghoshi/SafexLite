# 🔔 Notification System Flow Diagram

## 📊 **Current State (NOT WORKING)**

```
┌─────────────────┐
│   Guard Phone   │
│    (Device 1)   │
└────────┬────────┘
         │
         │ 1. Press Panic Button
         │
         ▼
┌─────────────────┐
│  Create Alert   │
│   in Firestore  │
└────────┬────────┘
         │
         │ 2. Alert Saved ✅
         │
         ▼
┌─────────────────┐
│ Cloud Function  │
│   SHOULD RUN    │  ❌ NOT DEPLOYED!
│   BUT DOESN'T   │
└─────────────────┘
         │
         │ ❌ No notification sent
         │
         ▼
┌─────────────────┐
│  Other Guards   │
│   (Device 2-N)  │
│                 │
│ ❌ NO NOTIFICATION
└─────────────────┘
```

---

## ✅ **Expected State (AFTER DEPLOYING FUNCTIONS)**

```
┌─────────────────┐
│   Guard Phone   │
│    (Device 1)   │
└────────┬────────┘
         │
         │ 1. Press Panic Button
         │
         ▼
┌─────────────────────────────────────────────────────┐
│              Firebase Firestore                     │
│  ┌───────────────────────────────────────────────┐  │
│  │  alerts/abc123                                │  │
│  │  {                                            │  │
│  │    guardId: "guard1",                         │  │
│  │    guardName: "John Doe",                     │  │
│  │    location: "Block A",                       │  │
│  │    status: "ACTIVE",                          │  │
│  │    timestamp: "2025-10-06T10:30:00Z"          │  │
│  │  }                                            │  │
│  └───────────────────────────────────────────────┘  │
└────────┬────────────────────────────────────────────┘
         │
         │ 2. Alert Created ✅
         │
         ▼
┌─────────────────────────────────────────────────────┐
│         Cloud Function: sendAlertNotifications      │
│  ┌───────────────────────────────────────────────┐  │
│  │  1. Detect new alert created                  │  │
│  │  2. Query all active users                    │  │
│  │  3. Get FCM tokens (except alert creator)     │  │
│  │  4. Build notification payload                │  │
│  │  5. Send to Firebase Cloud Messaging          │  │
│  └───────────────────────────────────────────────┘  │
└────────┬────────────────────────────────────────────┘
         │
         │ 3. Notifications Sent ✅
         │
         ├──────────────┬──────────────┬──────────────┐
         │              │              │              │
         ▼              ▼              ▼              ▼
┌──────────────┐ ┌──────────────┐ ┌──────────────┐ ┌──────────────┐
│ Guard Phone  │ │ Guard Phone  │ │ Admin Phone  │ │ Admin Phone  │
│  (Device 2)  │ │  (Device 3)  │ │  (Device 4)  │ │  (Device 5)  │
│              │ │              │ │              │ │              │
│ 🔔 ALERT!    │ │ 🔔 ALERT!    │ │ 🔔 ALERT!    │ │ 🔔 ALERT!    │
│ Emergency at │ │ Emergency at │ │ Emergency at │ │ Emergency at │
│ Block A      │ │ Block A      │ │ Block A      │ │ Block A      │
└──────────────┘ └──────────────┘ └──────────────┘ └──────────────┘
```

---

## 🔄 **Complete Notification Flow**

```
┌─────────────────────────────────────────────────────────────────┐
│                    STEP 1: ALERT CREATION                       │
└─────────────────────────────────────────────────────────────────┘

Guard Dashboard Activity
         │
         │ User presses panic button
         │
         ▼
FirebaseService.createEmergencyAlert()
         │
         │ Validate input
         │ Check cooldown
         │
         ▼
Firestore: alerts/{alertId}
         │
         │ Document created
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                 STEP 2: CLOUD FUNCTION TRIGGER                  │
└─────────────────────────────────────────────────────────────────┘

Cloud Function: sendAlertNotifications
         │
         │ Triggered by Firestore onCreate
         │
         ▼
Query Firestore: users collection
         │
         │ WHERE isActive == true
         │ WHERE id != alertCreatorId
         │
         ▼
Extract FCM Tokens
         │
         │ tokens = ["token1", "token2", "token3", ...]
         │
         ▼
Build Notification Payload
         │
         │ {
         │   title: "🚨 Emergency Alert",
         │   body: "Alert from John at Block A",
         │   data: { alertId, guardName, location, ... }
         │ }
         │
         ▼
Firebase Cloud Messaging (FCM)
         │
         │ messaging.sendMulticast(tokens, payload)
         │
         ▼
┌─────────────────────────────────────────────────────────────────┐
│                  STEP 3: NOTIFICATION DELIVERY                  │
└─────────────────────────────────────────────────────────────────┘

FCM sends to all devices
         │
         ├──────────────┬──────────────┬──────────────┐
         │              │              │              │
         ▼              ▼              ▼              ▼
    Device 1       Device 2       Device 3       Device 4
         │              │              │              │
         │              │              │              │
         ▼              ▼              ▼              ▼
MyFirebaseMessagingService.onMessageReceived()
         │              │              │              │
         │              │              │              │
         ▼              ▼              ▼              ▼
NotificationService.showAlertNotification()
         │              │              │              │
         │              │              │              │
         ▼              ▼              ▼              ▼
    System Notification Displayed
         │              │              │              │
         │              │              │              │
         ▼              ▼              ▼              ▼
    🔔 User sees notification
    📱 User taps notification
    🚀 App opens to AlertDetailsActivity
```

---

## 🎯 **Key Components**

### **1. App Components (Already Working ✅)**

```
┌─────────────────────────────────────────────────────────┐
│  GuardDashboardActivity                                 │
│  ├─ Panic Button                                        │
│  ├─ FirebaseService.createEmergencyAlert()              │
│  └─ Alert saved to Firestore                            │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  MyFirebaseMessagingService                             │
│  ├─ onMessageReceived() - Handles incoming messages     │
│  ├─ onNewToken() - Updates FCM token                    │
│  └─ Calls NotificationService                           │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  NotificationService                                    │
│  ├─ showAlertNotification() - Displays notification     │
│  ├─ showStatusUpdateNotification()                      │
│  └─ Creates notification channels                       │
└─────────────────────────────────────────────────────────┘
```

### **2. Cloud Components (NEEDS DEPLOYMENT ❌)**

```
┌─────────────────────────────────────────────────────────┐
│  Cloud Function: sendAlertNotifications                 │
│  ├─ Trigger: onCreate('alerts/{alertId}')               │
│  ├─ Action: Send FCM notifications                      │
│  └─ Status: ❌ NOT DEPLOYED                             │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  Cloud Function: notifyStatusUpdate                     │
│  ├─ Trigger: onUpdate('alerts/{alertId}')               │
│  ├─ Action: Send status change notifications            │
│  └─ Status: ❌ NOT DEPLOYED                             │
└─────────────────────────────────────────────────────────┘
         │
         ▼
┌─────────────────────────────────────────────────────────┐
│  Cloud Function: cleanupOldAlerts                       │
│  ├─ Trigger: Scheduled (daily at midnight)              │
│  ├─ Action: Archive old alerts                          │
│  └─ Status: ❌ NOT DEPLOYED                             │
└─────────────────────────────────────────────────────────┘
```

---

## 🔍 **Data Flow**

### **User Data (Firestore)**

```
users/{userId}
├─ id: "user123"
├─ email: "guard@example.com"
├─ name: "John Doe"
├─ role: "GUARD"
├─ isActive: true
├─ fcmToken: "fcm_token_abc123..."  ← CRITICAL for notifications
└─ lastSeen: Timestamp
```

### **Alert Data (Firestore)**

```
alerts/{alertId}
├─ id: "alert123"
├─ guardId: "user123"
├─ guardName: "John Doe"
├─ timestamp: Timestamp
├─ location: {
│   ├─ blockName: "Block A"
│   ├─ blockId: "block_a"
│   └─ coordinates: GeoPoint
│   }
├─ message: "Emergency situation"
├─ status: "ACTIVE"
├─ acceptedBy: null
├─ acceptedAt: null
├─ resolvedAt: null
├─ closedBy: null
└─ closedAt: null
```

### **Notification Payload (FCM)**

```json
{
  "notification": {
    "title": "🚨 Emergency Alert",
    "body": "Alert from John Doe at Block A",
    "icon": "ic_emergency",
    "sound": "default"
  },
  "data": {
    "alertId": "alert123",
    "type": "new_alert",
    "guardId": "user123",
    "guardName": "John Doe",
    "location": "Block A",
    "timestamp": "2025-10-06T10:30:00Z",
    "message": "Emergency situation"
  },
  "android": {
    "priority": "high",
    "notification": {
      "channelId": "emergency_alerts",
      "priority": "max"
    }
  }
}
```

---

## 🚀 **Deployment Impact**

### **Before Deployment:**
```
Alert Created → Firestore → ❌ Nothing happens → No notifications
```

### **After Deployment:**
```
Alert Created → Firestore → ✅ Function triggers → ✅ FCM sends → ✅ Notifications arrive
```

---

## 📊 **Success Metrics**

After deploying functions, you should see:

1. **Firebase Console → Functions:**
   ```
   ✅ sendAlertNotifications - Active
   ✅ notifyStatusUpdate - Active
   ✅ cleanupOldAlerts - Active
   ```

2. **Function Logs:**
   ```
   ✅ Processing new alert: alert123
   ✅ Notifications sent successfully
   ✅ Success count: 5
   ✅ Failure count: 0
   ```

3. **Device Behavior:**
   ```
   Device 1: Creates alert → ✅ Alert saved
   Device 2: Receives notification → ✅ Within 2-3 seconds
   Device 3: Receives notification → ✅ Within 2-3 seconds
   Device 4: Receives notification → ✅ Within 2-3 seconds
   ```

---

## 🎯 **Quick Fix Command**

```bash
# Run this to fix everything:
deploy-functions.bat

# Or manually:
cd functions && npm install && cd ..
firebase deploy --only functions
```

---

## ✅ **Verification Steps**

1. **Deploy functions** (5 minutes)
2. **Check Firebase Console** → Functions (should show 3 deployed)
3. **Install app on 2 phones**
4. **Login on both phones**
5. **Create alert on Phone 1**
6. **Check Phone 2** → Should receive notification
7. **Check function logs** → Should show "Notifications sent successfully"

---

**That's it!** Once Cloud Functions are deployed, notifications will work automatically for all future alerts. 🎉
