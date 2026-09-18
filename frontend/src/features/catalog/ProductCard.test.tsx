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
