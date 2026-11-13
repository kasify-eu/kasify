import { test, expect } from "@playwright/test";

test("landing page loads for unauthenticated visitors", async ({ page }) => {
  await page.goto("/");
  await expect(page).toHaveTitle(/Kasify/);
  await expect(page.getByRole("heading", { level: 1 })).toBeVisible();
});
