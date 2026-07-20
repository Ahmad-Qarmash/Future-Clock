const { test, expect } = require("@playwright/test");

const REQUIRED_FEATURE_CARDS = [
  "Clock",
  "World Clock",
  "Alarms",
  "Timer",
  "Stopwatch",
];

test.describe("Future Clock landing page", () => {
  test("page loads with 200 status", async ({ page, request }) => {
    const response = await request.get("/");
    expect(response.status()).toBe(200);
  });

  test("title contains 'Future Clock'", async ({ page }) => {
    await page.goto("/");
    await expect(page).toHaveTitle(/Future Clock/i);
  });

  test("hero section is visible with title and tagline", async ({ page }) => {
    await page.goto("/");
    const hero = page.locator("section.hero");
    await expect(hero).toBeVisible();
    await expect(hero.getByRole("heading", { level: 1 })).toContainText(/Future/);
    await expect(hero.getByRole("heading", { level: 1 })).toContainText(/Clock/);
    await expect(hero).toContainText(/Neon time, in your pocket/i);
  });

  test("renders all 5 feature cards", async ({ page }) => {
    await page.goto("/");
    const featureGrid = page.locator("#features .feature-grid");
    await expect(featureGrid).toBeVisible();
    const cards = page.locator("#features .feature-grid .feature-card");
    await expect(cards).toHaveCount(5);
    for (const name of REQUIRED_FEATURE_CARDS) {
      await expect(
        page.locator("#features .feature-grid").getByText(name, { exact: true })
      ).toBeVisible();
    }
  });

  test("GitHub link is present and points to the project repo", async ({ page }) => {
    await page.goto("/");
    const ghLinks = page.getByTestId("github-link");
    await expect(ghLinks).toBeVisible();
    await expect(ghLinks).toHaveAttribute("href", /github\.com\/Ahmad-Qarmash\/Future-Clock/);
  });

  test("page has no console errors", async ({ page }) => {
    const errors = [];
    page.on("pageerror", (err) => errors.push(err.message));
    page.on("console", (msg) => {
      if (msg.type() === "error") errors.push(msg.text());
    });
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    expect(errors, errors.join("\n")).toEqual([]);
  });

  test("no 404 network responses for required resources", async ({ page }) => {
    const failures = [];
    page.on("response", (response) => {
      const status = response.status();
      const url = response.url();
      if (status === 404) {
        failures.push(`${status} ${url}`);
      } else if (status >= 400) {
        // 4xx for the GitHub outbound link is fine (we don't follow
        // offsite links in this test). Skip anything that isn't the
        // local server.
        if (url.startsWith("http://localhost:4173") || url.startsWith("/")) {
          failures.push(`${status} ${url}`);
        }
      }
    });
    await page.goto("/");
    await page.waitForLoadState("networkidle");
    expect(failures, failures.join("\n")).toEqual([]);
  });

  test("uses semantic landmarks", async ({ page }) => {
    await page.goto("/");
    await expect(page.locator("header.site-header")).toHaveCount(1);
    await expect(page.locator("main")).toHaveCount(1);
    await expect(page.locator("footer.site-footer")).toHaveCount(1);
    await expect(page.locator('nav[aria-label="Primary"]')).toBeVisible();
  });
});
