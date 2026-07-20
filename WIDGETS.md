# Widgets

Future Clock ships four production-quality home-screen widgets. All four tick
every minute via a shared `WidgetUpdateScheduler` that uses an exact `AlarmManager`
aligned to minute boundaries. They never spam `updatePeriodMillis` and never
use background services, so battery cost is negligible.

## 1. Analog clock widget

Three layout variants are picked automatically based on the user-resized height:

| Size              | Layout                    | Contents                                                  |
| ----------------- | ------------------------- | --------------------------------------------------------- |
| 2×2 (small)       | `widget_analog_small`     | Canvas-drawn analog face only                             |
| 3×3 (medium)      | `widget_analog_medium`    | Analog face + small date line                             |
| 4×4 (large)       | `widget_analog_large`     | Day name on top, analog face, date below                  |

The face is rendered by `ClockRenderer.renderBitmap` into an `ImageView` Bitmap.
This keeps the renderer shared with the in-app `AnalogClockView` so the two
never drift visually. The renderer draws:

- Dark face fill and a 1.5dp neon-cyan stroke ring
- 60 ticks (12 major, 48 minor) using `Paint.Cap.ROUND`
- 12/3/6/9 numerals in the inner ring
- Hour hand in neon-magenta, minute hand in neon-cyan
- Optional second hand in neon-lime with a `BlurMaskFilter` glow
- Filled center cap in lime over a dark plate

The widget respects the device timezone (`TimeZone.getDefault()`) and re-renders
every minute when `WidgetTickReceiver` fires.

## 2. Digital clock widget

Two layout variants based on height:

| Size        | Layout                       | Contents                                       |
| ----------- | ---------------------------- | ---------------------------------------------- |
| 2×1         | `widget_digital_small`       | HH:mm + :ss (separate), day, date              |
| 4×2         | `widget_digital_medium`      | Big HH:mm:ss, full date                        |

The digital widget always uses 24-hour format for compactness and readability.
Times are formatted with the device locale via `TimeFormat.formatTime`.

## 3. World clock widget

Resizable. Picks layouts by the user-selected height:

| Size        | Layout                       | Cities shown |
| ----------- | ---------------------------- | ------------ |
| small       | `widget_world_medium`        | 1–2 cities   |
| large       | `widget_world_large`         | 1–3 cities   |

Each row shows the country flag + city name on the left and the local time on the
right. Cities are configured at widget-add time via `WorldClockConfigActivity`
which lets the user search the curated `CityCatalog` of 150+ cities and pick
1–3 IANA timezones. The selected list is persisted in a `SharedPreferences`
keyed by widget instance id, so each pinned widget can have a different set of
cities.

## 4. Next alarm widget

Single layout `widget_next_alarm`. Shows:

- "NEXT ALARM" label in neon-cyan
- The time of the next enabled alarm in light type
- A subtitle that either shows the alarm's label (if any) or a live countdown
  such as `in 8h 24m`

If no alarms are enabled, the widget displays "No alarm set" and a blank
subtitle so it never looks broken.

## Update scheduler internals

```text
WidgetUpdateScheduler.scheduleNext(context)
  - alarmAt = (nowMs / 60_000 + 1) * 60_000 + 1_500  (next minute + 1.5s)
  - setExactAndAllowWhileIdle(ELAPSED_REALTIME_WAKEUP, alarmAt, tickIntent)

WidgetTickReceiver.onReceive
  - WidgetUpdateScheduler.refreshAll(context)
    - broadcasts ACTION_APPWIDGET_UPDATE to all 4 widget providers
  - WidgetUpdateScheduler.scheduleNext(context)
```

The scheduler is `setExactAndAllowWhileIdle` so it survives Doze on API 23+
and is also resilient to manufacturer background restrictions. Because it
self-reschedules at the next minute boundary, total drift over a day is bounded
to 1.5s.

## Tap-to-open

Every widget root view sets a `PendingIntent` to `MainActivity` with one of
the `ACTION_OPEN_*_TAB` actions from `notification.NotificationConstants`. The
activity reads the action in `handleDeepLink` and selects the matching
`BottomNavigationView` item so the user lands on the relevant tab.

## Customising the widgets

- Want different colours? Edit `ClockRenderer` (analog) or the layout XMLs
  (text widgets). The cyberpunk palette lives in `res/values/colors.xml`.
- Want a 12-hour digital widget? Change `use24h = true` in
  `DigitalClockWidget.updateOne` to `false`.
- Want more cities in the world widget? Edit `widget_info_world.xml` to a
  larger `targetCellHeight`, and add another row to `widget_world_large.xml`
  plus the matching view ids.
