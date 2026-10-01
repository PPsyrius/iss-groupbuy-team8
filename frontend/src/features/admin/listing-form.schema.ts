import * as v from "valibot";

import { CATEGORIES } from "@/features/catalog/categories";

import { isHttpsUrl } from "./listing-form";
import { toCents } from "./money";

// The listing form's rules, mirroring the contract's `ListingContent` and `CreateListingRequest`
// validation (admin plan §2.2) so a draft is refused here before the backend would refuse it.
// Fields are trimmed only for checking; `toListingContent` trims what is sent.

const PRODUCT_NO = /^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$/;
// Contract: a positive decimal string with at most 16 integer digits and 2 decimals.
const PRICE = /^\d{1,16}(\.\d{1,2})?$/;
const URL_MAX = 1024;
const IMAGES_MAX = 20;
const HTTPS = "Use an https:// URL.";
const STOCK = "Enter a whole number from 0.";

const imageUrl = v.pipe(
  v.string(),
  v.trim(),
  v.check((url) => url === "" || isHttpsUrl(url), HTTPS),
  v.maxLength(URL_MAX, `Keep the URL under ${URL_MAX} characters.`),
);

const fixedCategories: string[] = CATEGORIES.flatMap((c) => (c.value === null ? [] : [c.value]));

export function listingFormSchema({
  mode,
  loadedCategory,
}: {
  mode: "create" | "edit";
  // An existing listing may carry a category the fixed list does not know yet; it stays allowed.
  loadedCategory?: string;
}) {
  const categories = loadedCategory ? [...fixedCategories, loadedCategory] : fixedCategories;
  return v.object({
    // The product number is set once, on create; the editor shows it but never sends it.
    productNo:
      mode === "create"
        ? v.pipe(
            v.string(),
            v.trim(),
            v.nonEmpty("Enter a product number."),
            v.check(
              (no) => no === "" || PRODUCT_NO.test(no),
              "Use letters, digits, dots, dashes or underscores, starting with a letter or digit.",
            ),
          )
        : v.string(),
    title: v.pipe(
      v.string(),
      v.trim(),
      v.nonEmpty("Enter a title."),
      v.maxLength(256, "Keep the title under 256 characters."),
    ),
    subtitle: v.pipe(
      v.string(),
      v.trim(),
      v.maxLength(256, "Keep the subtitle under 256 characters."),
    ),
    description: v.pipe(
      v.string(),
      v.trim(),
      v.maxLength(10_000, "Keep the description under 10,000 characters."),
    ),
    category: v.pipe(
      v.string(),
      v.check((c) => c === "" || categories.includes(c), "Choose one of the categories."),
    ),
    originalPrice: v.pipe(
      v.string(),
      v.trim(),
      v.regex(PRICE, "Enter the price in dollars and cents, like 46.80."),
      // Only a well-formed price reaches this check; the regex above reports the rest.
      v.check(
        (price) => !PRICE.test(price) || (toCents(price) ?? 0n) > 0n,
        "Enter a price above S$0.00",
      ),
    ),
    // A cleared NumberField reads NaN, which `v.number` refuses with the same message.
    stock: v.pipe(
      v.number(STOCK),
      v.check((n) => Number.isInteger(n) && n >= 0, STOCK),
    ),
    coverImage: imageUrl,
    images: v.pipe(
      v.array(imageUrl),
      v.check(
        (urls) => urls.filter((url) => url !== "").length <= IMAGES_MAX,
        `Add at most ${IMAGES_MAX} image URLs.`,
      ),
    ),
  });
}
