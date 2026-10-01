import { SearchField } from "@heroui/react";
import { useForm } from "@tanstack/react-form";
import { Link, useNavigate } from "@tanstack/react-router";

import { CATEGORIES } from "./categories";
import { filterFormSchema } from "./filter-form.schema";
import type { ProductsSearch } from "./products-search";

// Catalog filter from the Claude Design Catalog artboards (docs/design-screens.md §4.1
// "Filter"): a single-select chip row where choosing a chip navigates at once, no submit button.
// Chips are router links (the filter is URL state, so it deep-links and prefetches like the
// pager); the selected chip is the current page, not a link. The artboards draw no search
// field ("SearchField is designed but parked"), but the contract's `keyword` is live on dev
// since PR #107, so a compact search field sits at the end of the row, submitting on Enter
// through TanStack Form + valibot (plan §12 note 44).
export function FilterForm({ search }: Readonly<{ search: ProductsSearch }>) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3 xl:gap-4">
      <CategoryChips search={search} />
      <KeywordSearch key={search.keyword ?? ""} search={search} />
    </div>
  );
}

const chip =
  "inline-flex min-h-11 items-center rounded-full border px-4 text-sm font-medium whitespace-nowrap";
const chipOff = "border-border bg-surface text-foreground hover:bg-surface-secondary";
// accent-soft layered over the white surface, not over the canvas (artboard note: 4.83:1 on white
// against 4.46:1 on the background), hence the gradient overlay rather than a plain tint.
const chipOn =
  "border-accent-soft bg-surface bg-[image:linear-gradient(var(--accent-soft),var(--accent-soft))] text-accent-soft-foreground";

function CategoryChips({ search }: Readonly<{ search: ProductsSearch }>) {
  const current = search.category ?? null;
  const { category: _omit, ...rest } = search;
  return (
    <fieldset className="flex flex-wrap gap-2 xl:gap-2.5">
      <legend className="sr-only">Category</legend>
      {CATEGORIES.map(({ value, label }) =>
        value === current ? (
          <span key={label} aria-current="page" className={`${chip} ${chipOn}`}>
            {label}
          </span>
        ) : (
          <Link
            key={label}
            to="/"
            search={{ ...rest, page: 1, ...(value === null ? {} : { category: value }) }}
            className={`${chip} ${chipOff}`}
          >
            {label}
          </Link>
        ),
      )}
    </fieldset>
  );
}

// Remounted by `key` when the URL's keyword changes (back button, chip navigation), so the field
// always starts from the URL and needs no effect to sync.
function KeywordSearch({ search }: Readonly<{ search: ProductsSearch }>) {
  const navigate = useNavigate();
  const applied = search.keyword ?? "";
  const go = (keyword: string) => {
    const { keyword: _omit, ...rest } = search;
    return navigate({
      to: "/",
      search: { ...rest, page: 1, ...(keyword.length > 0 ? { keyword } : {}) },
    });
  };
  const form = useForm({
    defaultValues: { keyword: applied },
    validators: { onSubmit: filterFormSchema },
    onSubmit: ({ value }) => {
      const keyword = value.keyword.trim();
      if (keyword === applied) return;
      void go(keyword);
    },
  });
  return (
    <form.Field name="keyword">
      {(field) => {
        const errors = field.state.meta.errorMap.onSubmit;
        const messages = (Array.isArray(errors) ? errors : [])
          .map((e) => (typeof e === "string" ? e : e?.message))
          .filter((m): m is string => typeof m === "string" && m.length > 0);
        return (
          <search className="flex w-full flex-col gap-1 md:w-auto md:min-w-72">
            <SearchField
              aria-label="Search listings"
              variant="secondary"
              value={field.state.value}
              onChange={field.handleChange}
              onBlur={field.handleBlur}
              onSubmit={() => void form.handleSubmit()}
              onClear={() => {
                field.handleChange("");
                if (applied.length > 0) void go("");
              }}
              isInvalid={messages.length > 0}
            >
              <SearchField.Group className="min-h-11">
                <SearchField.SearchIcon />
                <SearchField.Input placeholder="Search listings" />
                <SearchField.ClearButton aria-label="Clear search" />
              </SearchField.Group>
            </SearchField>
            {messages.length > 0 ? (
              <p role="alert" className="text-danger-soft-foreground text-sm">
                {messages.join(" ")}
              </p>
            ) : null}
          </search>
        );
      }}
    </form.Field>
  );
}
