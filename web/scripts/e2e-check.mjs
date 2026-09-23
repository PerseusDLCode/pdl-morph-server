// One-off manual verification (not part of the regular test suite):
// drives the real Svelte app in a real browser (Playwright's bundled
// Chromium) against the Vite dev server, submits a word, and confirms
// the result renders without console errors.
//
// Usage: pnpm exec vite --port 5183 &   (with web/static/morph.db symlinked to clojure/morph.db)
//        node scripts/e2e-check.mjs
import { chromium } from "playwright";

const URL = process.env.APP_URL ?? "http://127.0.0.1:5183";

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  const consoleErrors = [];
  page.on("console", (msg) => {
    if (msg.type() === "error") {
      consoleErrors.push(msg.text());
    }
  });
  page.on("pageerror", (err) => consoleErrors.push(String(err)));

  // Deep link: a lookup should run straight from /morph's query params.
  await page.goto(`${URL}/morph?word=${encodeURIComponent("λόγος")}&language=grc`);
  await page.waitForSelector("details summary", { timeout: 30_000 });
  console.log(`deep-link headword: ${await page.textContent("details summary span")}`);

  // Form submission: should navigate to (and render) /morph?word=...
  await page.goto(URL);
  await page.fill('input[type="text"]', "λαμβάνω");
  await page.click('button[type="submit"]');
  await page.waitForURL(/\/morph\?word=/, { timeout: 10_000 });
  console.log(`form navigated to: ${decodeURIComponent(page.url())}`);

  await page.waitForSelector("details summary", { timeout: 30_000 });
  await page.waitForFunction(
    () => document.querySelectorAll("table tbody tr").length > 0,
    { timeout: 30_000 },
  );

  const headword = await page.textContent("details summary span");
  const parseCount = await page.locator("table tbody tr").count();
  const winnerCount = await page.locator("tr.bg-green-50").count();

  console.log(`headword: ${headword}`);
  console.log(`parse rows: ${parseCount}`);
  console.log(`winner-highlighted rows: ${winnerCount}`);

  // Confirm TEI-rendered dictionary markup made it into the DOM (it's
  // inside collapsed per-entry <details>, so "attached" not "visible").
  await page.waitForSelector(".tei-i, .tei-bibl, .tei-quote", {
    timeout: 10_000,
    state: "attached",
  });
  const teiElementCount = await page.locator(".tei-i, .tei-bibl, .tei-quote").count();
  console.log(`TEI-rendered dictionary elements found: ${teiElementCount}`);

  console.log(`console errors: ${consoleErrors.length}`);
  consoleErrors.forEach((e) => console.log(`  ${e}`));

  await browser.close();

  if (consoleErrors.length > 0 || parseCount === 0) {
    console.error("\nFAIL");
    process.exit(1);
  }
  console.log("\nPASS");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
