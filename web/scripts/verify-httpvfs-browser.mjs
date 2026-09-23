// Phase 2 verification (real browser, via Playwright's bundled Chromium):
// loads test-harness/index.html against the nginx container started for
// manual testing and confirms sql.js-httpvfs issues small HTTP range
// requests against morph.db -- not one full-file download -- and that
// buildMorphResponse() returns a correct result over that connection.
//
// Prereqs:
//   1. web/test-harness bundled into /tmp/morph-harness-dist (see below)
//   2. docker run -d --name morph-nginx-test -p 8091:8080 \
//        -v /tmp/morph-harness-dist:/usr/share/nginx/html:ro \
//        -v "$(pwd)/../deploy/nginx.conf:/etc/nginx/nginx.conf:ro" nginx:alpine
//
// Usage: node scripts/verify-httpvfs-browser.mjs
import { chromium } from "playwright";

const NGINX_URL = process.env.NGINX_URL ?? "http://127.0.0.1:8091";
const DB_SIZE_BYTES = 3_611_664_384;

async function main() {
  const browser = await chromium.launch();
  const page = await browser.newPage();

  let requestCount = 0;
  let rawBytesTransferred = 0;
  const requestLog = [];
  const coveredRanges = [];

  page.on("response", async (response) => {
    if (!response.url().endsWith("/morph.db")) {
      return;
    }
    requestCount += 1;
    const method = response.request().method();
    const range = response.request().headers()["range"] ?? "(no range)";
    const contentLength = Number(response.headers()["content-length"]) || 0;
    requestLog.push(`${method} ${response.status()} range=${range} content-length=${contentLength}`);
    // The first request is always a HEAD (sql.js-httpvfs probing the file
    // size before it knows how to address anything) -- Content-Length is
    // present on a HEAD response too, but no body is transferred, so it's
    // excluded from both byte counters, not just double-counted.
    if (method === "HEAD") {
      return;
    }
    rawBytesTransferred += contentLength;

    const match = /bytes=(\d+)-(\d+)/.exec(range);
    if (match) {
      coveredRanges.push([Number(match[1]), Number(match[2])]);
    }
  });

  function uniqueBytesCovered(ranges) {
    const sorted = [...ranges].sort((a, b) => a[0] - b[0]);
    let total = 0;
    let currentEnd = -1;
    for (const [start, end] of sorted) {
      const effectiveStart = Math.max(start, currentEnd + 1);
      if (end >= effectiveStart) {
        total += end - effectiveStart + 1;
        currentEnd = Math.max(currentEnd, end);
      }
    }
    return total;
  }

  page.on("console", (msg) => console.log(`[page] ${msg.text()}`));
  page.on("pageerror", (err) => console.error(`[page error] ${err}`));

  console.log(`loading ${NGINX_URL}/index.html ...`);
  await page.goto(`${NGINX_URL}/index.html`);

  // The harness runs its query as soon as the module loads; wait for its
  // final log line rather than polling arbitrarily.
  await page.waitForFunction(
    () => document.querySelector("#output")?.textContent?.includes('"lemmas"'),
    { timeout: 30_000 },
  );

  const outputText = await page.textContent("#output");
  console.log("\n--- page output ---");
  console.log(outputText);

  console.log("\n--- morph.db requests ---");
  requestLog.forEach((line, i) => console.log(`  #${i + 1}: ${line}`));

  const uniqueBytes = uniqueBytesCovered(coveredRanges);
  console.log(`\nrequests to morph.db: ${requestCount}`);
  console.log(`raw bytes transferred (sum of Content-Length, including re-fetched/overlapping ranges from the adaptive read-ahead heuristic): ${rawBytesTransferred}`);
  console.log(
    `unique bytes covered: ${uniqueBytes} (${((uniqueBytes / DB_SIZE_BYTES) * 100).toFixed(4)}% of the ${DB_SIZE_BYTES}-byte file)`,
  );

  await browser.close();

  if (uniqueBytes === 0 || uniqueBytes >= DB_SIZE_BYTES) {
    console.error("\nFAIL: didn't see partial range requests as expected.");
    process.exit(1);
  }
  if (!outputText?.includes('"is_winner": true')) {
    console.error("\nFAIL: query result looks wrong.");
    process.exit(1);
  }
  console.log("\nPASS: range requests confirmed, query result correct.");
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
