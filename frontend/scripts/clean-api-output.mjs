// Preserve the output directory so regeneration works with a read-only parent.
// Delete stale output, not reviewed API source or the generated directory itself.
import { lstatSync, mkdirSync, readdirSync, rmSync } from "node:fs";
import { join } from "node:path";
import { fileURLToPath } from "node:url";

const output = fileURLToPath(new URL("../src/api/generated", import.meta.url));
mkdirSync(output, { recursive: true });
if (lstatSync(output).isSymbolicLink()) {
  throw new Error("Generated API output must not be a symlink");
}
for (const entry of readdirSync(output)) {
  rmSync(join(output, entry), { recursive: true, force: true });
}
