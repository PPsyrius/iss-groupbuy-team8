import type { TeamTermsRequest, TeamTermsView } from "@/api/generated/types.gen";

import { type Refusal, refusalOf } from "./listing-errors";

// The group terms panel's values (admin plan A3-04, §11 D6): seats per bloc, and the bloc's
// lifetime as hours and minutes, which the request sends as `lifetimeSeconds`. A cleared or never
// set number reads NaN, as a cleared NumberField does, and the schema asks for it.
export interface TeamTermsFormValues {
  seats: number;
  hours: number;
  minutes: number;
}

const HOUR = 3600;
const MINUTE = 60;

export function termsFormDefaults(terms: TeamTermsView | undefined): TeamTermsFormValues {
  const lifetime = terms?.lifetimeSeconds ?? null;
  return {
    seats: terms?.targetSize ?? Number.NaN,
    hours: lifetime === null ? Number.NaN : Math.floor(lifetime / HOUR),
    minutes: lifetime === null ? 0 : Math.floor((lifetime % HOUR) / MINUTE),
  };
}

export function lifetimeSecondsOf(values: Pick<TeamTermsFormValues, "hours" | "minutes">): number {
  return values.hours * HOUR + values.minutes * MINUTE;
}

// The terms write carries the listing's version (the terms are part of the listing, #242). The
// panel never sends `teamPrice`: the bloc price rule panel owns rules.
export function toTermsRequest(
  values: TeamTermsFormValues,
  expectedVersion: number,
): TeamTermsRequest {
  return {
    expectedVersion,
    targetSize: values.seats,
    lifetimeSeconds: lifetimeSecondsOf(values),
  };
}

// The panel's chip from `configured`: both terms set and inside the bounds. Configured terms are
// needed to start a bloc but not enough (an on-sale listing with stock and a rule in force are the
// rest), so the chip names only what the terms decide.
export function termsChip(terms: Pick<TeamTermsView, "configured"> | undefined): {
  label: string;
  color: "success" | "default";
} {
  return terms?.configured
    ? { label: "Ready to start blocs", color: "success" }
    : { label: "Not set", color: "default" };
}

// The backend's field paths and the form field each lands on; the lifetime's message goes under
// Hours, the first of its two inputs.
const fieldOf: Record<string, keyof TeamTermsFormValues> = {
  targetSize: "seats",
  lifetimeSeconds: "hours",
};

// A refused terms write reads as a listing write does (a stale version is the editor's conflict,
// since the terms carry the listing version), with the backend's field paths moved onto the form.
export function termsRefusalOf(error: unknown): Refusal {
  const refusal = refusalOf(
    error,
    "The server refused these terms. Check the fields and try again.",
  );
  if (refusal.kind !== "fields") return refusal;
  return {
    kind: "fields",
    fields: Object.fromEntries(
      Object.entries(refusal.fields).map(([path, message]) => [fieldOf[path] ?? path, message]),
    ),
  };
}
