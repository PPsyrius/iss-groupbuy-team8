import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { formatPrice, Price } from "./Price";

describe("formatPrice", () => {
  it("prints SGD as S$ with two decimals and grouping", () => {
    expect(formatPrice(89, "sgd")).toBe("S$89.00");
    expect(formatPrice(1234.5, "SGD")).toBe("S$1,234.50");
  });

  it("falls back to the ISO code for other currencies", () => {
    expect(formatPrice(12, "usd")).toMatch(/12\.00/);
    expect(formatPrice(12, "usd")).not.toMatch(/^S\$/);
  });
});

describe("Price", () => {
  it("renders the formatted amount with the given class", () => {
    render(<Price amount={5.5} currency="sgd" className="tabular-nums" />);
    expect(screen.getByText("S$5.50")).toHaveClass("tabular-nums");
  });
});
