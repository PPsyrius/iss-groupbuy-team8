import { ZonedDateTime } from "@internationalized/date";
import * as v from "valibot";

import { formatSgd, toCents } from "./money";
import { sentEnd } from "./policy-form";

// The revision form's rules (admin plan A2-01 step 4), mirroring the backend's (`PolicyRequest`
// and `TeamPricePolicy`) so a rule is refused here before the backend would refuse it: a decimal
// amount above zero and strictly below the solo price, both ends, an end after the start and in
// the future. Valid until is the last valid minute, so both end checks compare the instant sent,
// a minute later (§6 Q13). `nowMs` is the console's minute clock; the backend checks the end
// against its own.
const AMOUNT = /^\d{1,16}(\.\d{1,2})?$/;

const isZoned = (value: unknown): value is ZonedDateTime => value instanceof ZonedDateTime;

export function policyFormSchema({
  originalPrice,
  nowMs,
}: {
  originalPrice: string;
  nowMs: number;
}) {
  const solo = toCents(originalPrice);
  // Only a well-formed amount reaches the two checks after the regex, which reports the rest.
  const cents = (amount: string) => (AMOUNT.test(amount) ? toCents(amount) : null);
  return v.pipe(
    v.object({
      amount: v.pipe(
        v.string(),
        v.trim(),
        v.regex(AMOUNT, "Enter the bloc price in dollars and cents, like 71.00."),
        v.check((amount) => (cents(amount) ?? 1n) > 0n, "Enter a price above S$0.00"),
        v.check(
          (amount) => solo === null || (cents(amount) ?? 0n) < solo,
          `Must be strictly below the solo price of ${formatSgd(originalPrice)}.`,
        ),
      ),
      validFrom: v.custom<ZonedDateTime | null>(isZoned, "Choose when the rule starts."),
      validUntil: v.pipe(
        v.custom<ZonedDateTime | null>(isZoned, "Choose when the rule ends."),
        v.check(
          (until) => !until || sentEnd(until).toDate().getTime() > nowMs,
          "Must be in the future.",
        ),
      ),
    }),
    v.forward(
      v.partialCheck(
        [["validFrom"], ["validUntil"]],
        ({ validFrom, validUntil }) =>
          !validFrom || !validUntil || sentEnd(validUntil).compare(validFrom) > 0,
        "Must be after the start of the rule.",
      ),
      ["validUntil"],
    ),
  );
}
