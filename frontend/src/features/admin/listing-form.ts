import type { AdminListingResponse, ListingContent } from "@/api/generated/types.gen";

// The listing form's values and their mapping to and from the contract's `ListingContent` (admin
// plan A1-05). Every text is kept as typed and trimmed on the way out; the price stays the string
// the server sent (§12 note 8), so a revise after publish sends it back byte for byte and the
// backend's frozen-field check never trips on formatting.
export interface ListingFormValues {
  title: string;
  subtitle: string;
  description: string;
  category: string;
  originalPrice: string;
  stock: number;
  coverImage: string;
  images: string[];
}

// 4b.1 opens with one empty image row and a stock of 0.
export const emptyListingForm: ListingFormValues = {
  title: "",
  subtitle: "",
  description: "",
  category: "",
  originalPrice: "",
  stock: 0,
  coverImage: "",
  images: [""],
};

// The CSP allows `img-src 'self' https:` with `upgrade-insecure-requests`, so an image URL is
// accepted only over https (Global Constraints).
export function isHttpsUrl(value: string): boolean {
  if (!URL.canParse(value)) return false;
  const url = new URL(value);
  return url.protocol === "https:" && url.hostname !== "";
}

// A revise replaces the whole listing, so an optional left empty is omitted (the backend clears
// it) rather than sent as "". Blank image rows are dropped.
export function toListingContent(values: ListingFormValues): ListingContent {
  const optional = (key: "subtitle" | "description" | "category" | "coverImage") => {
    const value = values[key].trim();
    return value === "" ? {} : { [key]: value };
  };
  const images = values.images.map((url) => url.trim()).filter((url) => url !== "");
  return {
    title: values.title.trim(),
    ...optional("subtitle"),
    ...optional("description"),
    ...optional("category"),
    originalPrice: values.originalPrice.trim(),
    currency: "sgd",
    stock: values.stock,
    ...optional("coverImage"),
    ...(images.length === 0 ? {} : { images }),
  };
}

export function fromListing(dto: AdminListingResponse): ListingFormValues {
  const listing = dto.listing;
  return {
    title: listing?.title ?? "",
    subtitle: listing?.subtitle ?? "",
    description: listing?.description ?? "",
    category: listing?.category ?? "",
    originalPrice: listing?.originalPrice ?? "",
    stock: listing?.stock ?? 0,
    coverImage: listing?.coverImage ?? "",
    images: listing?.images ?? [],
  };
}
