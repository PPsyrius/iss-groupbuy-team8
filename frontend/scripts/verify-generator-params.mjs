// The TypeScript-7-compatible snapshot sorts below 0.97.3 in advisory version matching.
// Exercise the installed template against GHSA-hhx9-57xq-r5rw instead of suppressing it.
import assert from "node:assert/strict";
import { readFileSync } from "node:fs";
import { stripTypeScriptTypes } from "node:module";

const entry = import.meta.resolve("@hey-api/openapi-ts");
const template = readFileSync(new URL("./clients/core/params.ts", entry), "utf8");
const javascript = stripTypeScriptTypes(template);
const { buildClientParams } = await import(
  `data:text/javascript;base64,${Buffer.from(javascript).toString("base64")}`
);

for (const slot of ["body", "headers", "path", "query"]) {
  const payload = { injectedPrivilege: true };
  const params = buildClientParams(
    [
      {
        q: "hello",
        [`$${slot}_legitimate`]: "safe",
        [`$${slot}___proto__`]: payload,
      },
    ],
    [{ args: [{ in: "query", key: "q" }] }],
  );
  assert.equal(params.query.q, "hello");
  assert.equal(params[slot].legitimate, "safe");
  assert.equal(Object.getPrototypeOf(params[slot]), null, `${slot}: prototype substitution`);
  assert.equal(params[slot].injectedPrivilege, undefined, `${slot}: inherited attacker data`);
  assert.ok(Object.hasOwn(params[slot], "__proto__"), `${slot}: inert own property expected`);
  assert.equal(params[slot].__proto__, payload);
}
console.log("Installed API generator template rejects prototype substitution in all four slots");
