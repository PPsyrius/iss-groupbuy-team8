import { HttpResponse } from "msw";
import { setupWorker } from "msw/browser";

import { OK_CODE } from "@/api/envelope";
import { createMswHandlers } from "@/api/generated/msw.gen";
import type { ProductSummaryResponse } from "@/api/generated/types.gen";

// Offline UI work: `?msw=1` on the URL makes main.tsx start this worker before rendering, so the
// catalog renders the Catalog artboard's sample content (docs/design-screens.md §4.1) without a
// backend. Unmatched requests pass through to the Vite proxy. Never imported outside that gate.

const id = (n: number) => `5b1b9d5c-1a11-4e1e-9a10-${String(n).padStart(12, "0")}`;
const listing = (
  n: number,
  title: string,
  subtitle: string,
  category: string,
  individualPrice: number,
  groupPrice?: number,
): ProductSummaryResponse => ({
  id: id(n),
  title,
  subtitle,
  category,
  individualPrice,
  ...(groupPrice === undefined ? {} : { groupPrice }),
  currency: "sgd",
});

const seed: ProductSummaryResponse[] = [
  listing(1, "Thai jasmine rice 10 kg", "New crop, fragrant", "groceries", 89, 71),
  listing(2, "D24 durian, per kg", "Pahang, delivered chilled", "groceries", 38, 32),
  listing(3, "Laundry detergent 5 L twin pack", "Fragrance-free", "household", 42.9, 34.9),
  listing(
    4,
    "Replacement HEPA filters for the Coway Airmega 150, twin pack",
    "H13, fits 2019 onwards",
    "electronics",
    118,
  ),
  listing(5, "Kopi powder 500 g", "Traditional roast", "groceries", 12.8, 9.9),
  listing(6, "Fold-out drying rack", "Stainless, 3 tiers", "household", 56),
  listing(7, "Kids’ stationery bundle", "Primary 1 to 3", "kids", 24, 19.5),
  listing(8, "Hokkaido milk, 12 cartons", "3.6%, UHT", "groceries", 46.8, 39.6),
  listing(9, "Bak kwa, 500 g", "Sliced, vacuum packed", "groceries", 32, 28),
  listing(10, "Dish soap refill 3 L", "Lemon", "household", 9.9),
];

export async function startBrowserMsw() {
  const worker = setupWorker(
    ...createMswHandlers().all({
      pick: {
        pageListings: ({ request }) => {
          const url = new URL(request.url);
          const page = Math.max(1, Number(url.searchParams.get("page") ?? 1));
          const size = Math.max(1, Number(url.searchParams.get("size") ?? 20));
          const category = url.searchParams.get("category");
          const filtered = category ? seed.filter((p) => p.category === category) : seed;
          return HttpResponse.json({
            code: OK_CODE,
            message: "ok",
            data: {
              items: filtered.slice((page - 1) * size, page * size),
              total: filtered.length,
              page,
              size,
            },
          });
        },
      },
    }),
  );
  await worker.start({ onUnhandledRequest: "bypass" });
}
