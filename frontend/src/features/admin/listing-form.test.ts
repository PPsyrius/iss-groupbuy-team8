import { describe, expect, it } from "vitest";

import type { AdminListingResponse } from "@/api/generated/types.gen";

import { emptyListingForm, fromListing, isHttpsUrl, toListingContent } from "./listing-form";

describe("toListingContent", () => {
  it("omits empty optionals, because a revise replaces the whole listing", () => {
    expect(
      toListingContent({
        ...emptyListingForm,
        title: "  Hokkaido milk, 12 cartons ",
        subtitle: " ",
        originalPrice: "46.80",
        stock: 0,
        images: [""],
      }),
    ).toEqual({
      title: "Hokkaido milk, 12 cartons",
      originalPrice: "46.80",
      currency: "sgd",
      stock: 0,
    });
  });

  it("keeps the filled fields and drops blank image rows", () => {
    expect(
      toListingContent({
        title: "Rice",
        subtitle: "New crop",
        description: "Vacuum packed.",
        category: "groceries",
        originalPrice: "89",
        stock: 38,
        coverImage: "https://cdn.example.com/cover.jpg",
        images: ["https://cdn.example.com/1.jpg", "  ", "https://cdn.example.com/2.jpg "],
      }),
    ).toEqual({
      title: "Rice",
      subtitle: "New crop",
      description: "Vacuum packed.",
      category: "groceries",
      originalPrice: "89",
      currency: "sgd",
      stock: 38,
      coverImage: "https://cdn.example.com/cover.jpg",
      images: ["https://cdn.example.com/1.jpg", "https://cdn.example.com/2.jpg"],
    });
  });
});

describe("fromListing", () => {
  it("uses empty form values when listing content is missing", () => {
    expect(fromListing({})).toEqual({ ...emptyListingForm, images: [] });
  });
  const dto: AdminListingResponse = {
    id: "p1",
    productNo: "BB-100481",
    listing: {
      title: "Thai jasmine rice 10 kg",
      category: "groceries",
      originalPrice: "89",
      currency: "sgd",
      stock: 38,
      images: ["https://cdn.example.com/1.jpg"],
    },
    status: "on_sale",
    version: 4,
  };

  it("keeps the price exactly as the server sent it", () => {
    expect(fromListing(dto)).toEqual({
      title: "Thai jasmine rice 10 kg",
      subtitle: "",
      description: "",
      category: "groceries",
      originalPrice: "89",
      stock: 38,
      coverImage: "",
      images: ["https://cdn.example.com/1.jpg"],
    });
  });

  it("round-trips through toListingContent", () => {
    expect(toListingContent(fromListing(dto))).toEqual(dto.listing);
  });
});

describe("isHttpsUrl", () => {
  it("accepts https URLs only", () => {
    expect(isHttpsUrl("https://cdn.example.com/a.jpg")).toBe(true);
    expect(isHttpsUrl("http://cdn.example.com/a.jpg")).toBe(false);
    expect(isHttpsUrl("https://")).toBe(false);
    expect(isHttpsUrl("cdn.example.com/a.jpg")).toBe(false);
  });
});
