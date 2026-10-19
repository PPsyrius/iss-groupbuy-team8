import { render, screen } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { BrandIsologo, BrandLogo } from "./BrandMark";

describe("BrandMark", () => {
  it("names the logo BlocBuy by default", () => {
    render(<BrandLogo />);
    expect(screen.getByRole("img", { name: "BlocBuy" })).toHaveAttribute(
      "src",
      expect.stringContaining("blocbuy-logo"),
    );
  });

  it("swaps in the dark isologo for inverse surfaces", () => {
    // Vitest inlines the small isologos as data URIs, so compare the two sources.
    render(
      <>
        <BrandIsologo alt="light" />
        <BrandIsologo alt="dark" tone="dark" />
      </>,
    );
    const light = screen.getByRole("img", { name: "light" }).getAttribute("src");
    expect(screen.getByRole("img", { name: "dark" }).getAttribute("src")).not.toBe(light);
  });

  it("drops out of the accessibility tree when decorative", () => {
    render(<BrandIsologo alt="" />);
    expect(screen.queryByRole("img")).toBeNull();
  });
});
