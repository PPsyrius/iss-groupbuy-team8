// Deal deadlines as the Listing Detail artboards print them (docs/design-screens.md §4.2): a live
// countdown that switches to mm:ss and "urgent" under one hour, and the absolute deadline in
// Singapore time with the local "9.30 pm" punctuation, "today" when it falls on the current SGT
// day. `expireTime` is the contract's ISO string; every date is rendered in Asia/Singapore
// regardless of the buyer's device zone, because the deadline is a Singapore deadline.

const zone = "Asia/Singapore";
const minuteMs = 60_000;
const hourMs = 3_600_000;
const dayMs = 86_400_000;
// Singapore has been UTC+8 all year since 1982, so its midnights are plain arithmetic on epoch
// ms; `clockReading` relies on it and its tests check it against the `Intl` formatters below.
const sgtOffsetMs = 8 * hourMs;

export interface Countdown {
  text: string;
  urgent: boolean;
}

export function formatCountdown(deadlineMs: number, nowMs: number): Countdown {
  const ms = deadlineMs - nowMs;
  if (ms <= 0) return { text: "Ended", urgent: false };
  if (ms >= hourMs) {
    const minutes = Math.floor(ms / minuteMs);
    return { text: `${Math.floor(minutes / 60)}h ${minutes % 60}m left`, urgent: false };
  }
  const seconds = Math.floor(ms / 1000);
  const mm = String(Math.floor(seconds / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");
  return { text: `${mm}:${ss} left`, urgent: true };
}

// The contract's `expireTime` as epoch ms, or null when it is absent or not a date the runtime
// can parse. Every caller goes through this: an invalid `Date` reaching `Intl.DateTimeFormat`
// throws a RangeError (CodeRabbit r4045102880), so the formatters below take ms, never strings.
export function parseDeadline(expireTime: string | undefined): number | null {
  if (!expireTime) return null;
  const ms = Date.parse(expireTime);
  return Number.isNaN(ms) ? null : ms;
}

// A deal with a parseable deadline in the past has ended; no (or no readable) deadline never ends.
export function hasEnded(expireTime: string | undefined, nowMs: number): boolean {
  const ms = parseDeadline(expireTime);
  return ms !== null && ms <= nowMs;
}

// The `now` a row with this deadline should render with, derived from the real clock `nowMs`.
// `useNow` hands it to the formatters in place of the clock, and `useSyncExternalStore` re-renders
// the row only when it changes, so it moves only when some text the row prints can change:
// - `undefined`: no deadline of its own (the quote sheet's countdown); every second.
// - `null`: no readable deadline, so no clock-relative text; once a day, at Singapore midnight.
// - Under an hour left: every second (the mm:ss countdown).
// - An hour or more left: when the whole minutes left change. Those flip a multiple of a minute
//   before the deadline, not on the wall-clock minute, so the reading is aligned to the deadline;
//   it is held to the last millisecond of the Singapore day so "Ends today" flips at midnight.
// - Ended: "Ended" is final, so only the "Ends today" label can still change, at midnight.
// Every formatter in this file and in `bloc-status.ts` prints for the reading what it prints for
// the real time (`deal-time.test.ts`). `nowMs` is floored to the second, as the clock always was.
export function clockReading(deadlineMs: number | null | undefined, nowMs: number): number {
  const second = Math.floor(nowMs / 1000) * 1000;
  if (deadlineMs === undefined) return second;
  const dayStart = Math.floor((second + sgtOffsetMs) / dayMs) * dayMs - sgtOffsetMs;
  if (deadlineMs === null) return dayStart;
  const left = deadlineMs - second;
  if (left <= 0) return Math.max(deadlineMs, dayStart);
  if (left < hourMs) return second;
  const minuteBoundary = deadlineMs - Math.floor(left / minuteMs) * minuteMs;
  return Math.min(minuteBoundary, dayStart + dayMs - 1);
}

const dayKey = new Intl.DateTimeFormat("en-CA", {
  timeZone: zone,
  year: "numeric",
  month: "2-digit",
  day: "2-digit",
});
// en-US parts, assembled by hand: en-SG/en-GB print "Thu, 17 Sept" while the artboard prints
// "Thu 17 Sep".
const dayParts = new Intl.DateTimeFormat("en-US", {
  timeZone: zone,
  weekday: "short",
  day: "numeric",
  month: "short",
});
function part(date: Date, type: Intl.DateTimeFormatPartTypes): string {
  return dayParts.formatToParts(date).find((p) => p.type === type)?.value ?? "";
}
function sgDay(date: Date): string {
  return `${part(date, "weekday")} ${part(date, "day")} ${part(date, "month")}`;
}
const timeLabel = new Intl.DateTimeFormat("en-SG", {
  timeZone: zone,
  hour: "numeric",
  minute: "2-digit",
  hour12: true,
});

// "9:30 pm" → "9.30 pm"; Intl may also emit a narrow no-break space before the meridiem.
function sgTime(date: Date): string {
  return timeLabel.format(date).replace(":", ".").replace(/\s+/g, " ").toLowerCase();
}

export function formatDeadline(deadlineMs: number, nowMs: number): string {
  const date = new Date(deadlineMs);
  const isToday = dayKey.format(date) === dayKey.format(new Date(nowMs));
  const day = isToday ? "today" : sgDay(date);
  return `Ends ${day}, ${sgTime(date)} SGT`;
}

// Trust triptych "Bloc closes" value: "Thu, 9.30 pm".
export function formatDeadlineShort(deadlineMs: number): string {
  const date = new Date(deadlineMs);
  return `${part(date, "weekday")}, ${sgTime(date)}`;
}

// My blocs paid line (§4.4 "Paid · 16 Sep, 7.46 pm"): day and month without the weekday.
export function formatDayTime(ms: number): string {
  const date = new Date(ms);
  return `${part(date, "day")} ${part(date, "month")}, ${sgTime(date)}`;
}

// My blocs Refunded row (§4.4 "closed 15 Sep"): day and month only.
export function formatDay(ms: number): string {
  const date = new Date(ms);
  return `${part(date, "day")} ${part(date, "month")}`;
}
