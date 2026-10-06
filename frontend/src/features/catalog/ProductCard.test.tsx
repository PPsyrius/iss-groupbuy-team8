import { screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { renderWithRouter } from "@/test/render";

import { ProductCard } from "./ProductCard";

const product = {
  id: "0b6f2c1e-6b1a-4f0e-9d1c-2a9c3e5f7a11",
  title: "Thai jasmine rice 10 kg",
  subtitle: "Fragrant, new crop",
  coverImage: "https://example.test/rice.jpg",
  category: "groceries",
  individualPrice: 89,
  groupPrice: 71,
  currency: "sgd",
};

const invalidDeals = [
  undefined,
  null,
  {},
  { currentSize: 3 },
  { targetSize: 5 },
  { currentSize: 3, targetSize: 0 },
  { currentSize: -1, targetSize: 5 },
  { currentSize: 3, targetSize: -5 },
  { currentSize: 1.5, targetSize: 5 },
  { currentSize: 3, targetSize: 5.5 },
  { currentSize: Number.NaN, targetSize: 5 },
  { currentSize: 3, targetSize: Number.POSITIVE_INFINITY },
];

describe("ProductCard", () => {
  it("shows the title and links to the listing detail", async () => {
    await renderWithRouter(<ProductCard product={product} />);
    expect(screen.getByRole("heading", { name: "Thai jasmine rice 10 kg" })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Thai jasmine rice 10 kg" })).toHaveAttribute(
      "href",
      `/products/${product.id}`,
    );
  });

  it("shows the bloc price with the solo price struck through", async () => {
    await renderWithRouter(<ProductCard product={product} />);
    expect(screen.getByText("S$71.00")).toBeInTheDocument();
    expect(screen.getByText("S$89.00")).toHaveClass("line-through");
    expect(screen.queryByText(/buying alone/i)).not.toBeInTheDocument();
  });

  it("shows the solo price, 'Buying alone' and a start link when no bloc is open", async () => {
    const { groupPrice: _omitted, ...solo } = product;
    await renderWithRouter(<ProductCard product={solo} />);
    expect(screen.getByText("S$89.00")).not.toHaveClass("line-through");
    expect(screen.getByText("Buying alone")).toBeInTheDocument();
    expect(screen.getByText("No open bloc yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /start one/i })).toHaveAttribute(
      "href",
      `/products/${product.id}`,
    );
    expect(screen.queryByText(/S\$0/)).not.toBeInTheDocument();
  });

  // §4.1 "Card anatomy": the open bloc's progress row, from the summary's `deal` (#375, FB-16).
  it("shows the open bloc's progress, seats left and a bar", async () => {
    const deal = {
      dealId: "d1",
      currentSize: 3,
      targetSize: 5,
      expireTime: "2099-01-01T00:00:00Z",
    };
    await renderWithRouter(<ProductCard product={{ ...product, deal }} />);
    expect(screen.getByText("3 of 5 joined")).toBeInTheDocument();
    expect(screen.getByText("2 seats left")).toBeInTheDocument();
    const bar = screen.getByRole("progressbar", { name: "3 of 5 joined" });
    expect(bar).toHaveAttribute("aria-valuenow", "3");
    expect(bar).toHaveAttribute("aria-valuemax", "5");
    expect(screen.queryByText("No open bloc yet")).not.toBeInTheDocument();
    // No countdown on the grid (§4.1).
    expect(screen.queryByText(/\d+h \d+m left|\d\d:\d\d left/u)).not.toBeInTheDocument();
    expect(screen.queryByText(/^Ends /u)).not.toBeInTheDocument();
  });

  it("emphasises the last seat and never counts below zero", async () => {
    const last = { dealId: "d1", currentSize: 4, targetSize: 5 };
    const { unmount } = await renderWithRouter(
      <ProductCard product={{ ...product, deal: last }} />,
    );
    expect(screen.getByText("1 seat left")).toHaveClass("font-semibold");
    unmount();
    const over = { dealId: "d1", currentSize: 6, targetSize: 5 };
    await renderWithRouter(<ProductCard product={{ ...product, deal: over }} />);
    expect(screen.getByText("0 seats left")).toBeInTheDocument();
  });

  it("shows no progress row when the summary has no open bloc", async () => {
    await renderWithRouter(<ProductCard product={{ ...product, deal: null }} />);
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.queryByText(/joined$/u)).not.toBeInTheDocument();
  });

  it.each([
    [0, 5],
    [3, 5],
    [2, 8],
    [5, 5],
    [6, 5],
  ])("shows the API's %i of %i joined with a bounded accessible bar", async (joined, target) => {
    await renderWithRouter(
      <ProductCard product={{ ...product, deal: { currentSize: joined, targetSize: target } }} />,
    );
    expect(screen.getByText(`${joined} of ${target} joined`)).toBeInTheDocument();
    const bar = screen.getByRole("progressbar", { name: `${joined} of ${target} joined` });
    expect(bar).toHaveAttribute("aria-valuenow", String(Math.min(joined, target)));
    expect(bar).toHaveAttribute("aria-valuemax", String(target));
    expect(screen.queryByText("No open bloc yet")).not.toBeInTheDocument();
  });

  it("does not call an API-provided bloc absent when its price is missing", async () => {
    const { groupPrice: _omitted, ...solo } = product;
    await renderWithRouter(
      <ProductCard product={{ ...solo, deal: { currentSize: 2, targetSize: 8 } }} />,
    );
    expect(screen.getByText("S$89.00")).not.toHaveClass("line-through");
    expect(screen.getByRole("progressbar", { name: "2 of 8 joined" })).toBeInTheDocument();
    expect(screen.queryByText("Buying alone")).not.toBeInTheDocument();
    expect(screen.queryByText("No open bloc yet")).not.toBeInTheDocument();
    expect(screen.queryByRole("link", { name: /start one/i })).not.toBeInTheDocument();
  });
  it.each(invalidDeals)("omits progress when sizes are missing or invalid: %j", async (deal) => {
    await renderWithRouter(
      <ProductCard product={{ ...product, ...(deal === undefined ? {} : { deal }) }} />,
    );
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.queryByText(/of .* joined/)).not.toBeInTheDocument();
    expect(screen.getByRole("link", { name: product.title })).toHaveAttribute(
      "href",
      `/products/${product.id}`,
    );
  });

  it.each(invalidDeals)("keeps the solo fallback when progress is invalid: %j", async (deal) => {
    const { groupPrice: _omitted, ...solo } = product;
    await renderWithRouter(
      <ProductCard product={{ ...solo, ...(deal === undefined ? {} : { deal }) }} />,
    );
    expect(screen.queryByRole("progressbar")).not.toBeInTheDocument();
    expect(screen.getByText("S$89.00")).not.toHaveClass("line-through");
    expect(screen.getByText("Buying alone")).toBeInTheDocument();
    expect(screen.getByText("No open bloc yet")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: /start one/i })).toHaveAttribute(
      "href",
      `/products/${product.id}`,
    );
  });

  // The title link stretches over the tile with an absolutely positioned pseudo-element; the
  // tile must be the containing block, or the overlay covers the whole page (CodeRabbit
  // r4043389594). The start link is positioned too so it paints above that overlay.
  it("confines the stretched title link to the tile and keeps the start link clickable", async () => {
    const { groupPrice: _omitted, ...solo } = product;
    await renderWithRouter(<ProductCard product={solo} />);
    expect(screen.getByRole("article")).toHaveClass("relative");
    expect(screen.getByRole("link", { name: "Thai jasmine rice 10 kg" })).toHaveClass(
      "after:absolute",
    );
    expect(screen.getByRole("link", { name: /start one/i })).toHaveClass("relative");
  });
});
