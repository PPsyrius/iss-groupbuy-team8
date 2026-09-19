import { HttpResponse } from "msw";
import { setupWorker } from "msw/browser";

import { OK_CODE } from "@/api/envelope";
import { createMswHandlers } from "@/api/generated/msw.gen";
import type { ProductDetailResponse, ProductSummaryResponse } from "@/api/generated/types.gen";

// Offline UI work: `?msw=1` on the URL makes main.tsx start this worker before rendering, so the
// catalog and the listing detail render the artboards' sample content (docs/design-screens.md
// §4.1, §4.2) without a backend. Unmatched requests pass through to the Vite proxy. Never
// imported outside that gate. Deal deadlines are relative to page load so the countdowns tick
// the way the artboard's do (2h 14m, 47:25 urgent, 19h 05m).

const id = (n: number) => `5b1b9d5c-1a11-4e1e-9a10-${String(n).padStart(12, "0")}`;
const loadedAt = Date.now();
const inSeconds = (s: number) => new Date(loadedAt + s * 1000).toISOString();

interface Seed {
  n: number;
  title: string;
  subtitle: string;
  category: string;
  individualPrice: number;
  groupPrice?: number;
  description: string;
  stock: number;
  soldCount: number;
  deal?: { joined: number; target: number; endsIn: number };
}

const rice =
  "Fragrant long-grain rice from the 2026 harvest, milled in Bangkok and vacuum packed in 10 kg sacks. Keeps six months unopened. Sold by the sack only, one sack per seat.";

const seeds: Seed[] = [
  {
    n: 1,
    title: "Thai jasmine rice 10 kg",
    subtitle: "New crop, vacuum packed",
    category: "groceries",
    individualPrice: 89,
    groupPrice: 71,
    description: rice,
    stock: 38,
    soldCount: 142,
    deal: { joined: 3, target: 5, endsIn: 2 * 3600 + 14 * 60 },
  },
  {
    n: 2,
    title: "D24 durian, per kg",
    subtitle: "Pahang, delivered chilled",
    category: "groceries",
    individualPrice: 38,
    groupPrice: 32,
    description:
      "Pahang D24, harvested this week and delivered chilled the same day. Sold per kilogram; one seat is one kilogram.",
    stock: 24,
    soldCount: 61,
    deal: { joined: 4, target: 5, endsIn: 47 * 60 + 25 },
  },
  {
    n: 3,
    title: "Laundry detergent 5 L twin pack",
    subtitle: "Fragrance-free",
    category: "home",
    individualPrice: 42.9,
    groupPrice: 34.9,
    description:
      "Two 5 L bottles of fragrance-free liquid detergent, suitable for front and top loaders.",
    stock: 80,
    soldCount: 210,
    deal: { joined: 2, target: 6, endsIn: 19 * 3600 + 5 * 60 },
  },
  {
    n: 4,
    title: "Replacement HEPA filters for the Coway Airmega 150, twin pack",
    subtitle: "Fits AP-1512 and AP-1519",
    category: "electronics",
    individualPrice: 118,
    description:
      "Genuine Coway replacement set: one pre-filter and one HEPA cartridge. Recommended every twelve months.",
    stock: 12,
    soldCount: 9,
  },
  {
    n: 5,
    title: "Kopi powder 500 g",
    subtitle: "Traditional roast",
    category: "groceries",
    individualPrice: 12.8,
    groupPrice: 9.9,
    description:
      "Robusta beans roasted with margarine and sugar the traditional way, ground for the sock filter.",
    stock: 150,
    soldCount: 388,
    deal: { joined: 5, target: 8, endsIn: 5 * 3600 },
  },
  {
    n: 6,
    title: "Fold-out drying rack",
    subtitle: "Stainless, 3 tiers",
    category: "home",
    individualPrice: 56,
    description: "Three-tier stainless steel rack, folds flat to 6 cm.",
    stock: 30,
    soldCount: 17,
  },
  {
    n: 7,
    title: "Kids’ stationery bundle",
    subtitle: "Primary 1 to 3",
    category: "kids",
    individualPrice: 24,
    groupPrice: 19.5,
    description: "Pencils, erasers, rulers and exercise books for the first three primary years.",
    stock: 60,
    soldCount: 44,
    deal: { joined: 1, target: 4, endsIn: 30 * 3600 },
  },
  {
    n: 8,
    title: "Hokkaido milk, 12 cartons",
    subtitle: "3.6%, UHT",
    category: "groceries",
    individualPrice: 46.8,
    groupPrice: 39.6,
    description: "Twelve 1 L cartons of full-cream UHT milk from Hokkaido.",
    stock: 45,
    soldCount: 96,
    deal: { joined: 6, target: 10, endsIn: 8 * 3600 + 30 * 60 },
  },
  {
    n: 9,
    title: "Bak kwa, 500 g",
    subtitle: "Sliced, vacuum packed",
    category: "groceries",
    individualPrice: 32,
    groupPrice: 28,
    description: "Sliced pork bak kwa, vacuum packed the day it is grilled.",
    stock: 20,
    soldCount: 73,
    deal: { joined: 7, target: 8, endsIn: 3 * 3600 },
  },
  {
    n: 10,
    title: "Dish soap refill 3 L",
    subtitle: "Lemon",
    category: "home",
    individualPrice: 9.9,
    description: "Lemon dish soap refill pouch, 3 L.",
    stock: 200,
    soldCount: 51,
  },
];

const details: ProductDetailResponse[] = seeds.map((s) => ({
  id: id(s.n),
  title: s.title,
  subtitle: s.subtitle,
  category: s.category,
  individualPrice: s.individualPrice,
  ...(s.groupPrice === undefined ? {} : { groupPrice: s.groupPrice }),
  currency: "sgd",
  description: s.description,
  images: [],
  stock: s.stock,
  soldCount: s.soldCount,
  ...(s.deal === undefined
    ? {}
    : {
        deal: {
          dealId: `deal-${s.n}`,
          currentSize: s.deal.joined,
          targetSize: s.deal.target,
          expireTime: inSeconds(s.deal.endsIn),
        },
      }),
}));

const summaries: ProductSummaryResponse[] = seeds.map((s) => ({
  id: id(s.n),
  title: s.title,
  subtitle: s.subtitle,
  category: s.category,
  individualPrice: s.individualPrice,
  ...(s.groupPrice === undefined ? {} : { groupPrice: s.groupPrice }),
  currency: "sgd",
}));

export async function startBrowserMsw() {
  const worker = setupWorker(
    ...createMswHandlers().all({
      pick: {
        pageListings: ({ request }) => {
          const url = new URL(request.url);
          const page = Math.max(1, Number(url.searchParams.get("page") ?? 1));
          const size = Math.max(1, Number(url.searchParams.get("size") ?? 20));
          const category = url.searchParams.get("category");
          // Contract: whitespace-separated terms all match across title, subtitle or description;
          // the summary has no description, so the seed matches title and subtitle.
          const terms = (url.searchParams.get("keyword") ?? "")
            .toLowerCase()
            .split(/\s+/)
            .filter(Boolean);
          const filtered = summaries.filter(
            (p) =>
              (!category || p.category === category) &&
              terms.every((t) => `${p.title} ${p.subtitle ?? ""}`.toLowerCase().includes(t)),
          );
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
        getListing: ({ params }) => {
          const found = details.find((p) => p.id === params.id);
          if (!found) {
            return HttpResponse.json(
              { code: "PRODUCT_404_001", message: "Listing not found" },
              { status: 404 },
            );
          }
          return HttpResponse.json({ code: OK_CODE, message: "ok", data: found });
        },
      },
    }),
  );
  await worker.start({ onUnhandledRequest: "bypass" });
}
