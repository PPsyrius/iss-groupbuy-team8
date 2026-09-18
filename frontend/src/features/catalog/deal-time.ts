// Deal deadlines as the Listing Detail artboards print them (docs/design-screens.md §4.2): a live
// countdown that switches to mm:ss and "urgent" under one hour, and the absolute deadline in
// Singapore time with the local "9.30 pm" punctuation, "today" when it falls on the current SGT
// day. `expireTime` is the contract's ISO string; every date is rendered in Asia/Singapore
// regardless of the buyer's device zone, because the deadline is a Singapore deadline.

const zone = "Asia/Singapore";

export interface Countdown {
  text: string;
  urgent: boolean;
}

export function formatCountdown(deadlineMs: number, nowMs: number): Countdown {
  const ms = deadlineMs - nowMs;
  if (ms <= 0) return { text: "Ended", urgent: false };
  if (ms >= 3_600_000) {
    const minutes = Math.floor(ms / 60_000);
    return { text: `${Math.floor(minutes / 60)}h ${minutes % 60}m left`, urgent: false };
  }
  const seconds = Math.floor(ms / 1000);
  const mm = String(Math.floor(seconds / 60)).padStart(2, "0");
  const ss = String(seconds % 60).padStart(2, "0");
  return { text: `${mm}:${ss} left`, urgent: true };
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

export function formatDeadline(expireTime: string, nowMs: number): string {
  const date = new Date(expireTime);
  const isToday = dayKey.format(date) === dayKey.format(new Date(nowMs));
  const day = isToday ? "today" : sgDay(date);
  return `Ends ${day}, ${sgTime(date)} SGT`;
}

// Trust triptych "Bloc closes" value: "Thu, 9.30 pm".
export function formatDeadlineShort(expireTime: string): string {
  const date = new Date(expireTime);
  return `${part(date, "weekday")}, ${sgTime(date)}`;
}
