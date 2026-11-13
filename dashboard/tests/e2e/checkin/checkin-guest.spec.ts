import { test, expect, type BrowserContext, type Page } from "@playwright/test";

async function useEnglish(context: BrowserContext) {
  await context.addCookies([
    { name: "kasify_locale", value: "en", url: "http://localhost" },
  ]);
}

// Navigate to a guest page and wait until it has hydrated. The form fetches
// /geo/countries from a client `useEffect`, so that response only fires after
// React has mounted on the client — a reliable signal that inputs are now
// controlled. Filling before hydration is unsafe: React discards the typed
// values when it reconciles the server-rendered markup.
async function gotoGuest(page: Page, code: string) {
  const hydrated = page
    .waitForResponse(
      (r) => r.url().includes("/geo/countries") && r.status() === 200,
      { timeout: 30_000 },
    )
    .catch(() => {});
  await page.goto(`/checkin/${code}/guest`);
  await hydrated;
}

// Commit a value in a Mantine Select / KeyboardSelect: focus it, type the
// search term so the dropdown filters, then click the matching option.
async function selectOption(
  page: Page,
  label: string,
  search: string,
  optionName = search,
) {
  // Target the input via the textbox role: an open Mantine dropdown also
  // carries the field's label on its listbox, so getByLabel would be ambiguous.
  const input = page.getByRole("textbox", { name: label, exact: true });
  await input.click();
  await input.fill(search);
  await page.getByRole("option", { name: optionName, exact: true }).click();
}

// Pick a date of birth via the three day/month/year selects (aria-labelled).
async function selectDateOfBirth(
  page: Page,
  day: string,
  month: string,
  year: string,
) {
  await selectOption(page, "Year", year);
  await selectOption(page, "Month", month);
  await selectOption(page, "Day", day);
}

// Fill a complete, valid adult guest with a foreign (French) passport. France
// keeps the form simple: no second surname, no Spanish document checksum, and
// a free-text residence city instead of the async province/city selects.
async function fillValidFranceGuest(page: Page) {
  await selectOption(page, "Nationality", "France");
  await page.getByLabel("First Name", { exact: true }).fill("John");
  await page.getByLabel("Surname", { exact: true }).fill("Doe");
  await selectDateOfBirth(page, "15", "Jun", "1990");
  await selectOption(page, "Gender", "Male");
  await selectOption(page, "Document Type", "Passport");
  await page.getByLabel("Document Number", { exact: true }).fill("AB123456");
  await page.getByLabel("Street Address", { exact: true }).fill("1 Rue de Rivoli");
  await page.getByLabel("City", { exact: true }).fill("Paris");
  await page.getByLabel("Postal Code", { exact: true }).fill("75001");
  const email = page.getByLabel("Email", { exact: true });
  await email.fill("john@example.com");
  // Ensure the last controlled value commits before the caller submits.
  await expect(email).toHaveValue("john@example.com");
}

const submitButton = (page: Page) =>
  page.getByRole("button", { name: "Sign and submit" });

test.beforeEach(async ({ context }) => {
  await useEnglish(context);
});

test.describe("checkin guest page — loading & states", () => {
  test("renders the registration form for an active booking", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");

    await expect(page).toHaveTitle(/Guest Registration/);
    await expect(page.getByRole("heading", { name: "Your details" })).toBeVisible();
    await expect(page.getByText("Casa Olivo")).toBeVisible();
    await expect(page.getByText("Guest 1 of 2")).toBeVisible();

    // Section headers
    await expect(page.getByRole("heading", { name: "About you" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Your ID" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "Where you live" })).toBeVisible();
    await expect(page.getByRole("heading", { name: "How to reach you" })).toBeVisible();
  });

  test("step counter reflects already-registered guests", async ({ page }) => {
    await gotoGuest(page, "MINORS");
    await expect(page.getByText("Guest 2 of 3")).toBeVisible();
  });

  test("shows a not-found state for an unknown booking code", async ({ page }) => {
    await page.goto("/checkin/UNKNOWN/guest");
    await expect(
      page.getByRole("heading", { name: "Booking not found" }),
    ).toBeVisible();
  });

  test("redirects to the welcome page when the check-in is completed", async ({
    page,
  }) => {
    await page.goto("/checkin/COMPLETED/guest");
    await expect(page).toHaveURL(/\/checkin\/COMPLETED$/);
    await expect(page.getByText("Check-in complete!")).toBeVisible();
  });

  test("redirects to the welcome page when the check-in is closed", async ({
    page,
  }) => {
    await page.goto("/checkin/CLOSED/guest");
    await expect(page).toHaveURL(/\/checkin\/CLOSED$/);
  });
});

test.describe("checkin guest page — conditional sections", () => {
  test("second surname appears only for Spanish nationals", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");

    await expect(page.getByLabel("Second Surname", { exact: true })).toHaveCount(0);

    await selectOption(page, "Nationality", "Spain");
    await expect(
      page.getByLabel("Second Surname", { exact: true }),
    ).toBeVisible();
  });

  test("Spanish residence shows province/city selects, foreign shows free text", async ({
    page,
  }) => {
    await gotoGuest(page, "ACTIVE");

    // Spain auto-fills residence as Spain → province select appears. Target the
    // input by role: Mantine keeps the (hidden) listbox mounted, so getByLabel
    // would match both the input and the listbox.
    const province = page.getByRole("textbox", { name: "Province", exact: true });
    await selectOption(page, "Nationality", "Spain");
    await expect(province).toBeVisible();

    // Switching nationality does not change an already-set residence, so set
    // the residence country directly to France to exercise the free-text path.
    await selectOption(page, "Country of Residence", "France");
    await expect(province).toHaveCount(0);
  });

  test("documentation hidden and parentesco shown for a minor", async ({ page }) => {
    await gotoGuest(page, "MINORS");

    // A 6-year-old: under 14 (no ID section) and under 18 (parentesco shown).
    await selectDateOfBirth(page, "15", "Jun", "2020");

    await expect(page.getByRole("heading", { name: "Your ID" })).toHaveCount(0);
    await expect(
      page.getByRole("heading", { name: "Family relationship" }),
    ).toBeVisible();
  });

  test("contact section stays present for a minor", async ({ page }) => {
    await gotoGuest(page, "MINORS");

    // The contact details are required for every guest (SES rule), so the
    // "How to reach you" block must remain even after the ID section is hidden
    // for a 6-year-old.
    await selectDateOfBirth(page, "15", "Jun", "2020");

    await expect(page.getByRole("heading", { name: "Your ID" })).toHaveCount(0);
    await expect(
      page.getByRole("heading", { name: "How to reach you" }),
    ).toBeVisible();
  });
});

test.describe("checkin guest page — validation", () => {
  test("empty submit opens the validation modal with field errors", async ({
    page,
  }) => {
    await gotoGuest(page, "ACTIVE");
    await submitButton(page).click();

    await expect(page.getByText("Almost there!")).toBeVisible();
    await expect(page.getByText("This field is required").first()).toBeVisible();
    await expect(
      page.getByText("At least a phone or email is required").first(),
    ).toBeVisible();
  });

  test("rejects an invalid Spanish DNI", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");
    await selectOption(page, "Nationality", "Spain"); // auto-selects DNI
    // Correct format but wrong checksum letter (expected is Z).
    const docNumber = page.getByLabel("Document Number", { exact: true });
    await docNumber.fill("12345678A");
    // The inputs are controlled by React state; wait for the value to commit so
    // the submit handler validates the typed value, not a stale empty closure.
    await expect(docNumber).toHaveValue("12345678A");
    await submitButton(page).click();

    await expect(page.getByText("Invalid Spanish DNI")).toBeVisible();
  });

  test("rejects a malformed email", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");
    const email = page.getByLabel("Email", { exact: true });
    await email.fill("not-an-email");
    await expect(email).toHaveValue("not-an-email");
    await submitButton(page).click();

    await expect(page.getByText("Invalid email address")).toBeVisible();
  });

  test("rejects a non 5-digit Spanish postal code", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");
    await selectOption(page, "Nationality", "Spain"); // residence → Spain
    const postal = page.getByLabel("Postal Code", { exact: true });
    await postal.fill("123");
    await expect(postal).toHaveValue("123");
    await submitButton(page).click();

    await expect(
      page.getByText("Spanish postal code must be 5 digits"),
    ).toBeVisible();
  });
});

test.describe("checkin guest page — submission", () => {
  test("registers a guest and shows the success modal", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");
    await fillValidFranceGuest(page);
    await submitButton(page).click();

    await expect(page.getByText("Registration signed")).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Register next guest" }),
    ).toBeVisible();
  });

  test("shows the completion modal when the last guest is registered", async ({
    page,
  }) => {
    await gotoGuest(page, "LASTGUEST");
    await fillValidFranceGuest(page);
    await submitButton(page).click();

    await expect(page.getByText("Check-in complete!")).toBeVisible();
    await expect(
      page.getByRole("button", { name: "Back to check-in" }),
    ).toBeVisible();
  });

  test("surfaces a server duplicate-document error", async ({ page }) => {
    await gotoGuest(page, "DUPDOC");
    await fillValidFranceGuest(page);
    await submitButton(page).click();

    await expect(
      page.getByText("We couldn't complete your registration"),
    ).toBeVisible();
    await expect(
      page.getByText(
        "A guest with this document number has already been registered.",
      ),
    ).toBeVisible();
  });

  test("registers a Spanish national with DNI and residence", async ({ page }) => {
    await gotoGuest(page, "ACTIVE");

    await selectOption(page, "Nationality", "Spain"); // → DNI + Spain residence
    await page.getByLabel("First Name", { exact: true }).fill("Ana");
    await page.getByLabel("Surname", { exact: true }).fill("García");
    await page.getByLabel("Second Surname", { exact: true }).fill("López");
    await selectDateOfBirth(page, "20", "Mar", "1985");
    await selectOption(page, "Gender", "Female");
    await page.getByLabel("Document Number", { exact: true }).fill("12345678Z");
    await page.getByLabel("Support Number", { exact: true }).fill("ABC123456");
    await selectOption(page, "Province", "Madrid");
    await selectOption(page, "City", "Madrid");
    await page.getByLabel("Street Address", { exact: true }).fill("Calle Mayor 1");
    await page.getByLabel("Postal Code", { exact: true }).fill("28013");
    const email = page.getByLabel("Email", { exact: true });
    await email.fill("ana@example.com");
    await expect(email).toHaveValue("ana@example.com");

    await submitButton(page).click();

    await expect(page.getByText("Registration signed")).toBeVisible();
  });

  test("register-next resets personal fields but keeps nationality", async ({
    page,
  }) => {
    await gotoGuest(page, "ACTIVE");
    await fillValidFranceGuest(page);
    await submitButton(page).click();

    await page.getByRole("button", { name: "Register next guest" }).click();

    await expect(page.getByText("Registration signed")).toHaveCount(0);
    await expect(page.getByLabel("First Name", { exact: true })).toHaveValue("");
    await expect(
      page.getByRole("textbox", { name: "Nationality", exact: true }),
    ).toHaveValue("France");
  });
});
