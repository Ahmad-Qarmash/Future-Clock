const { test, expect } = require("@playwright/test");

test.describe("Live analog clock", () => {
  test("the live analog clock canvas is visible", async ({ page }) => {
    await page.goto("/");
    const clock = page.getByTestId("live-clock");
    await expect(clock).toBeVisible();
  });

  test("the canvas has a non-zero size", async ({ page }) => {
    await page.goto("/");
    const clock = page.getByTestId("live-clock");
    await expect(clock).toBeVisible();
    const box = await clock.boundingBox();
    expect(box).not.toBeNull();
    expect(box.width).toBeGreaterThan(0);
    expect(box.height).toBeGreaterThan(0);
  });

  test("two snapshots taken 1.1s apart differ (clock is ticking)", async ({ page }) => {
    await page.goto("/");
    const clock = page.getByTestId("live-clock");
    await expect(clock).toBeVisible();

    // Wait until the canvas has actually been drawn into.
    await page.waitForFunction(() => {
      const c = document.getElementById("live-clock");
      if (!c || c.width === 0 || c.height === 0) return false;
      const ctx = c.getContext("2d");
      if (!ctx) return false;
      // Sample a few pixels; at least one must have non-zero alpha.
      const data = ctx.getImageData(0, 0, c.width, c.height).data;
      for (let i = 3; i < data.length; i += 4) {
        if (data[i] !== 0) return true;
      }
      return false;
    });

    const sampleCanvas = async () => {
      return await page.evaluate(() => {
        const c = document.getElementById("live-clock");
        // Hash a tiny PNG of the current canvas so we can compare
        // frames without dragging megabytes around.
        return c.toDataURL("image/png");
      });
    };

    const snapshot1 = await sampleCanvas();
    await page.waitForTimeout(1100);
    const snapshot2 = await sampleCanvas();

    expect(snapshot1.length, "first snapshot must exist").toBeGreaterThan(100);
    expect(snapshot2.length, "second snapshot must exist").toBeGreaterThan(100);
    expect(
      snapshot1,
      "clock canvas must change between snapshots — second hand should have moved"
    ).not.toBe(snapshot2);
  });

  test("clock ticks on multiple intervals", async ({ page }) => {
    // Sanity-check the animation loop is sustained: take three
    // samples 600ms apart and assert all three are unique.
    await page.goto("/");
    const clock = page.getByTestId("live-clock");
    await expect(clock).toBeVisible();
    await page.waitForFunction(() => {
      const c = document.getElementById("live-clock");
      return c && c.width > 0 && c.height > 0;
    });

    const sample = () => page.evaluate(() => document.getElementById("live-clock").toDataURL("image/png"));
    const a = await sample();
    await page.waitForTimeout(600);
    const b = await sample();
    await page.waitForTimeout(600);
    const c = await sample();

    expect(a).not.toBe(b);
    expect(b).not.toBe(c);
    expect(a).not.toBe(c);
  });
});
