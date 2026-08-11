# Widgets

Future Clock offers two deliberate, battery-conscious home-screen widgets:

- **World Clock Live** — the flagship view of the places already tracked in the World tab.
- **Next Alarm** — the soonest enabled alarm in its saved IANA timezone.

Both are refreshed by `WidgetUpdateScheduler` at the next minute boundary. The scheduler
uses an exact alarm when permitted and an idle-safe fallback when it is not. It does not
run a background service or rely on a noisy `updatePeriodMillis` loop.

## World Clock Live

World Clock Live is a direct, read-only projection of Room's `world_cities` table. It
does **not** maintain a separate widget city list: adding, removing, or reordering a
place in the World tab updates every pinned instance immediately.

Each row contains the city and country, current local time, current IANA UTC offset,
device-relative offset, and Today/Tomorrow/Yesterday context. Offsets are calculated
for the current instant, so daylight-saving changes are handled by the underlying IANA
timezone rules.

### Size, pages, and controls

The widget adapts to the height provided by the launcher:

| Available height | Visible places |
| --- | --- |
| compact | 2 |
| medium | 3 |
| tall | 4 |
| extra tall | 5–6 |

When there are more places than fit, it advances to the next page on the scheduled
minute tick and wraps around. Each widget instance saves only its own current page in
`SharedPreferences`; page state survives process death and reboot. Previous/Next
controls change that instance's page without opening the app.

Tapping the header opens the World tab. Tapping a place opens the World tab and scrolls
to that tracked location. The empty state explains how to add the first place.

You can add the widget from the World screen's promotion card or **Settings → Widgets**.
On Android 8+ launchers that support the platform pin request, Future Clock asks the
launcher to place it. Otherwise it gives the standard Home screen → Widgets fallback.

## Next Alarm

Next Alarm shows the next enabled alarm, its saved local time, optional label/place, and
a live countdown. It uses the same alarm data and timezone-aware scheduling logic as the
app, so an alarm anchored to another region remains correct when the device timezone
changes.

## Update flow

```text
World Clock change (add / remove / reorder)
  -> Room world_cities
  -> WidgetUpdateScheduler.refreshAll()
  -> every World Clock Live instance renders the same ordered list

Minute tick
  -> WidgetTickReceiver
  -> World Clock Live refreshes and advances one page per instance
  -> Next Alarm refreshes its countdown
  -> scheduler arms the next minute tick
```

The launcher, not the app, owns final widget placement and may delay rendering while in
battery saver or under manufacturer background restrictions. The next app/widget update
will reconcile the display with Room.
