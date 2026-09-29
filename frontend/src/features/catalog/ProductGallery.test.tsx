import { fireEvent, render, screen, within } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";

import { ProductGallery } from "./ProductGallery";

const images = [
  "https://example.test/shot-1.jpg",
  "https://example.test/shot-2.jpg",
  "https://example.test/shot-3.jpg",
];
const reorderedImages = [images[2]!, images[0]!];
const initialReorder = [images[1]!, images[0]!, images[2]!];
const withoutLast = [images[0]!, images[1]!];
const reorderedFallback = [images[1]!, images[0]!];
const restoredLast = [images[2]!, images[1]!, images[0]!];
const repeatedImages = [images[0]!, images[0]!];
const noImages: string[] = [];

describe("ProductGallery", () => {
  it("retains the initially displayed source when images reorder before a click", () => {
    const { rerender } = render(<ProductGallery images={images} />);

    rerender(<ProductGallery images={initialReorder} />);

    expect(screen.getByRole("img", { name: "Product shot 2" })).toHaveAttribute("src", images[0]);
    expect(screen.getByText("product shot 2 of 3")).toBeInTheDocument();
  });

  it("retains the fallback source through later reorders and restored images", async () => {
    const { rerender } = render(<ProductGallery images={images} />);
    await userEvent.click(screen.getAllByRole("button", { name: "Show product shot 3" })[0]!);

    rerender(<ProductGallery images={withoutLast} />);
    expect(screen.getByRole("img", { name: "Product shot 1" })).toHaveAttribute("src", images[0]);

    rerender(<ProductGallery images={reorderedFallback} />);
    expect(screen.getByRole("img", { name: "Product shot 2" })).toHaveAttribute("src", images[0]);
    expect(screen.getByText("product shot 2 of 2")).toBeInTheDocument();

    rerender(<ProductGallery images={restoredLast} />);
    expect(screen.getByRole("img", { name: "Product shot 3" })).toHaveAttribute("src", images[0]);
    expect(screen.getByText("product shot 3 of 3")).toBeInTheDocument();
  });

  it("retains the selected source and updates the caption when images reorder", async () => {
    const { rerender } = render(<ProductGallery images={images} />);
    await userEvent.click(screen.getAllByRole("button", { name: "Show product shot 3" })[0]!);

    rerender(<ProductGallery images={reorderedImages} />);

    expect(screen.getByRole("img", { name: "Product shot 1" })).toHaveAttribute("src", images[2]);
    expect(screen.getByText("product shot 1 of 2")).toBeInTheDocument();
    for (const control of screen.getAllByRole("button", { name: "Show product shot 1" })) {
      expect(control).toHaveAttribute("aria-pressed", "true");
    }
  });

  it("keeps repeated image URLs independently selectable", async () => {
    render(<ProductGallery images={repeatedImages} />);
    await userEvent.click(screen.getAllByRole("button", { name: "Show product shot 2" })[0]!);

    expect(screen.getByRole("img", { name: "Product shot 2" })).toHaveAttribute("src", images[0]);
    expect(screen.getByText("product shot 2 of 2")).toBeInTheDocument();
    for (const control of screen.getAllByRole("button", { name: "Show product shot 2" })) {
      expect(control).toHaveAttribute("aria-pressed", "true");
    }
  });

  it("loads the main image eagerly and requests lazy loading for all 20 thumbnails", () => {
    const manyImages = Array.from({ length: 20 }, (_, i) => `https://example.test/shot-${i}.jpg`);
    render(<ProductGallery images={manyImages} />);

    expect(screen.getByRole("img", { name: "Product shot 1" })).toHaveAttribute("loading", "eager");
    const thumbnails = within(screen.getByRole("list", { name: "Product thumbnails" }));
    const thumbnailImages = thumbnails.getAllByRole("presentation");
    expect(thumbnailImages).toHaveLength(20);
    for (const img of thumbnailImages) expect(img).toHaveAttribute("loading", "lazy");
  });

  it("keeps the fallback without a caption or controls when all images are removed", () => {
    const { rerender } = render(<ProductGallery images={images} />);
    rerender(<ProductGallery images={noImages} />);

    expect(screen.getByRole("region", { name: "Product images" })).toBeInTheDocument();
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.queryByText(/product shot/)).not.toBeInTheDocument();
    expect(screen.queryByRole("button")).not.toBeInTheDocument();
  });

  it("reveals the fallback after an image error and can display the next shot", async () => {
    render(<ProductGallery images={images} />);
    fireEvent.error(screen.getByRole("img", { name: "Product shot 1" }));
    expect(screen.queryByRole("img")).not.toBeInTheDocument();
    expect(screen.getByText("product shot 1 of 3")).toBeInTheDocument();

    await userEvent.click(screen.getAllByRole("button", { name: "Show product shot 2" })[0]!);
    expect(screen.getByRole("img", { name: "Product shot 2" })).toHaveAttribute("src", images[1]);
    expect(screen.getByText("product shot 2 of 3")).toBeInTheDocument();
  });
});
