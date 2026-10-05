import * as v from "valibot";

import { lifetimeSecondsOf } from "./team-terms";

// The group terms form's rules (admin plan A3-04), mirroring the backend's `TeamTermsRequest` and
// `ProductTeamTerms` so a refusal is caught here first: at least 2 seats with no upper bound, and
// a lifetime from 2400 seconds (the purchase window) to one day. A cleared NumberField reads NaN,
// which the integer checks refuse with the field's own message.
const MIN_SEATS = 2;
const MIN_LIFETIME = 2400;
const MAX_LIFETIME = 86_400;

const isWhole = (n: number) => Number.isInteger(n);

export const teamTermsSchema = v.pipe(
  v.object({
    seats: v.pipe(
      v.number("Enter at least 2 seats."),
      v.check((n) => isWhole(n) && n >= MIN_SEATS, "Enter at least 2 seats."),
    ),
    hours: v.pipe(
      v.number("Enter the hours."),
      v.check((n) => isWhole(n) && n >= 0, "Enter the hours."),
    ),
    minutes: v.pipe(
      v.number("Enter 0 to 59."),
      v.check((n) => isWhole(n) && n >= 0 && n <= 59, "Enter 0 to 59."),
    ),
  }),
  v.forward(
    v.partialCheck(
      [["hours"], ["minutes"]],
      // Only a well-formed lifetime is bounded; a bad hours or minutes reports itself above.
      (values) => {
        const { hours, minutes } = values;
        if (!isWhole(hours) || hours < 0 || !isWhole(minutes) || minutes < 0 || minutes > 59)
          return true;
        const seconds = lifetimeSecondsOf(values);
        return seconds >= MIN_LIFETIME && seconds <= MAX_LIFETIME;
      },
      "A bloc stays open from 40 minutes to 24 hours.",
    ),
    ["hours"],
  ),
);
