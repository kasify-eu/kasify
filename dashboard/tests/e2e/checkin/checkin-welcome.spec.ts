import { test, expect, type BrowserContext } from "@playwright/test";

async function useEnglish(context: BrowserContext) {
  await context.addCookies([
    {
      name: "kasify_locale",
      value: "en",
      url: "http://localhost",
    },
  ]);
}

test.describe("checkin welcome page", () => {
  test("renders active booking with start CTA", async ({ page, context }) => {
    await useEnglish(context);
    await page.goto("/checkin/ACTIVE");

    await expect(page).toHaveTitle(/Casa Olivo/);
    await expect(page.getByText("Welcome to Casa Olivo")).toBeVisible();
    await expect(page.getByText("Reference: ACTIVE")).toBeVisible();
    await expect(page.getByRole("button", { name: "Start Check-in" })).toBeVisible();
  });

  test("renders completed booking with success panel and no CTA", async ({ page, context }) => {
    await useEnglish(context);
    await page.goto("/checkin/COMPLETED");

    await expect(page.getByText("Check-in complete!")).toBeVisible();
    await expect(page.getByRole("button", { name: /Check-in/ })).toHaveCount(0);
  });

  test("renders not-found state for unknown booking code", async ({ page, context }) => {
    await useEnglish(context);
    await page.goto("/checkin/UNKNOWN");

    await expect(page.getByRole("heading", { name: "Booking not found" })).toBeVisible();
  });
});
