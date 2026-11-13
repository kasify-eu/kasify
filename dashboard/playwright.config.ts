import { defineConfig, devices } from "@playwright/test";
import { MOCK_SERVER_PORT } from "./tests/e2e/mock-server";

const mockApiUrl = `http://localhost:${MOCK_SERVER_PORT}`;

// Tests run against their own dev server on a dedicated port, started with the
// mock API env. Keeping this off the default dev port (5173) means the suite
// ignores any non-mock `npm run dev` server you have running — it never reuses
// it and never fights it for the port.
const TEST_PORT = 5174;
const baseURL = `http://localhost:${TEST_PORT}`;

export default defineConfig({
  testDir: "./tests/e2e",
  fullyParallel: true,
  forbidOnly: !!process.env.CI,
  retries: process.env.CI ? 2 : 0,
  reporter: process.env.CI ? "github" : "list",
  globalSetup: "./tests/e2e/global-setup.ts",
  use: {
    baseURL,
    trace: "on-first-retry",
  },
  projects: [{ name: "chromium", use: { ...devices["Desktop Chrome"] } }],
  webServer: {
    command: `npm run dev -- --port ${TEST_PORT}`,
    url: baseURL,
    reuseExistingServer: !process.env.CI,
    timeout: 120_000,
    env: { VITE_KASIFY_API_URL: mockApiUrl },
  },
});
