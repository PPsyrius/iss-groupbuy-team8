import policy from "../../../../deployment/frontend/cloudfront/response-headers-policy.json";

// Decision record: frontend/docs/adr/0015-admin-console-shell-and-guard.md
// Use the deployed-policy source, not a second list that can drift from CSP.
const imageSources = policy.SecurityHeadersConfig.ContentSecurityPolicy.ContentSecurityPolicy.split(
  ";",
)
  .map((directive) => directive.trim().split(/\s+/))
  .find(([name]) => name === "img-src")
  ?.slice(1);
if (!imageSources) throw new Error("The image CSP directive is missing");
const origins = new Set(imageSources.filter((source) => source !== "'self'"));
const permitsSelf = imageSources.includes("'self'");

export const imageHostHelp = `Approved image sources: ${[
  ...(permitsSelf ? ["this site's HTTPS origin"] : []),
  ...origins,
].join(", ")}.`;
export const imageHostError =
  "Use an approved image host. Replace or remove unsupported image URLs before saving.";

export function isApprovedImageUrl(value: string): boolean {
  if (!URL.canParse(value)) return false;
  const url = new URL(value);
  if (url.protocol !== "https:" || url.username !== "" || url.password !== "") return false;
  return origins.has(url.origin) || (permitsSelf && url.origin === globalThis.location.origin);
}
