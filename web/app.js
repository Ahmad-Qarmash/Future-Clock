/* =========================================================
   Future Clock — web preview
   - ClockRenderer ported line-for-line from
     app/src/main/java/com/futureclock/app/ui/views/ClockRenderer.kt
   - Live analog clock + four home-screen widget previews
   ========================================================= */
(function () {
  "use strict";

  /* ---------- Color palette (matches res/values/colors.xml) ---------- */
  const COLORS = {
    bgBase:      "#0A0A1A",
    bgSurface:   "#11112A",
    bgWidget:    "#0F0F22",
    bgWidgetAlt: "#16163A",
    neonCyan:    "#00E5FF",
    neonMagenta: "#FF00E5",
    neonLime:    "#B6FF00",
    neonPurple:  "#9D00FF",
    neonPink:    "#FF1493",
    textPrimary: "#FFFFFF",
    textMuted:   "#9090C0",
    textTertiary:"#6E6E94",
    divider:     "#22224A",
  };

  /* ---------- ClockRenderer (port of ClockRenderer.kt) ----------
   * Pure function: takes a canvas, size and a time, and draws
   * the full analog face: background, ring, ticks, numerals,
   * hour/minute/second hands and center cap.
   * ----------------------------------------------------------- */
  function parseHex(hex) {
    const h = hex.replace("#", "");
    return {
      r: parseInt(h.substring(0, 2), 16),
      g: parseInt(h.substring(2, 4), 16),
      b: parseInt(h.substring(4, 6), 16),
    };
  }
  function withAlpha(hex, alphaByte) {
    const { r, g, b } = parseHex(hex);
    return `rgba(${r}, ${g}, ${b}, ${alphaByte / 255})`;
  }

  function ClockRenderer_draw(canvas, w, h, time) {
    if (w <= 0 || h <= 0) return;
    const ctx = canvas.getContext("2d");
    if (!ctx) return;

    // High-DPI handling: keep the requested CSS size, but render
    // at devicePixelRatio so lines stay crisp.
    const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    const targetW = Math.round(w * dpr);
    const targetH = Math.round(h * dpr);
    if (canvas.width !== targetW || canvas.height !== targetH) {
      canvas.width = targetW;
      canvas.height = targetH;
    }
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);

    // Clear with a transparent fill so the host page's background
    // shows through. The Android version clears with faceColor
    // because the canvas always has a face; the web widgets do
    // their own background paint, so we skip it here.
    ctx.clearRect(0, 0, w, h);

    const cx = w / 2;
    const cy = h / 2;
    const r  = Math.min(w, h) / 2;

    const faceColor       = COLORS.bgWidget;        // #0F0F22
    const strokeColor     = COLORS.neonCyan;        // #00E5FF
    const hourHandColor   = COLORS.neonMagenta;     // #FF00E5
    const minuteHandColor = COLORS.neonCyan;        // #00E5FF
    const secondHandColor = COLORS.neonLime;        // #B6FF00
    const tickColor       = COLORS.textMuted;       // #9090C0

    // 1) Dark face fill
    ctx.fillStyle = faceColor;
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.98, 0, Math.PI * 2);
    ctx.fill();

    // 2) Neon stroke ring
    ctx.strokeStyle = strokeColor;
    ctx.lineWidth = r * 0.04;
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.94, 0, Math.PI * 2);
    ctx.stroke();

    // 3) Faint inner ring
    ctx.strokeStyle = withAlpha(strokeColor, 60);
    ctx.lineWidth = r * 0.012;
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.55, 0, Math.PI * 2);
    ctx.stroke();

    // 4) Ticks — 60 total, every 5th is major
    ctx.lineCap = "round";
    for (let i = 0; i < 60; i++) {
      const angle = (i * 6 - 90) * Math.PI / 180;
      const isMajor = i % 5 === 0;
      const outer = r * 0.88;
      const inner = isMajor ? r * 0.78 : r * 0.83;
      const sx = cx + outer * Math.cos(angle);
      const sy = cy + outer * Math.sin(angle);
      const ex = cx + inner * Math.cos(angle);
      const ey = cy + inner * Math.sin(angle);
      if (isMajor) {
        ctx.strokeStyle = tickColor;
        ctx.lineWidth = r * 0.025;
      } else {
        ctx.strokeStyle = withAlpha(tickColor, 120);
        ctx.lineWidth = r * 0.012;
      }
      ctx.beginPath();
      ctx.moveTo(sx, sy);
      ctx.lineTo(ex, ey);
      ctx.stroke();
    }

    // 5) Hour numerals (12, 3, 6, 9)
    ctx.fillStyle = tickColor;
    ctx.font = `${r * 0.18}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";
    const numberOffset = r * 0.66;
    const baselineOffset = r * 0.18 / 3;
    ctx.fillText("12", cx,            cy - numberOffset + baselineOffset);
    ctx.fillText("3",  cx + numberOffset, cy + baselineOffset);
    ctx.fillText("6",  cx,            cy + numberOffset + baselineOffset);
    ctx.fillText("9",  cx - numberOffset, cy + baselineOffset);

    // 6) Time → angles
    const hour   = time.getHours() % 12;
    const minute = time.getMinutes();
    const second = time.getSeconds();
    const milli  = time.getMilliseconds();
    const hourAngle   = ((hour + minute / 60) * 30 - 90) * Math.PI / 180;
    const minuteAngle = ((minute + second / 60) * 6 - 90) * Math.PI / 180;
    const secondAngle = ((second + milli / 1000) * 6 - 90) * Math.PI / 180;

    // 7) Hour hand (magenta)
    ctx.strokeStyle = hourHandColor;
    ctx.lineWidth = r * 0.07;
    ctx.lineCap = "round";
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * 0.50 * Math.cos(hourAngle), cy + r * 0.50 * Math.sin(hourAngle));
    ctx.stroke();

    // 8) Minute hand (cyan)
    ctx.strokeStyle = minuteHandColor;
    ctx.lineWidth = r * 0.05;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * 0.72 * Math.cos(minuteAngle), cy + r * 0.72 * Math.sin(minuteAngle));
    ctx.stroke();

    // 9) Second hand (lime, with shadowBlur glow)
    ctx.shadowColor = secondHandColor;
    ctx.shadowBlur = r * 0.18;
    ctx.strokeStyle = withAlpha(secondHandColor, 200);
    ctx.lineWidth = r * 0.025;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * 0.82 * Math.cos(secondAngle), cy + r * 0.82 * Math.sin(secondAngle));
    ctx.stroke();

    ctx.shadowBlur = 0;
    ctx.strokeStyle = secondHandColor;
    ctx.lineWidth = r * 0.018;
    ctx.beginPath();
    ctx.moveTo(cx, cy);
    ctx.lineTo(cx + r * 0.82 * Math.cos(secondAngle), cy + r * 0.82 * Math.sin(secondAngle));
    ctx.stroke();

    // 10) Center cap
    ctx.shadowBlur = 0;
    ctx.fillStyle = COLORS.bgBase; // #0A0A1A
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.06, 0, Math.PI * 2);
    ctx.fill();
    ctx.fillStyle = secondHandColor;
    ctx.beginPath();
    ctx.arc(cx, cy, r * 0.035, 0, Math.PI * 2);
    ctx.fill();
  }

  /* ---------- Widget backgrounds (frame paint) ---------- */
  function paintWidgetBackground(ctx, w, h) {
    // Subtle vertical gradient matching widget_bg_gradient.xml
    const grad = ctx.createLinearGradient(0, 0, 0, h);
    grad.addColorStop(0, COLORS.bgWidget);     // #0F0F22
    grad.addColorStop(1, COLORS.bgWidgetAlt);  // #16163A
    ctx.fillStyle = grad;
    roundRect(ctx, 0, 0, w, h, 18);
    ctx.fill();
    // 1px neon-cyan border
    ctx.strokeStyle = "rgba(0, 229, 255, 0.35)";
    ctx.lineWidth = 1;
    roundRect(ctx, 0.5, 0.5, w - 1, h - 1, 18);
    ctx.stroke();
  }
  function roundRect(ctx, x, y, w, h, r) {
    const radius = Math.min(r, w / 2, h / 2);
    ctx.beginPath();
    ctx.moveTo(x + radius, y);
    ctx.arcTo(x + w, y,     x + w, y + h, radius);
    ctx.arcTo(x + w, y + h, x,     y + h, radius);
    ctx.arcTo(x,     y + h, x,     y,     radius);
    ctx.arcTo(x,     y,     x + w, y,     radius);
    ctx.closePath();
  }

  /* ---------- Widget renderers ---------- */
  function renderAnalogWidget(canvas, time) {
    const w = canvas.clientWidth  || canvas.width;
    const h = canvas.clientHeight || canvas.height;
    const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    canvas.width  = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    paintWidgetBackground(ctx, w, h);
    // Add a small inset padding so the face doesn't kiss the border
    const inset = 10;
    ClockRenderer_draw(
      { getContext: () => ctx, width: w - inset * 2, height: h - inset * 2 },
      w - inset * 2,
      h - inset * 2,
      time
    );
  }

  function renderDigitalWidget(canvas, time) {
    const w = canvas.clientWidth  || canvas.width;
    const h = canvas.clientHeight || canvas.height;
    const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    canvas.width  = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    paintWidgetBackground(ctx, w, h);

    // Layout matches widget_digital_small.xml: HH:mm + :ss on the
    // left, day + date right-aligned.
    const pad = Math.round(h * 0.14);
    const hh  = String(time.getHours()).padStart(2, "0");
    const mm  = String(time.getMinutes()).padStart(2, "0");
    const ss  = String(time.getSeconds()).padStart(2, "0");
    const days = ["SUN","MON","TUE","WED","THU","FRI","SAT"];
    const months = ["Jan","Feb","Mar","Apr","May","Jun","Jul","Aug","Sep","Oct","Nov","Dec"];
    const day  = days[time.getDay()];
    const date = `${months[time.getMonth()]} ${time.getDate()}`;

    // Big time
    const timeSize = Math.round(h * 0.55);
    ctx.fillStyle = COLORS.textPrimary;
    ctx.font = `200 ${timeSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.textAlign = "left";
    ctx.textBaseline = "alphabetic";
    const baselineY = pad + timeSize * 0.78;
    ctx.fillText(`${hh}:${mm}`, pad, baselineY);

    // Seconds (neon cyan, smaller, beneath)
    const secSize = Math.round(h * 0.22);
    ctx.fillStyle = COLORS.neonCyan;
    ctx.font = `300 ${secSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.fillText(`:${ss}`, pad, baselineY + secSize * 0.95);

    // Day + date (right side)
    const metaSize = Math.round(h * 0.18);
    ctx.fillStyle = COLORS.textMuted;
    ctx.font = `500 ${metaSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.textAlign = "right";
    ctx.fillText(day, w - pad, pad + metaSize);
    ctx.fillText(date, w - pad, pad + metaSize * 2.1);
  }

  // Sample cities for the world widget. Offsets in hours from the
  // viewer's local time. (Cities picked to roughly cover the globe.)
  const WORLD_CITIES = [
    { name: "New York",  offset: -5 },
    { name: "London",    offset:  0 },
    { name: "Tokyo",     offset:  9 },
  ];

  function cityTime(baseDate, offsetHours) {
    // Returns a Date whose getHours/Minutes/Seconds reflect the
    // city-local time. We use a UTC view so DST offset-of-offset
    // doesn't bleed in for this preview widget.
    const utcMs = baseDate.getTime() + baseDate.getTimezoneOffset() * 60000;
    return new Date(utcMs + offsetHours * 3600000);
  }

  function renderWorldWidget(canvas, time) {
    const w = canvas.clientWidth  || canvas.width;
    const h = canvas.clientHeight || canvas.height;
    const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    canvas.width  = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    paintWidgetBackground(ctx, w, h);

    const pad = Math.round(h * 0.08);
    const rowH = (h - pad * 2) / WORLD_CITIES.length;

    ctx.textBaseline = "middle";
    const nameSize = Math.round(rowH * 0.32);
    const timeSize = Math.round(rowH * 0.55);

    WORLD_CITIES.forEach((city, i) => {
      const cy = pad + rowH * i + rowH / 2;
      const local = cityTime(time, city.offset);
      const hh = String(local.getHours()).padStart(2, "0");
      const mm = String(local.getMinutes()).padStart(2, "0");

      // City name (muted)
      ctx.fillStyle = COLORS.textMuted;
      ctx.font = `500 ${nameSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
      ctx.textAlign = "left";
      ctx.fillText(city.name, pad, cy);

      // Local time (white)
      ctx.fillStyle = COLORS.textPrimary;
      ctx.font = `300 ${timeSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
      ctx.textAlign = "right";
      ctx.fillText(`${hh}:${mm}`, w - pad, cy);

      // Divider
      if (i < WORLD_CITIES.length - 1) {
        ctx.strokeStyle = "rgba(34, 34, 74, 1)";
        ctx.lineWidth = 1;
        ctx.beginPath();
        ctx.moveTo(pad, pad + rowH * (i + 1));
        ctx.lineTo(w - pad, pad + rowH * (i + 1));
        ctx.stroke();
      }
    });
  }

  function renderNextAlarmWidget(canvas, time) {
    const w = canvas.clientWidth  || canvas.width;
    const h = canvas.clientHeight || canvas.height;
    const dpr = Math.max(1, Math.min(3, window.devicePixelRatio || 1));
    canvas.width  = Math.round(w * dpr);
    canvas.height = Math.round(h * dpr);
    const ctx = canvas.getContext("2d");
    ctx.setTransform(dpr, 0, 0, dpr, 0, 0);
    paintWidgetBackground(ctx, w, h);

    // Static sample: alarm at 07:30 (matches WIDGETS.md copy)
    // Countdown computed against the live time.
    const pad = Math.round(h * 0.12);
    const labelSize = Math.round(h * 0.16);
    const timeSize  = Math.round(h * 0.42);
    const subSize   = Math.round(h * 0.18);

    ctx.textBaseline = "alphabetic";
    ctx.textAlign = "left";

    // Label "NEXT ALARM"
    ctx.fillStyle = COLORS.neonCyan;
    ctx.font = `700 ${labelSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.fillText("NEXT ALARM", pad, pad + labelSize);

    // Time
    ctx.fillStyle = COLORS.textPrimary;
    ctx.font = `300 ${timeSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.fillText("07:30", pad, pad + labelSize + timeSize * 0.95);

    // Subtitle: countdown or label
    const target = new Date(time);
    target.setHours(7, 30, 0, 0);
    if (target <= time) target.setDate(target.getDate() + 1);
    const diffMs = target - time;
    const totalMin = Math.max(0, Math.round(diffMs / 60000));
    const hh = Math.floor(totalMin / 60);
    const mm = totalMin % 60;
    const subtitle = `in ${hh}h ${mm}m`;

    ctx.fillStyle = COLORS.textMuted;
    ctx.font = `500 ${subSize}px "Inter", "Segoe UI", system-ui, sans-serif`;
    ctx.fillText(subtitle, pad, pad + labelSize + timeSize * 0.95 + subSize * 1.1);
  }

  /* ---------- Boot ---------- */
  function start() {
    // Wire up the widget showcase canvases
    const widgetCanvases = Array.from(document.querySelectorAll('canvas[data-widget]'));
    widgetCanvases.forEach((canvas) => {
      const kind = canvas.dataset.widget;
      const render = ({
        analog:    renderAnalogWidget,
        digital:   renderDigitalWidget,
        world:     renderWorldWidget,
        "next-alarm": renderNextAlarmWidget,
      })[kind];
      if (!render) return;
      const tick = () => render(canvas, new Date());
      tick();
    });

    // Live analog clock: 60fps with requestAnimationFrame
    const live = document.getElementById("live-clock");
    const liveNote = document.getElementById("live-clock-time");
    if (live) {
      // Tag the canvas so tests can find it without depending on
      // layout.
      live.setAttribute("data-clock-ticking", "true");
      const fmt = new Intl.DateTimeFormat(undefined, {
        hour: "2-digit", minute: "2-digit", second: "2-digit", hour12: false,
      });
      const draw = () => {
        ClockRenderer_draw(live, live.clientWidth || 420, live.clientHeight || 420, new Date());
        if (liveNote) liveNote.textContent = `Local time: ${fmt.format(new Date())}`;
        requestAnimationFrame(draw);
      };
      // First paint after layout, so clientWidth is non-zero
      requestAnimationFrame(draw);
    }
  }

  if (document.readyState === "loading") {
    document.addEventListener("DOMContentLoaded", start, { once: true });
  } else {
    start();
  }

  // Re-render widget previews on resize so the devicePixelRatio
  // is correct after a viewport change.
  let resizeRaf = 0;
  window.addEventListener("resize", () => {
    if (resizeRaf) cancelAnimationFrame(resizeRaf);
    resizeRaf = requestAnimationFrame(() => {
      document.querySelectorAll('canvas[data-widget]').forEach((canvas) => {
        const kind = canvas.dataset.widget;
        const render = ({
          analog: renderAnalogWidget, digital: renderDigitalWidget,
          world: renderWorldWidget, "next-alarm": renderNextAlarmWidget,
        })[kind];
        if (render) render(canvas, new Date());
      });
    });
  });

  // Expose for tests / debugging
  window.FutureClock = { drawClock: ClockRenderer_draw };
})();
