// Dependency upgrades must not silently invalidate the edge's reviewed static style hashes.
// Decision record: frontend/docs/adr/0016-hashed-styles-and-cross-origin-policies.md
import assert from "node:assert/strict";
import { createHash } from "node:crypto";
import { readFileSync } from "node:fs";
import { createRequire } from "node:module";
import { pathToFileURL } from "node:url";

const require = createRequire(import.meta.url);
// Inspect the dependency actually used by HeroUI, not stale versions in pnpm's store.
// react-aria stays transitive; knip ignores only this audit-only dependency lookup.
const ariaEntry = require.resolve("react-aria", { paths: [require.resolve("@heroui/react")] });
const ariaPrivate = new URL("../private/", pathToFileURL(ariaEntry));
const policy = JSON.parse(
  readFileSync(
    new URL("../../deployment/frontend/cloudfront/response-headers-policy.json", import.meta.url),
    "utf8",
  ),
);
const csp = policy.SecurityHeadersConfig.ContentSecurityPolicy.ContentSecurityPolicy;
// Exact reviewed permissions, independent of the dependency being inspected.
// This is a targeted drift check of two known injections, not a vendor-code audit.
const reviewedHashes = [
  "'sha256-38RhXrc7EdReTKsOm23ZPOCUgniTUUcjky8QOOrQx6o='",
  "'sha256-gYiS/BvZvRcK27JIXTuwhZ3hs2+VJ1X+2gUlE+farlg='",
];
const styleDirectives = csp
  .split(";")
  .map((x) => x.trim().split(/\s+/))
  .map(([name, ...sources]) => [name.toLowerCase(), ...sources])
  .filter(([name]) => name.startsWith("style-src"));
assert.deepEqual(
  styleDirectives,
  [
    ["style-src", "'self'", "'report-sample'", ...reviewedHashes],
    ["style-src-attr", "'none'"],
  ],
  "Style CSP must contain exactly the reviewed sources, hashes and attribute denial, without duplicate or overriding directives",
);

for (const module of ["interactions/usePress.mjs", "overlays/usePreventScroll.mjs"]) {
  const source = readFileSync(new URL(module, ariaPrivate), "utf8");
  const blocks = [...source.matchAll(/style\.textContent\s*=\s*`([\s\S]*?)`\.trim\(\)/g)];
  assert.equal(blocks.length, 1, `Review changed style injection in ${module}`);
  let text = blocks[0][1];
  // Resolve the sole known constant without evaluating third-party source.
  if (module.startsWith("interactions/")) {
    const attribute = source.match(/const ([\w$]*PRESSABLE_ATTRIBUTE) = '([^']+)';/);
    assert.ok(attribute, "Review changed pressable selector");
    text = text.replace(`\${${attribute[1]}}`, attribute[2]);
  }
  assert.ok(!text.includes("${"), `Review unexpected dynamic stylesheet in ${module}`);
  const hash = `'sha256-${createHash("sha256").update(text.trim()).digest("base64")}'`;
  assert.equal(
    hash,
    reviewedHashes[module.startsWith("interactions/") ? 0 : 1],
    `${module} needs a reviewed CSP hash: ${hash}`,
  );
}
console.log("Installed React Aria styles match the reviewed CSP hashes");
