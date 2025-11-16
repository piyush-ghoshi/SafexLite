# Create Test Data in Firestore

## To Test Notifications and History

### 1. Go to Firebase Console
- Visit: https://console.firebase.google.com/project/safexlite
- Click **Firestore Database**

### 2. Create Test Alerts Collection

#### Create Collection: `alerts`

#### Add Test Alert Document 1:
**Document ID**: `test-alert-1`
```json
{
  "id": "test-alert-1",
  "guardId": "test-guard-1",
  "guardName": "Test Guard 1",
  "message": "Test emergency alert",
  "location": null,
  "status": "ACTIVE",
  "timestamp": "2024-01-15T10:30:00Z",
  "acceptedBy": null,
  "acceptedAt": null,
  "resolvedAt": null,
  "closedBy": null,
  "closedAt": null
}
```

#### Add Test Alert Document 2:
**Document ID**: `test-alert-2`
```json
{
  "id": "test-alert-2", 
  "guardId": "test-guard-2",
  "guardName": "Test Guard 2",
  "message": "Another test alert",
  "location": null,
  "status": "RESOLVED",
  "timestamp": "2024-01-15T09:15:00Z",
  "acceptedBy": "test-guard-3",
  "acceptedAt": "2024-01-15T09:20:00Z",
  "resolvedAt": "2024-01-15T09:45:00Z",
  "closedBy": null,
  "closedAt": null
}
```

### 3. Field Types in Firestore:
- `id`: string
- `guardId`: string
- `guardName`: string
- `message`: string
- `location`: null
- `status`: string
- `timestamp`: timestamp (use Firestore timestamp)
- `acceptedBy`: string (or null)
- `acceptedAt`: timestamp (or null)
- `resolvedAt`: timestamp (or null)
- `closedBy`: string (or null)
- `closedAt`: timestamp (or null)

### 4. Test Steps:
1. **Add test data** to Firestore as above
2. **Login to app** - should see test alerts in history
3. **Create new alert** - should appear in real-time
4. **Test on 2 devices** - new alerts should trigger notifications

This will help verify if the history and notification systems are working properly.