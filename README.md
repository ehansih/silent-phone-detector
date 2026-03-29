# Silent Phone Detector

Behavior-based visibility for sensitive Android permissions. This repo is the initial MVP scaffold with a working Compose UI that mirrors the concept note and provides a clean foundation for the monitoring engine.

## What\'s Included

- Kotlin + Jetpack Compose app skeleton
- Dashboard UI with risk score, summary, and recent behavior cards
- Theme + typography

## Next Implementation Steps

- AppOpsManager hooks for microphone/location access usage
- UsageStatsManager for background activity
- Foreground service for session tracking (user-opt-in)
- Local Room database for event history
- Daily summaries + notifications

## Run Locally

1. Open the project in Android Studio.
2. Sync Gradle and run on an emulator or device.

Note: `gradle/wrapper/gradle-wrapper.jar` is not included in this scaffold. If Android Studio does not generate it automatically, run `gradle wrapper` on a machine with Gradle installed.

## License

MIT
