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
  ├── hosts a fragment container + five-destination bottom nav
  ├── owns a shared banner ad slot above the bottom nav
  └── routes widget deep-link intents to the right tab
  ↓
Primary fragments (Clock, World, Alarm, Timer, More)
Secondary fragments (Stopwatch, Settings, Widget settings)
  ↓ observe Flow
Room DAO (alarms, world cities)   +   DataStore (settings)
Versioned SQLite place catalog (read-only, separate from user data)

Background paths
  AlarmManager.setAlarmClock ──► AlarmReceiver ──► AlarmRingActivity
  AlarmManager.setExact      ──► WidgetTickReceiver ──► World Clock Live + Next Alarm redraw
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
| `ui/more`                                       | Secondary tools, preferences, widget entry point, and app information |
| `ui/settings`                                   | User preferences plus the Settings → Widgets screen                  |
| `ui/views`                                      | Custom `AnalogClockView` and `CircularTimerView` (Canvas-drawn)      |
| `data/db`                                       | Room entities (`AlarmEntity`, `WorldCityEntity`), DAOs, database     |
| `data/prefs`                                    | DataStore-backed `SettingsRepository`                                |
| `data/tz`                                       | Indexed offline `CityCatalog` with 235,000+ GeoNames places          |
| `alarm`                                         | `AlarmScheduler`, `AlarmReceiver`, `AlarmRingActivity`, snooze      |
| `service`                                       | `TimerService` and `StopwatchService` foreground services            |
| `receiver`                                      | `BootReceiver`, `WidgetTickReceiver`                                 |
| `widget`                                        | World Clock Live, Next Alarm, pinning/discovery helpers, and scheduler |
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
| `alarms`        | schedule, IANA timezone, independent place snapshot, feedback, and next trigger           |
| `world_cities`  | stable catalog location ID, IANA timezone, display name, country, flag, sort order        |

Alarms are sorted by `next_trigger_ms` (computed by `AlarmMath.nextTrigger`) so the
`NextAlarmWidget` always shows the soonest enabled alarm and the schedule path can avoid
duplicating work.

## Alarm scheduling flow

1. User creates or edits an alarm in `AlarmEditActivity`.
   - The place picker promotes valid, de-duplicated `world_cities` rows with their
     current local time, UTC offset, and day delta before the full offline catalog.
   - The selected place ID, name, country, flag, and IANA timezone are copied into
     the alarm. Removing that place from World Clock therefore cannot change the alarm.
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
   - Refreshes World Clock Live and Next Alarm. World Clock Live advances its per-widget
     page once before rendering current Room data.
   - Reschedules itself at the next minute boundary.
4. The minute alignment avoids drift and the small buffer gives the system time to
   fully draw before the next redraw.

## World Clock Live data flow

1. `WorldFragment` and `WorldPickerActivity` are the only ways tracked locations change.
2. They write Room's ordered `world_cities` list, then call `WidgetUpdateScheduler.refreshAll()`.
3. Every World Clock Live instance reads that same list. `SharedPreferences` stores only each
   instance's page number, never location records.
4. Header taps open the World tab; row taps also pass the location ID so the app scrolls to it.
5. Settings → Widgets and the World screen use `requestPinAppWidget` where the launcher supports it.

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
