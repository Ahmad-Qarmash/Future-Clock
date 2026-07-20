# Contributing

Thanks for considering a contribution. Future Clock is intentionally small
and the bar for changes is "does this make the experience more delightful
without making the app heavier?".

## Ground rules

1. Keep the cyberpunk neon aesthetic. Avoid Material defaults that clash
   with the palette (white backgrounds, blue primary, etc.).
2. Every change must keep `Lint` and `unit tests` green. Run locally:
   ```bash
   ./gradlew lint test
   ```
3. For new features, update the relevant `.md` doc in the repository root
   (`README.md`, `WIDGETS.md`, `ADS.md`, `BUILD.md`, or `ROADMAP.md`).
4. New widgets need an `appwidget-provider` XML, a RemoteViews layout
   under `res/layout/widget_*.xml`, a `ClockRenderer` extension if the
   widget draws to a canvas, and a registration in `WidgetUpdateScheduler.refreshAll`.
5. The web preview at `web/` should be kept in sync — if you add a new
   Android screen, add a feature card to `web/index.html`. The Playwright
   tests will catch most regressions.

## Local setup

```bash
git clone https://github.com/Ahmad-Qarmash/Future-Clock.git
cd Future-Clock
./gradlew assembleDebug         # Android APK
cd web
npm install
npx playwright install chromium
npx playwright test             # Web preview e2e
```

## Code style

- Kotlin: official Kotlin code style, 4-space indent.
- XML: 4-space indent, attributes alphabetised within an element when
  the element has more than 5 attributes.
- No `!!` (force-unwrap) outside test code. Prefer `?.let`, `?:`, or
  explicit null-handling.
- No `GlobalScope` — use `lifecycleScope` or `applicationScope`.
- Never use `Log.d` / `Log.v` in production paths. Use the existing
  pattern in `AlarmScheduler` (logger tag + concise message).

## Pull request process

1. Branch off `main`.
2. Open a PR with a short title and a description that links to the
   issue or roadmap item being addressed.
3. Wait for CI to pass (lint + unit + Playwright).
4. Address review comments with additional commits; we'll squash on merge.

## Reporting bugs

Use the GitHub issue tracker. Include:
- Device model and Android version
- `adb logcat` snippet if the issue is a crash
- The exact screen or widget
- Whether it reproduces after a clean install

## Security

The app does not collect any data. Ads are loaded via AdMob and a
consent dialog will be added before the first ad request. If you find
a security issue, please email the maintainer rather than opening a
public issue.
