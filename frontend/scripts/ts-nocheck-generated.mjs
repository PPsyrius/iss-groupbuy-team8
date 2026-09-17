// Marks every generated .ts file under src/api/generated with a leading
// `// @ts-nocheck` so tsc treats hey-api's output as library code, the same
// way lint/format/knip already exclude it. The hey-api nightly used here does
// not produce exactOptionalPropertyTypes-clean output; regenerating still
// gives us a working client, it just cannot be type-checked as if it were
// hand-authored app code.
import { readdirSync, readFileSync, statSync, writeFileSync } from "node:fs";
import { join, resolve } from "node:path";
import { fileURLToPath } from "node:url";

const BANNER = "// @ts-nocheck";

const scriptDir = fileURLToPath(new URL(".", import.meta.url));
const targetDir = resolve(scriptDir, "..", "src", "api", "generated");

function assertTargetExists(dir) {
  try {
    if (!statSync(dir).isDirectory()) {
      throw new Error(`not a directory: ${dir}`);
    }
  } catch (error) {
    console.error(`ts-nocheck-generated: generated directory not found: ${dir}`);
    console.error(String(error?.message ?? error));
    process.exit(1);
  }
}

function collectTsFiles(dir) {
  const files = [];
  for (const entry of readdirSync(dir, { withFileTypes: true })) {
    const fullPath = join(dir, entry.name);
    if (entry.isDirectory()) {
      files.push(...collectTsFiles(fullPath));
    } else if (entry.isFile() && fullPath.endsWith(".ts")) {
      files.push(fullPath);
    }
  }
  return files;
}

function ensureTsNocheck(filePath) {
  const content = readFileSync(filePath, "utf8");
  const firstLineEnd = content.indexOf("\n");
  const firstLine = firstLineEnd === -1 ? content : content.slice(0, firstLineEnd);
  if (firstLine === BANNER) {
    return false;
  }
  writeFileSync(filePath, `${BANNER}\n${content}`);
  return true;
}

assertTargetExists(targetDir);

const tsFiles = collectTsFiles(targetDir);
let updated = 0;
for (const filePath of tsFiles) {
  if (ensureTsNocheck(filePath)) {
    updated += 1;
  }
}

console.log(`ts-nocheck-generated: ${updated}/${tsFiles.length} files updated in ${targetDir}`);
