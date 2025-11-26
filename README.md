# SafexLite - Campus Emergency Alert System

**Your Safety, Our Priority**

SafexLite is a professional emergency alert system designed for campus security. Guards can instantly alert their team about emergencies with precise location information.

## 🚨 Features

- **Emergency Panic Button** - One-tap emergency alerts
- **Manual Location Selection** - 5 departments, 3 blocks each, 4 floors per block
- **Instant Notifications** - Real-time alerts with custom sound
- **Role-Based Access** - Separate dashboards for Guards and Admins
- **Alert Management** - Accept, resolve, and track emergency alerts
- **Offline Support** - Works without internet, syncs when online
- **Dark Mode** - Full dark theme support
- **Background Monitoring** - Receives alerts even when app is in background
- **Custom Notification Sound** - Attention-grabbing emergency alert sound
- **Splash Screen** - Professional branded splash screen

## 📱 App Structure

### Campus Organization
- **5 Departments:** AITR, AIPER, AIMSR, AIL, AFMR
- **3 Blocks per department:** A, B, C
- **4 Floors per block:** Ground, 1st, 2nd, 3rd
- **Common Areas:** Ground, Garden, Canteen, Parking
- **Shared Area:** Bus Parking Area

## 🏗️ Tech Stack

- **Language:** Kotlin
- **Architecture:** MVVM
- **Database:** Firebase Firestore + Room (offline)
- **Authentication:** Firebase Auth
- **Notifications:** Firebase Cloud Messaging
- **UI:** Material Design 3



### Guard
- Create emergency alerts with location
- View all active alerts
- Accept and respond to alerts
- Resolve emergencies
- Receive instant notifications

### Admin
- View all alerts and statistics
- Monitor guard responses
- Close resolved alerts
- Access complete alert history
- View alert timeline

## 🔔 Notifications

- **Instant Delivery:** 1-2 second notification when app is running
- **Custom Sound:** Your own emergency alert sound
- **Background Service:** Monitors alerts when app is in background
- **Smart Filtering:** No duplicate notifications for resolved alerts
- **High Priority:** Shows as heads-up notification

## 📦 Project Structure

```
app/src/main/
├── java/com/campus/panicbutton/
│   ├── activities/          # UI Activities
│   │   ├── SplashActivity.kt
│   │   ├── LoginActivity.kt
│   │   ├── GuardDashboardActivity.kt
│   │   ├── AdminDashboardActivity.kt
│   │   └── AlertDetailsActivity.kt
│   ├── models/              # Data Models
│   │   ├── Alert.kt
│   │   ├── User.kt
│   │   └── CampusLocation.kt
│   ├── services/            # Background Services
│   │   ├── FirebaseService.kt
│   │   ├── AlertListenerService.kt
│   │   └── LocationService.kt
│   ├── utils/               # Utilities
│   │   ├── SimpleNotificationManager.kt
│   │   └── ErrorHandler.kt
│   └── database/            # Local Database
│       ├── AppDatabase.kt
│       └── dao/
└── res/
    ├── layout/              # UI Layouts
    ├── drawable/            # Icons and graphics
    ├── raw/                 # Custom notification sound
    └── values/              # Strings, colors, themes
```

## 🛠️ Configuration

### Firestore Security Rules

Deploy security rules:
```bash
firebase deploy --only firestore:rules
```

### Cloud Functions (Optional - for push notifications when app is closed)

1. Upgrade to Firebase Blaze plan
2. Deploy functions:
```bash
cd functions
npm install
firebase deploy --only functions
```

Cost: ~$0.30/month for typical usage

## 📱 How to Use

### For Guards:

1. **Login** with your credentials
2. **Tap EMERGENCY** button when needed
3. **Select location:**
   - Choose department
   - Choose block/floor or area
   - Add optional message
4. **Send alert** - All guards notified instantly

### For Admins:

1. **Login** with admin credentials
2. **View dashboard** with alert statistics
3. **Monitor alerts** in real-time
4. **Close resolved** alerts
5. **View history** and timeline

## 🔧 Troubleshooting

### Notifications not working:
1. Uninstall old app completely
2. Restart phone
3. Install new APK
4. Grant all permissions

### Custom sound not playing:
1. Ensure `emergency_alert.mp3` is in `app/src/main/res/raw/`
2. Rebuild app
3. Uninstall and reinstall

### App crashes on launch:
1. Check Firebase configuration
2. Verify `google-services.json` is correct
3. Clear app data and reinstall

## 📄 License

This project is licensed under the MIT License.

## 👨‍💻 Development

### Building Release APK

```bash
./gradlew assembleRelease
```

### Running Tests

```bash
./gradlew test
./gradlew connectedAndroidTest
```

## 🤝 Contributing

Contributions are welcome! Please:
1. Fork the repository
2. Create a feature branch
3. Commit your changes
4. Push to the branch
5. Open a Pull Request

## 📞 Support

For issues or questions:
- Open an issue on GitHub
- Check existing documentation
- Review Firebase setup guide

## 🙏 Acknowledgments

- Firebase for backend infrastructure
- Material Design for UI components
- Android community for support and libraries

---

**Built with ❤️ for campus safety**
