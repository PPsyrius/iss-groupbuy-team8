// One place that knows the backend Result<T> envelope. Generated types stay envelope-shaped;
// queries pass `select: unwrapResult` (or `select: (r) => unwrapResult(r).items`).
export interface ResultEnvelope<T> {
  code?: string;
  data?: T;
  message?: string;
  errors?: unknown;
  traceId?: string;
}

// Confirmed against backend/blocbuy-domain/.../common/exception/CommonErrorCode.java:
// CommonErrorCode.SUCCESS = "COMMON_200_000" backs the Result envelope's success factory.
// Re-exported: the 401 interceptor (client.ts) settles the `me` query on an anonymous envelope
// carrying this code.
export const OK_CODE = "COMMON_200_000";

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly traceId: string | undefined;
  // The envelope's `errors` as sent (a `FieldViolation[]` on a 400 validation failure); left
  // `unknown` because only `fieldViolations` below reads it, and it checks every entry.
  readonly errors: unknown;
  constructor(args: {
    code: string;
    message: string;
    status?: number;
    traceId?: string;
    errors?: unknown;
  }) {
    super(args.message);
    this.name = "ApiError";
    this.code = args.code;
    this.status = args.status ?? 200;
    this.traceId = args.traceId;
    this.errors = args.errors;
  }
}

// Field-level messages from a 400, keyed by field for a form's `setFieldMeta`. Only
// `{field: string, message: string}` entries count; the admin write DTOs nest the content under
// `listing`, so a `listing.` prefix is stripped to match the form's own field names.
export function fieldViolations(error: unknown): Record<string, string> {
  const out: Record<string, string> = {};
  if (!(error instanceof ApiError) || !Array.isArray(error.errors)) return out;
  for (const entry of error.errors as unknown[]) {
    if (!entry || typeof entry !== "object") continue;
    const { field, message } = entry as { field?: unknown; message?: unknown };
    if (typeof field !== "string" || typeof message !== "string") continue;
    out[field.replace(/^listing\./, "")] = message;
  }
  return out;
}

export function unwrapResult<T>(
  envelope: ResultEnvelope<T>,
  opts: { allowVoid?: boolean } = {},
): T {
  const { allowVoid = true } = opts;
  if (envelope.code !== OK_CODE) {
    throw new ApiError({
      code: envelope.code ?? "UNKNOWN",
      message: envelope.message ?? "Request failed",
      ...(envelope.traceId === undefined ? {} : { traceId: envelope.traceId }),
      ...(envelope.errors === undefined ? {} : { errors: envelope.errors }),
    });
  }
  if (envelope.data === undefined) {
    if (!allowVoid) {
      throw new ApiError({ code: "EMPTY_DATA", message: "Envelope has no data" });
    }
    // allowVoid callers (mutations returning nothing) declare T = void; the envelope has no
    // data field to check against, so this cast is the one deliberate trust boundary here.
    // oxlint-disable-next-line typescript/no-unsafe-type-assertion
    return undefined as T;
  }
  return envelope.data;
}
