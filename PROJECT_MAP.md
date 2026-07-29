# Project Map

## Runtime architecture

```text
App launch
  ↓
FutureClockApp (Application)
  ├── creates Notification channels
  ├── warms the offline place catalog on a background dispatcher
  └── schedules the next widget tick
  ↓
MainActivity
  ├── hosts a fragment container + bottom nav
  ├── owns a shared banner ad slot above the bottom nav
  └── routes widget deep-link intents to the right tab
  ↓
Fragments (Clock, World, Alarm, Timer, Stopwatch, Settings)
  ↓ observe Flow
Room DAO (alarms, world cities)   +   DataStore (settings)
Versioned SQLite place catalog (read-only, separate from user data)

Background paths
  AlarmManager.setAlarmClock ──► AlarmReceiver ──► AlarmRingActivity
  AlarmManager.setExact      ──► WidgetTickReceiver ──► all 4 widgets redraw
  BootReceiver               ──► re-arms all enabled alarms from Room

Foreground services
  TimerService   ──► ongoing notification with pause / reset
  StopwatchService ──► ongoing notification with play / pause / lap / reset
```

## Important directories

| Path                                            | Responsibility                                                       |
| ----------------------------------------------- | -------------------------------------------------------------------- |
| `app/src/main/java/com/futureclock/app/`        | Application class, MainActivity, navigation root                     |
| `ui/clock`                                      | Live clock screen with neon typography and battery card              |
| `ui/world`                                      | World clock list, city picker, drag-to-reorder                       |
| `ui/alarm`                                      | Alarm list, edit screen with time picker, day chips, snooze slider   |
| `ui/timer`                                      | Countdown timer with circular neon progress and presets              |
| `ui/stopwatch`                                  | Stopwatch with laps and share                                        |
| `ui/settings`                                   | User preferences screen                                              |
| `ui/views`                                      | Custom `AnalogClockView` and `CircularTimerView` (Canvas-drawn)      |
| `data/db`                                       | Room entities (`AlarmEntity`, `WorldCityEntity`), DAOs, database     |
| `data/prefs`                                    | DataStore-backed `SettingsRepository`                                |
| `data/tz`                                       | Indexed offline `CityCatalog` with 235,000+ GeoNames places          |
| `alarm`                                         | `AlarmScheduler`, `AlarmReceiver`, `AlarmRingActivity`, snooze      |
| `service`                                       | `TimerService` and `StopwatchService` foreground services            |
| `receiver`                                      | `BootReceiver`, `WidgetTickReceiver`                                 |
| `widget`                                        | 4 `AppWidgetProvider`s, `WidgetUpdateScheduler`, world widget config |
| `ads`                                           | `AdManager` singleton for banner + interstitial                      |
| `notification`                                  | Channel IDs, action keys, extras keys                                |
| `util`                                          | Pure-Kotlin helpers (`TimeFormat`, `AlarmMath`)                      |
| `res/layout`                                    | Fragments, list items, widget RemoteViews, ring activity            |
| `res/drawable`                                  | Vector icons, neon backgrounds, adaptive launcher                    |
| `res/values`                                    | Cyberpunk color palette, theme, dimens, strings                      |
| `res/xml`                                       | Widget info providers and backup rules                               |

## Room data model

| Table           | Purpose                                                                                  |
| --------------- | ---------------------------------------------------------------------------------------- |
| `alarms`        | hour, minute, label, repeat mask, IANA timezone, enabled, feedback, next trigger          |
| `world_cities`  | stable catalog location ID, IANA timezone, display name, country, flag, sort order        |

Alarms are sorted by `next_trigger_ms` (computed by `AlarmMath.nextTrigger`) so the
`NextAlarmWidget` always shows the soonest enabled alarm and the schedule path can avoid
duplicating work.

## Alarm scheduling flow

1. User creates or edits an alarm in `AlarmEditActivity`.
2. The activity calls `AlarmScheduler.schedule(context, alarm)` which:
   - Computes the next absolute trigger time using `AlarmMath.nextTrigger`.
   - Uses `AlarmManager.setAlarmClock` on API 23+ when exact alarms are permitted
     (preferred because it shows in the system status bar and is exempt from Doze).
   - Falls back to `setAndAllowWhileIdle` when exact-alarm permission is denied.
3. When the trigger fires, `AlarmReceiver` looks up the alarm and starts
   `AlarmRingActivity` with `FLAG_ACTIVITY_NEW_TASK | FLAG_ACTIVITY_CLEAR_TOP`
   and `setShowWhenLocked` / `setTurnScreenOn` so the alarm works over the lock screen.
4. After dismiss the activity either disables a one-shot alarm or re-schedules a
   repeating alarm at its next valid day-of-week.

## Widget update flow

1. `FutureClockApp.onCreate` calls `WidgetUpdateScheduler.scheduleNext(context)`.
2. The scheduler sets an exact alarm at the next minute boundary + 1.5s buffer.
3. At that time, `WidgetTickReceiver` fires and the scheduler:
   - Calls `refreshAll(context)` which broadcasts `ACTION_APPWIDGET_UPDATE` to all 4
     `AppWidgetProvider` classes; each one re-renders its `RemoteViews`.
   - Reschedules itself at the next minute boundary.
4. The minute alignment avoids drift and the small buffer gives the system time to
   fully draw before the next redraw.

## World widget configuration

1. User long-presses the home screen, picks the World widget, then the system
   launches `WorldClockConfigActivity`.
2. The activity lets the user search and toggle 1–3 cities.
3. On confirm, complete place records are saved to `SharedPreferences` keyed by widget
   instance ID, so rendering survives process death or catalog replacement.
4. Launchers that support reconfiguration can reopen the same activity for an existing widget.

## Ads

`AdManager` is a singleton that:

- Loads banner and interstitial inventory through the shared manager.
- Exposes `createBanner` for the always-visible banner above the bottom nav.
- Renders the interstitial at well-defined moments and rate-limits it so it never feels
  intrusive.

See [ADS.md](./ADS.md) for swapping in your own AdMob unit IDs.

## Ad-free in debug

`FutureClockApp` initialises AdMob at startup, but the unit IDs in
`app/build.gradle.kts` are Google-provided test IDs so the debug APK is
immediately usable. Replace them with your real unit IDs in a `release` build
type for production.

## Build configuration

| Property              | Value                                  |
| --------------------- | -------------------------------------- |
| `applicationId`       | `com.futureclock.app`                  |
| `namespace`           | `com.futureclock.app`                  |
| `minSdk`              | 24                                      |
| `targetSdk`           | 34                                      |
| `compileSdk`          | 34                                      |
| `versionCode`         | 1                                       |
| `versionName`         | 1.0.0                                   |
| `Kotlin`              | 1.9.22                                  |
| `AGP`                 | 8.2.2                                   |
| `Java`                | 17                                      |
