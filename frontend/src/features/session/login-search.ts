import * as v from "valibot";

const DEFAULT_RETURN_TO = "/account";

// A return path is only ever a same-origin relative path: one leading slash, not two (a
// protocol-relative URL), no scheme, and no backslash anywhere: the WHATWG URL parser treats
// `\` as `/` for http(s), so `/\evil.example` (`?returnTo=/%5Cevil.example`) would leave the
// origin through `redirect({ href })` (CodeRabbit r4045129508, CWE-601). Anything else falls
// back to the account page rather than failing the route, so a tampered link still lands
// somewhere sensible.
const sameOriginPath = v.pipe(v.string(), v.trim(), v.regex(/^\/(?![/\\])[^\s\\]*$/u));

export function safeReturnTo(value: unknown): string {
  return v.is(sameOriginPath, value) ? value.trim() : DEFAULT_RETURN_TO;
}

// `/login?returnTo=…` (the artboards' sign-in screen) and `/login?error={errorCode}` (the
// backend's failure landing per contracts/openapi.json, googleAuthorize / googleCallback).
export const loginSearchSchema = v.object({
  returnTo: v.optional(v.fallback(sameOriginPath, DEFAULT_RETURN_TO), DEFAULT_RETURN_TO),
  error: v.optional(
    v.pipe(
      v.optional(v.string(), ""),
      v.trim(),
      v.transform((s) => (s.length > 0 ? s : undefined)),
    ),
  ),
});
