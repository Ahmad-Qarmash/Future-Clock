# Roadmap

A short list of ideas that would deepen the app without bloating it. Each
item is sized for an evening of work and is independent of the others.
Nothing here is committed — pull requests welcome.

## App features

1. **Bedside full-screen mode** — make the clock screen a real "nightstand"
   app by dimming the system UI, charging-only behaviour, and a slow
   breathing animation on the seconds. Why: a clock app is the natural
   bedside companion, and a beautiful one gets shared.
2. **Custom alarm sounds** — let users record a 5-second sound, choose a
   ringtone, or pick a Spotify/YouTube link. Why: stock ringtones are stale
   and personalisation is a retention lever.
3. **Smart alarm skipping** — if a holiday is detected from the user's
   calendar (read-only), skip that one alarm. Why: removes the #1 complaint
   about every alarm app.
4. **Sleep tracking lite** — detect phone-down time vs. screen-on time
   overnight and produce a "you slept 6h 42m" stat. Why: deepens daily
   engagement.
5. **Pomodoro mode in stopwatch** — alternate work/break phases with
   subtle haptic cues. Why: turns the stopwatch from a passive tool into
   a productivity surface.

## Widgets

6. **Pixel-art analog widget** — a low-res 16×16 chunky analog face that
   works on tiny homescreens and on the lock screen (lock screen widget
   support is currently API 31+ but is gaining traction).
7. **Countdown widget** — show the active timer's remaining time on the
   home screen so the user can scrub the phone without opening the app.
8. **Battery + clock hybrid widget** — put the time on top and a battery
   bar below, with system theme awareness. Useful as a "clean" home-screen
   option for users who want less neon.
9. **World clock with day/night map** — render a tiny world map with
   sunrise/sunset terminator and the user's city lit up.
10. **Tap-to-toggle widget** — let long-press on the analog widget toggle
    between time and the next alarm. Why: deepens the most-pinned surface.

## Monetisation

11. **Ad-free tier** — a one-time $1.99 unlock via Google Play Billing
    that removes the banner and disables the interstitial manager. The
    architecture already isolates ads in `AdManager`, so this is
    straightforward.
12. **Tip jar** — three tip amounts ($0.99, $2.99, $4.99) for users who
    like the app but don't need a feature unlock.
13. **Sponsored theme** — let brands (Spotify, Netflix, etc.) commission
    a themed skin (colour palette + clock face) that's free to all users.
    Why: better than banners and more on-brand.

## Accessibility

14. **TalkBack pass** — every screen and every widget should be readable
    aloud with sensible labels for the day, the alarm toggle, and the
    stopwatch laps. Add a `contentDescription` audit step to CI.
15. **High-contrast theme** — a pure-black, white-cyan variant for users
    with low vision. Why: a clock is a critical tool, not just a toy.
16. **Larger text scale** — honour the system font size in every screen
    by using `sp` everywhere, including the analog numerals. Why:
    Android ships with great font scaling and apps that ignore it lose
    users.

## Performance

17. **Baseline profiles** — add a Macrobenchmark module that defines a
    critical-user-journey (open app → start timer → add alarm → pin
    widget) and ships a generated baseline profile so the cold start
    improves by ~25%.
18. **R8 + resource shrinking** — already configured for release. Add
    `android.experimental.r8.dex-startup-optimization=true` to the
    gradle.properties to reduce first-install size.
19. **Batched widget updates** — when there are 10+ widgets pinned, a
    single minute tick re-renders them all. Group by visibility / size
    and only re-render visible ones.

## Engineering

20. **Migrate to Material 3 dynamic color** — opt-in to wallpaper-based
    theming on Android 12+, keep neon as the default.
21. **Kotlin Multiplatform module for the util classes** — `TimeFormat`
    and `AlarmMath` are pure Kotlin and would be useful in a Wear OS
    sibling app or a desktop widget.
22. **Hilt for DI** — the lazy fields in `FutureClockApp` work but Hilt
    would be more testable. Adopt only if/when the project grows.
23. **Detekt + ktlint in CI** — codify the style guidelines so they
    don't drift over time.
24. **Compose UI for the edit screens** — the alarm edit screen is a
    great candidate for a small Compose migration as a proof of concept
    for a future full migration.
