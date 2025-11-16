# Campus Panic Button - Firebase Cloud Functions

This directory contains the Firebase Cloud Functions for the Campus Panic Button application.

## Functions Overview

### 1. sendAlertNotifications
- **Trigger**: Firestore document creation in `alerts/{alertId}`
- **Purpose**: Sends FCM push notifications to all guards and administrators when a new alert is created
- **Features**:
  - Excludes the alert creator from notifications
  - Handles invalid FCM tokens cleanup
  - Comprehensive error logging
  - High-priority notifications for emergency alerts

### 2. notifyStatusUpdate
- **Trigger**: Firestore document update in `alerts/{alertId}`
- **Purpose**: Sends notifications when alert status changes (accepted, resolved, closed)
- **Features**:
  - Only triggers on actual status changes
  - Provides context about who updated the status
  - Different notification channels for status updates vs new alerts

### 3. cleanupOldAlerts (Optional)
- **Trigger**: Scheduled daily at midnight UTC
- **Purpose**: Archives alerts older than 30 days to maintain database performance
- **Features**:
  - Moves old closed alerts to archived collection
  - Configurable retention period

## Setup Instructions

### Prerequisites
- Node.js 18 or higher
- Firebase CLI installed globally: `npm install -g firebase-tools`
- Firebase project configured

### Installation
1. Navigate to the functions directory:
   ```bash
   cd functions
   ```

2. Install dependencies:
   ```bash
   npm install
   ```

3. Build the TypeScript code:
   ```bash
   npm run build
   ```

### Development
1. Start the Firebase emulator:
   ```bash
   npm run serve
   ```

2. Test functions locally using the Firebase shell:
   ```bash
   npm run shell
   ```

### Deployment
1. Deploy all functions:
   ```bash
   npm run deploy
   ```

2. Deploy specific function:
   ```bash
   firebase deploy --only functions:sendAlertNotifications
   ```

### Monitoring
- View function logs:
  ```bash
  npm run logs
  ```

- Monitor in Firebase Console:
  - Go to Firebase Console > Functions
  - View execution logs, performance metrics, and error rates

## Configuration

### Environment Variables
The functions use Firebase Admin SDK which automatically uses the default service account when deployed. For local development, you may need to set:

```bash
export GOOGLE_APPLICATION_CREDENTIALS="path/to/service-account-key.json"
```

### FCM Setup
Ensure your Android app is configured with:
- Notification channels: `emergency_alerts` and `alert_updates`
- Proper FCM token management
- Notification click handling

## Error Handling

The functions include comprehensive error handling:
- Invalid FCM token cleanup
- Firestore operation retries
- Detailed logging for debugging
- Graceful degradation when services are unavailable

## Security

- Functions run with Firebase Admin privileges
- Firestore security rules control data access
- FCM tokens are validated and cleaned up automatically
- All operations are logged for audit purposes