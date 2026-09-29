// Money stays a decimal string end to end (admin plan §12 note 8): `originalPrice` and a policy
// `amount` are sent back exactly as read, so the backend's "must remain unchanged" check never
// trips on formatting. Comparisons go through integer cents, never a float.
const DECIMAL = /^(\d+)(?:\.(\d{1,2}))?$/;

// `null` for anything the backend would not accept as a positive-or-zero SGD amount with at most
// two decimals (a sign, an exponent, whitespace, a third decimal).
export function toCents(value: string): bigint | null {
  const match = DECIMAL.exec(value);
  if (!match) return null;
  const [, whole = "0", fraction = ""] = match;
  return BigInt(whole) * 100n + BigInt(fraction.padEnd(2, "0"));
}

const grouping = new Intl.NumberFormat("en-SG");

// `S$46.80` as the storefront prints it (see `formatPrice` in catalog/Price.tsx), but built from
// cents so no amount goes through a float. An unreadable amount is shown as sent.
export function formatSgd(value: string): string {
  const cents = toCents(value);
  if (cents === null) return value;
  const fraction = (cents % 100n).toString().padStart(2, "0");
  return `S$${grouping.format(cents / 100n)}.${fraction}`;
}
