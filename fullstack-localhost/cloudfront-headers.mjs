// Prints the Caddy `header` block for the CloudFront response headers policy, the same headers
// `vite preview` sends (frontend/vite.config.ts). HSTS is left out because the stack is plain HTTP.
// Usage: node cloudfront-headers.mjs <response-headers-policy.json> > cloudfront-headers.caddy
import { readFileSync } from "node:fs";

const [policyPath] = process.argv.slice(2);
const { SecurityHeadersConfig: security, RemoveHeadersConfig: removed } = JSON.parse(
  readFileSync(policyPath, "utf8"),
);

const headers = {
  "Content-Security-Policy": security.ContentSecurityPolicy.ContentSecurityPolicy,
  "X-Content-Type-Options": "nosniff",
  "X-Frame-Options": security.FrameOptions.FrameOption,
  "Referrer-Policy": security.ReferrerPolicy.ReferrerPolicy,
};

const lines = [
  "header {",
  // After the handler runs, so the policy replaces the backend's own values, as Override does.
  "\tdefer",
  ...Object.entries(headers).map(([name, value]) => `\t${name} ${JSON.stringify(value)}`),
  ...removed.Items.map(({ Header }) => `\t-${Header}`),
  "}",
];
process.stdout.write(`${lines.join("\n")}\n`);
