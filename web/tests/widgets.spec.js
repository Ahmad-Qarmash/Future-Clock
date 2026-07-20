const { test, expect } = require("@playwright/test");

test.describe("Widget showcase", () => {
  test.beforeEach(async ({ page }) => {
    await page.goto("/");
  });

  test("widget showcase section has 4 widget cards", async ({ page }) => {
    const showcase = page.getByTestId("widget-showcase");
    await expect(showcase).toBeVisible();
    const cards = page.locator('[data-testid="widget-showcase"] .widget-card');
    await expect(cards).toHaveCount(4);
  });

  test("widget canvases are visible with non-zero dimensions", async ({ page }) => {
    const ids = [
      "widget-canvas-analog",
      "widget-canvas-digital",
      "widget-canvas-world",
      "widget-canvas-next-alarm",
    ];
    for (const testId of ids) {
      const canvas = page.getByTestId(testId);
      await expect(canvas).toBeVisible();
      const box = await canvas.boundingBox();
      expect(box, `bounding box for ${testId}`).not.toBeNull();
      expect(box.width,  `${testId} width`).toBeGreaterThan(0);
      expect(box.height, `${testId} height`).toBeGreaterThan(0);
    }
  });

  test("widgets have been rendered (canvas pixel data is not empty)", async ({ page }) => {
    await page.waitForFunction(() => {
      const canvases = Array.from(document.querySelectorAll('canvas[data-widget]'));
      return canvases.length === 4 && canvases.every((c) => c.width > 0 && c.height > 0);
    });
    const result = await page.evaluate(() => {
      return Array.from(document.querySelectorAll('canvas[data-widget]')).map((c) => {
        const ctx = c.getContext("2d");
        const w = c.width, h = c.height;
        if (!ctx || w === 0 || h === 0) return { kind: c.dataset.widget, nonEmpty: false, w, h };
        const data = ctx.getImageData(Math.floor(w / 2), Math.floor(h / 2), 1, 1).data;
        return {
          kind: c.dataset.widget,
          w, h,
          nonEmpty: data[3] > 0 || data[0] + data[1] + data[2] > 0,
        };
      });
    });
    expect(result).toHaveLength(4);
    for (const r of result) {
      expect(r.w, `${r.kind} canvas width`).toBeGreaterThan(0);
      expect(r.h, `${r.kind} canvas height`).toBeGreaterThan(0);
      // The widget background paint guarantees non-zero alpha even
      // for the small analog face (center is a lime dot).
      expect(r.nonEmpty, `${r.kind} pixel sample is not empty`).toBe(true);
    }
  });
});
