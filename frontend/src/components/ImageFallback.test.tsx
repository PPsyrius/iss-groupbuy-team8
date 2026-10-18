import { fireEvent, render } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { ImageFallback } from "./ImageFallback";

const cover = "https://example.test/cover.jpg";
const other = "https://example.test/other.jpg";

describe("ImageFallback", () => {
  it("draws the image over the striped tile", () => {
    const { container } = render(<ImageFallback src={cover} className="size-14" />);
    const img = container.querySelector("img");
    expect(img).toHaveAttribute("src", cover);
    expect(img).toHaveAttribute("alt", "");
    expect(img).toHaveAttribute("loading", "lazy");
    expect(container.querySelector("[data-image-fallback]")).toHaveAttribute("aria-hidden", "true");
  });

  it("shows only the tile when there is no src", () => {
    const { container } = render(<ImageFallback src={null} />);
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelectorAll("[data-image-fallback]")).toHaveLength(1);
  });

  it("removes the image after it fails to load and keeps the tile", () => {
    const { container } = render(<ImageFallback src={cover} />);
    fireEvent.error(container.querySelector("img")!);
    expect(container.querySelector("img")).toBeNull();
    expect(container.querySelectorAll("[data-image-fallback]")).toHaveLength(1);
  });

  it("tries again when the src changes after a failure", () => {
    const { container, rerender } = render(<ImageFallback src={cover} />);
    fireEvent.error(container.querySelector("img")!);
    expect(container.querySelector("img")).toBeNull();
    rerender(<ImageFallback src={other} />);
    expect(container.querySelector("img")).toHaveAttribute("src", other);
  });
});
