import { ApiError, conflictVersion, fieldViolations } from "@/api/envelope";

// What a refused listing write means for the screen (admin plan A1-08): the code, never the raw
// code itself, decides where the admin reads about it. Codes are the backend's
// `CatalogErrorCode` (blocbuy-domain, com.blocbuy.catalog.exception) and `CommonErrorCode`.
// A team-terms write elsewhere also bumps the listing version (#242), so it arrives here as the
// ordinary `CATALOG_412_002`.
export type Refusal =
  // 412: someone saved first. The editor shows the conflict banner; only Reload resolves it. The
  // server's version comes with the 412 (#286); `null` when it did not, so the page asks.
  | { kind: "conflict"; serverVersion: number | null }
  // 403: the admin role is gone. The route boundary shows Access denied.
  | { kind: "forbidden" }
  // 401: the session ended. The form stays as typed and offers the way to sign in again.
  | { kind: "session" }
  | { kind: "fields"; fields: Record<string, string> }
  | { kind: "message"; message: string };

const messages: Record<string, string> = {
  CATALOG_409_002: "This listing changed state. Reload to see what you can do now.",
  CATALOG_409_004: "Not available. This draft already has price or bloc history.",
};

// The interceptor sets the HTTP status; an envelope unwrapped from a 200 carries only the code,
// so both are read.
function statusOf(error: ApiError): number {
  const fromCode = /^[A-Z]+_(\d{3})_/.exec(error.code)?.[1];
  return fromCode ? Number(fromCode) : error.status;
}

export function refusalOf(error: unknown): Refusal {
  if (!(error instanceof ApiError))
    return { kind: "message", message: "Something went wrong on our side. Try again." };
  if (error.code === "CATALOG_412_002")
    return { kind: "conflict", serverVersion: conflictVersion(error) };
  const message = messages[error.code];
  if (message) return { kind: "message", message };
  const status = statusOf(error);
  if (status === 403) return { kind: "forbidden" };
  if (status === 401) return { kind: "session" };
  const fields = fieldViolations(error);
  if (Object.keys(fields).length > 0) return { kind: "fields", fields };
  if (status >= 400 && status < 500)
    return {
      kind: "message",
      message: "The server refused this listing. Check the fields and try again.",
    };
  return { kind: "message", message: "Something went wrong on our side. Try again." };
}
