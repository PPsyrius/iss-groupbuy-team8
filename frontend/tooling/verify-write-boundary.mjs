// Actual filesystem checks, not just Dockerfile source assertions.
import assert from "node:assert/strict";
import { closeSync, openSync, rmSync, statSync, writeFileSync } from "node:fs";
import { join } from "node:path";

assert.notEqual(process.getuid(), 0, "Browser tooling must not run as root");
const denied = (error) => error.code === "EACCES" || error.code === "EROFS";
const inputs = [
  "/repo/contracts/openapi.json",
  "/repo/deployment/frontend/cloudfront/response-headers-policy.json",
  "/repo/frontend/package.json",
  "/repo/frontend/node_modules/.pnpm-workspace-state-v1.json",
  "/repo/frontend/e2e/security-headers.spec.ts",
  "/repo/frontend/scripts/verify-csp-styles.mjs",
  "/repo/frontend/scripts/clean-api-output.mjs",
  "/repo/frontend/src/api/hey-api.ts",
  "/repo/frontend/vite.config.ts",
  "/repo/frontend/dist/index.html",
  "/repo/frontend/tooling/verify-write-boundary.mjs",
];
for (const input of inputs) {
  assert.equal(statSync(input).uid, 0, `${input} must remain root-owned`);
  closeSync(openSync(input, "r"));
  assert.throws(() => closeSync(openSync(input, "r+")), denied, `${input} must reject writes`);
}
const protectedDirectories = [
  "/repo",
  "/repo/deployment/frontend/cloudfront",
  "/repo/frontend",
  "/repo/frontend/src/api",
  "/repo/frontend/e2e",
  "/repo/frontend/dist",
  "/repo/frontend/node_modules",
  process.env.PLAYWRIGHT_BROWSERS_PATH,
];
for (const directory of protectedDirectories) {
  assert.equal(statSync(directory).uid, 0, `${directory} must remain root-owned`);
  const probe = join(directory, `.write-boundary-${process.pid}`);
  assert.throws(
    () => {
      writeFileSync(probe, "permission probe", { flag: "wx" });
      rmSync(probe);
    },
    denied,
    `${directory} must reject new files`,
  );
}
const outputs = [
  "/repo/frontend/src/api/generated",
  "/repo/frontend/node_modules/.vite",
  "/repo/frontend/node_modules/.vite-temp",
  "/evidence",
];
for (const directory of outputs) {
  const probe = join(directory, `.write-boundary-${process.pid}`);
  writeFileSync(probe, "permission probe", { flag: "wx" });
  rmSync(probe);
}
console.log(
  JSON.stringify({
    uid: process.getuid(),
    protectedFiles: inputs.length,
    protectedDirectories: protectedDirectories.length,
    writableOutputs: outputs,
  }),
);
