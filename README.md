# Future Clock

A free, ad-supported universal Android clock app with a bold cyberpunk neon design and four production-quality home-screen widgets. Future Clock combines a live clock, world clock, alarms, timer, and stopwatch into one lightweight native app.

## Main features

- **Live clock tab** — large neon digits, ticking seconds, date, day, battery, and timezone
- **World clock** — add any of 150+ curated cities, search by name or country, automatic day-difference indicators
- **Alarms** — repeat by day-of-week, custom label, gradual volume, vibration, snooze, and an optional math challenge to force wake-up
- **Timer** — circular neon progress, presets (1/3/5/10/30 min), foreground service, system notification with pause/reset
- **Stopwatch** — sub-second precision, lap history, share via system intent
- **Settings** — 12/24 h, show seconds, snooze length
- **Four home-screen widgets** — analog, digital, world clock, and next-alarm

## Stack

- Native Android, Kotlin 1.9, AGP 8.2
- View system + Material 3 components, ViewBinding
- Room 2.6 for alarms and world cities
- DataStore 1.0 for user preferences
- AlarmManager for exact alarms and minute-aligned widget ticks
- Foreground services for timer and stopwatch
- Google AdMob for banner and interstitial ads
- Min SDK 24 (Android 7.0), target SDK 34 (Android 14)

## Local development

The project ships with Gradle 8.4 and Android Gradle Plugin 8.2.2. Use the Gradle wrapper:

```bash
git clone https://github.com/Ahmad-Qarmash/Future-Clock.git
cd Future-Clock
./gradlew assembleDebug
```

The debug APK is produced at `app/build/outputs/apk/debug/app-debug.apk`. Install on any Android 7+ device:

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Useful commands

| Command                       | Purpose                                              |
| ----------------------------- | ---------------------------------------------------- |
| `./gradlew assembleDebug`     | Build a debug APK with test AdMob IDs                |
| `./gradlew assembleRelease`   | Build a release AAB (requires your signing key)      |
| `./gradlew installDebug`      | Install the debug APK on a connected device          |
| `./gradlew lint`              | Run Android Lint                                     |
| `./gradlew clean`             | Wipe build outputs                                   |

## Architecture overview

```
UI (Fragments + Activities + Views)
        ↓ observe
ViewModels / StateFlow
        ↓
Repositories
        ↓
Room (alarms, world cities) + DataStore (preferences)
        ↑
AlarmScheduler (AlarmManager)  — AlarmReceiver  — AlarmRingActivity
BootReceiver                   — re-arms alarms on BOOT_COMPLETED
WidgetUpdateScheduler          — re-renders all widgets each minute
AdManager (AdMob)              — banner + interstitial
```

## Documentation

- [PROJECT_MAP.md](./PROJECT_MAP.md) — directory structure, data model, key flows
- [WIDGETS.md](./WIDGETS.md) — the four home-screen widgets in detail
- [ADS.md](./ADS.md) — AdMob integration, swapping in real unit IDs, ad policy
- [BUILD.md](./BUILD.md) — release build, signing, Play Store preparation
- [ROADMAP.md](./ROADMAP.md) — future feature ideas
- [LICENSE](./LICENSE) — MIT

## License

Released under the [MIT License](./LICENSE).
