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
const OK_CODE = "COMMON_200_000";

export class ApiError extends Error {
  readonly code: string;
  readonly status: number;
  readonly traceId: string | undefined;
  constructor(args: { code: string; message: string; status?: number; traceId?: string }) {
    super(args.message);
    this.name = "ApiError";
    this.code = args.code;
    this.status = args.status ?? 200;
    this.traceId = args.traceId;
  }
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
