import {
  fromAbsolute,
  toCalendarDate,
  toZoned as atZone,
  type ZonedDateTime,
} from "@internationalized/date";

// Every admin time is a Singapore time, whatever the admin's device zone. The rule pickers hold
// `ZonedDateTime`s in this zone (the type HeroUI's DatePicker edits); the wire holds instants.
const ZONE = "Asia/Singapore";
const MINUTE = 60_000;

// The console's long Singapore date, as the 4a.1 date line prints it ("Wednesday 16 September
// 2026 · all times SGT").
// en-US parts, assembled by hand: en-SG/en-GB print "Wednesday, 16 September 2026" with a comma.
const longDay = new Intl.DateTimeFormat("en-US", {
  timeZone: ZONE,
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
});

function partsOf(format: Intl.DateTimeFormat, ms: number) {
  const parts = format.formatToParts(new Date(ms));
  return (type: Intl.DateTimeFormatPartTypes) => parts.find((p) => p.type === type)?.value ?? "";
}

export function formatSgtDate(ms: number): string {
  const part = partsOf(longDay, ms);
  return `${part("weekday")} ${part("day")} ${part("month")} ${part("year")}`;
}

// The contract's instant as Singapore wall time, or null when it is absent or unreadable.
export function toZoned(iso: string | undefined): ZonedDateTime | null {
  const ms = iso ? Date.parse(iso) : Number.NaN;
  return Number.isNaN(ms) ? null : fromAbsolute(ms, ZONE);
}

// What the backend reads: an ISO instant in UTC.
export function toInstant(zoned: ZonedDateTime): string {
  return zoned.toDate().toISOString();
}

// Now in Singapore, to the minute (the pickers' granularity).
export function sgtMinute(ms: number): ZonedDateTime {
  return fromAbsolute(Math.floor(ms / MINUTE) * MINUTE, ZONE);
}

// Midnight SGT at the start of the Singapore day `days` after the day `ms` falls on.
export function sgtDayStart(ms: number, days: number): ZonedDateTime {
  return atZone(toCalendarDate(fromAbsolute(ms, ZONE)).add({ days }), ZONE);
}

// "17 Sep 2026, 12.00 am", the 4b.4 picker's day and time with the storefront's "9.30 pm".
const minuteFormat = new Intl.DateTimeFormat("en-US", {
  timeZone: ZONE,
  day: "numeric",
  month: "short",
  year: "numeric",
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

export function formatSgtMinute(ms: number): string {
  const part = partsOf(minuteFormat, ms);
  return `${part("day")} ${part("month")} ${part("year")}, ${part("hour")}.${part("minute")} ${part("dayPeriod").toLowerCase()}`;
}

// The 4b.3 window line. `validUntil` is exclusive, so the end shows as its last valid minute: a
// rule ending at 1 Oct 00:00 reads "30 Sep 2026, 11.59 pm" (admin plan §6 Q13). Null when either
// end is unreadable.
export function formatWindow(
  validFrom: string | undefined,
  validUntil: string | undefined,
): string | null {
  const from = toZoned(validFrom);
  const until = toZoned(validUntil);
  if (!from || !until) return null;
  return `Valid ${formatSgtMinute(from.toDate().getTime())} → ${formatSgtMinute(until.toDate().getTime() - MINUTE)} SGT`;
}
