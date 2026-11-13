// Post-build SEO fixes that Zudoku can't express natively for this manually
// bilingual site (the /en and /es trees are plain folders, not native i18n):
//
//   1. Sets <html lang="es"> on Spanish pages. Zudoku hardcodes lang="en" in
//      its build shell (dist/**/*.html), so this is corrected after the build.
//   2. Injects reciprocal hreflang alternate links between the mirrored /en and
//      /es pages (plus x-default -> English), so search engines serve the right
//      language instead of treating the translations as duplicates.
//
// Canonical tags and sitemap.xml are handled by Zudoku itself via
// canonicalUrlOrigin / sitemap in zudoku.config.tsx — not here.
//
// Runs after `zudoku build` (see the "build" script in package.json). Safe to
// run repeatedly: it skips pages that already carry hreflang links.

import { existsSync } from "node:fs";
import { readdir, readFile, writeFile } from "node:fs/promises";
import { fileURLToPath } from "node:url";
import { join, relative } from "node:path";

const ORIGIN = "https://docs.kasify.eu";
const DIST = fileURLToPath(new URL("../dist", import.meta.url));

async function* walkHtml(dir) {
  for (const entry of await readdir(dir, { withFileTypes: true })) {
    const full = join(dir, entry.name);
    if (entry.isDirectory()) yield* walkHtml(full);
    else if (entry.name.endsWith(".html")) yield full;
  }
}

// dist/en/setup/tasks.html -> { locale: "en", rel: "en/setup/tasks" }
function localeOf(file) {
  const rel = relative(DIST, file).replace(/\\/g, "/").replace(/\.html$/, "");
  const match = rel.match(/^(en|es)\//);
  return match ? { locale: match[1], rel } : null;
}

let langFixed = 0;
let hreflangAdded = 0;

for await (const file of walkHtml(DIST)) {
  const info = localeOf(file);
  if (!info) continue; // only the localized doc pages get these tags

  const { locale, rel } = info;
  const other = locale === "en" ? "es" : "en";
  const otherRel = rel.replace(new RegExp(`^${locale}/`), `${other}/`);

  let html = await readFile(file, "utf8");
  let changed = false;

  // 1. Correct the language attribute on Spanish pages.
  if (locale === "es" && html.includes('<html lang="en"')) {
    html = html.replace('<html lang="en"', '<html lang="es"');
    changed = true;
    langFixed++;
  }

  // 2. Add hreflang alternates only when the page exists in both locales.
  const hasCounterpart = existsSync(join(DIST, `${otherRel}.html`));
  if (hasCounterpart && !html.includes('hreflang="')) {
    const enPath = locale === "en" ? rel : otherRel;
    const esPath = locale === "es" ? rel : otherRel;
    const alternates =
      `<link rel="alternate" hreflang="en" href="${ORIGIN}/${enPath}" />` +
      `<link rel="alternate" hreflang="es" href="${ORIGIN}/${esPath}" />` +
      `<link rel="alternate" hreflang="x-default" href="${ORIGIN}/${enPath}" />`;
    html = html.replace("</head>", `${alternates}</head>`);
    changed = true;
    hreflangAdded++;
  }

  if (changed) await writeFile(file, html);
}

console.log(
  `[seo-postprocess] lang="es" applied to ${langFixed} page(s); ` +
    `hreflang alternates added to ${hreflangAdded} page(s).`,
);
