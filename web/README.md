# Future Clock — Web Preview

A small, framework-free static preview site for the **Future Clock** Android
app (`D:\Dev\FutureClock`). It mirrors the cyberpunk neon theme and renders a
live analog clock plus the four home-screen widgets in the browser using
`<canvas>`. The rendering logic is a line-for-line port of
`app/src/main/java/com/futureclock/app/ui/views/ClockRenderer.kt`.

## What's inside

```
web/
├── index.html          # landing page (hero, features, widgets, live demo, footer)
├── styles.css          # cyberpunk neon theme matching the Android app palette
├── app.js              # ClockRenderer port + widget preview renderers
├── package.json        # @playwright/test + playwright devDependencies
├── playwright.config.js
├── tests/
│   ├── landing.spec.js # page loads, hero, features, GitHub, no errors / 404s
│   ├── widgets.spec.js # 4 widget canvases, non-zero size, painted
│   └── clock.spec.js   # live analog clock is visible and ticking
└── README.md
```

## Preview locally

No build step. Just serve the folder with any static server, for example:

```bash
cd D:\Dev\FutureClock\web
npx http-server . -p 4173 -c-1
# then open http://localhost:4173
```

## Run the Playwright tests

The Playwright config auto-starts `http-server` on port 4173 via
`webServer`, so you only need two commands.

```bash
cd D:\Dev\FutureClock\web
npm install                  # one-time
npx playwright install chromium
npx playwright test
```

Useful flags:

```bash
npx playwright test --headed          # watch the browser run
npx playwright test tests/clock.spec.js
npx playwright test --ui              # Playwright UI mode
```

The `webServer` block in `playwright.config.js` reuses an existing server when
one is already running locally, so you can keep `http-server` open in another
terminal while you iterate on tests.

## Sections of the site

- **Hero** — bold "Future Clock" title with a cyan → magenta gradient, tagline,
  and CTAs to the live demo and the GitHub repo.
- **Features** — Clock, World Clock, Alarms, Timer, Stopwatch (5 cards).
- **Widget showcase** — 4 home-screen widgets rendered as live canvas previews:
  Analog, Digital, World (3 cities), and Next Alarm.
- **Live demo** — full-size ticking analog clock driven by
  `requestAnimationFrame`, with a readable caption that updates each second.

If JavaScript is disabled the user sees a clear "Enable JavaScript to see the
live clock" message in the hero and a fallback inside the live-demo section.

## Accessibility notes

- Semantic landmarks (`header`, `main`, `footer`, `nav`, `section`).
- All interactive elements are reachable via the keyboard and use
  `aria-label` / `aria-labelledby` where the visible text isn't enough.
- Each canvas has an `aria-label` describing the preview.
- Neon colors are used on the dark `#0A0A1A` background, which clears the
  WCAG AA contrast bar for body text and large headings.
