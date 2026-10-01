// Money is shown as `S$89.00` (design brief §9): the `S$` prefix, two decimals, never rounded
// beyond the cents the API sent. Intl has no locale that prints `S$` for SGD (en-SG gives `$`,
// others `SGD`), so the prefix is ours and Intl only supplies the grouping and decimals. Any other
// currency falls back to Intl's currency style with the ISO code, which is never expected today
// (the contract pins `currency` to `sgd`).
const sgdDigits = new Intl.NumberFormat("en-SG", {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

export function formatPrice(amount: number, currency: string): string {
  const code = currency.toUpperCase();
  if (code === "SGD") return `S$${sgdDigits.format(amount)}`;
  return new Intl.NumberFormat("en-SG", { style: "currency", currency: code }).format(amount);
}

export function Price({
  amount,
  currency,
  className,
}: Readonly<{
  amount: number;
  currency: string;
  className?: string;
}>) {
  return <span className={className}>{formatPrice(amount, currency)}</span>;
}
