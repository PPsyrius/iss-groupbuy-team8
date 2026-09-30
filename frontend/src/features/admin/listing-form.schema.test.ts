import * as v from "valibot";
import { describe, expect, it } from "vitest";

import { emptyListingForm, type ListingFormValues } from "./listing-form";
import { listingFormSchema } from "./listing-form.schema";

const valid: ListingFormValues = {
  ...emptyListingForm,
  title: "Hokkaido milk, 12 cartons",
  category: "groceries",
  originalPrice: "46.80",
  stock: 0,
};

const create = listingFormSchema({});

// The issue messages keyed by field path ("images.1" for the second image row). TanStack Form
// shows every issue of a field, so two rules firing on one value would show both: joined here, a
// field must end up with exactly one message.
function problems(schema: typeof create, values: ListingFormValues) {
  const result = v.safeParse(schema, values);
  const out: Record<string, string> = {};
  for (const issue of result.issues ?? []) {
    const path = issue.path?.map((p) => String(p.key)).join(".") ?? "";
    out[path] = path in out ? `${out[path]} | ${issue.message}` : issue.message;
  }
  return out;
}

describe("listingFormSchema", () => {
  it("accepts a new draft with only the required fields", () => {
    expect(problems(create, valid)).toEqual({});
  });

  it("needs a title of at most 256 characters, ignoring surrounding spaces", () => {
    expect(problems(create, { ...valid, title: "   " })).toEqual({ title: "Enter a title." });
    expect(problems(create, { ...valid, title: "x".repeat(257) })).toEqual({
      title: "Keep the title under 256 characters.",
    });
  });

  it("takes the solo price as a positive decimal string with at most two decimals", () => {
    expect(problems(create, { ...valid, originalPrice: "12.8" })).toEqual({});
    expect(problems(create, { ...valid, originalPrice: "89" })).toEqual({});
    expect(problems(create, { ...valid, originalPrice: "0.00" })).toEqual({
      originalPrice: "Enter a price above S$0.00",
    });
    expect(problems(create, { ...valid, originalPrice: "1.234" })).toEqual({
      originalPrice: "Enter the price in dollars and cents, like 46.80.",
    });
    expect(problems(create, { ...valid, originalPrice: "" })).toEqual({
      originalPrice: "Enter the price in dollars and cents, like 46.80.",
    });
  });

  it("takes stock as a whole number from zero", () => {
    expect(problems(create, { ...valid, stock: -1 })).toEqual({
      stock: "Enter a whole number from 0.",
    });
    expect(problems(create, { ...valid, stock: 1.5 })).toEqual({
      stock: "Enter a whole number from 0.",
    });
    expect(problems(create, { ...valid, stock: Number.NaN })).toEqual({
      stock: "Enter a whole number from 0.",
    });
  });

  it("accepts only https image URLs, skipping blank rows", () => {
    expect(problems(create, { ...valid, coverImage: "http://cdn.example.com/a.jpg" })).toEqual({
      coverImage: "Use an https:// URL.",
    });
    expect(
      problems(create, { ...valid, images: ["https://cdn.example.com/a.jpg", "", "ftp://x"] }),
    ).toEqual({ "images.2": "Use an https:// URL." });
    expect(
      problems(create, { ...valid, images: ["https://cdn.example.com/a.jpg", " ", ""] }),
    ).toEqual({});
  });

  it("caps the gallery at 20 URLs", () => {
    const url = "https://cdn.example.com/a.jpg";
    expect(problems(create, { ...valid, images: [...Array(20).fill(url), ""] })).toEqual({});
    expect(problems(create, { ...valid, images: Array(21).fill(url) })).toEqual({
      images: "Add at most 20 image URLs.",
    });
  });

  it("has no product number: the server assigns it on save (#288)", () => {
    expect(Object.keys(create.entries)).not.toContain("productNo");
  });

  it("takes a fixed category, or the one the listing already has", () => {
    expect(problems(create, { ...valid, category: "" })).toEqual({});
    expect(problems(create, { ...valid, category: "snacks" })).toEqual({
      category: "Choose one of the categories.",
    });
    const edit = listingFormSchema({ loadedCategory: "snacks" });
    expect(problems(edit, { ...valid, category: "snacks" })).toEqual({});
  });
});
