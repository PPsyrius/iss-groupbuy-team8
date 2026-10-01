// The console's long Singapore date, as the 4a.1 date line prints it ("Wednesday 16 September
// 2026 · all times SGT"). Every admin time is a Singapore time, whatever the admin's device zone.
// en-US parts, assembled by hand: en-SG/en-GB print "Wednesday, 16 September 2026" with a comma.
const longDay = new Intl.DateTimeFormat("en-US", {
  timeZone: "Asia/Singapore",
  weekday: "long",
  day: "numeric",
  month: "long",
  year: "numeric",
});

export function formatSgtDate(ms: number): string {
  const parts = longDay.formatToParts(new Date(ms));
  const part = (type: Intl.DateTimeFormatPartTypes) =>
    parts.find((p) => p.type === type)?.value ?? "";
  return `${part("weekday")} ${part("day")} ${part("month")} ${part("year")}`;
}
