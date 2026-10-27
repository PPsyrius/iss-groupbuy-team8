import {
  Description,
  FieldError,
  Input,
  InputGroup,
  Label,
  ListBox,
  NumberField,
  Select,
  TextArea,
  TextField,
} from "@heroui/react";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useBlocker } from "@tanstack/react-router";
import { useEffect, useState } from "react";

import { CATEGORIES, categoryLabel } from "@/features/catalog/categories";

import { type FormAlertState, messageOf, placeProblem } from "./field-message";
import { FormAlert } from "./FormAlert";
import { FrozenLabel, frozenInput } from "./FrozenField";
import { imageHostError, imageHostHelp, isApprovedImageUrl } from "./image-policy";
import { ImageUrlsField } from "./ImageUrlsField";
import type { ListingFormValues } from "./listing-form";
import { listingFormSchema } from "./listing-form.schema";
import { UnsavedChangesDialog } from "./UnsavedChangesDialog";

// What a failed save hands back: messages for named fields, and a form-level alert for the rest.
export interface SubmitProblem {
  fields?: Record<string, string>;
  alert?: { title: string; description: string };
  // The session ended (401): the alert links to sign-in and the form keeps what was typed.
  signIn?: boolean;
}

// A field error as the page lists it ("Two problems to clear", A1-08).
export interface FieldProblem {
  label: string;
  message: string;
}

type FieldName = keyof ListingFormValues | `images[${number}]`;

// In form order, with the label each field shows.
const fieldLabels: Record<string, string> = {
  title: "Title",
  subtitle: "Subtitle",
  category: "Category",
  originalPrice: "Solo price",
  stock: "Stock",
  coverImage: "Cover image URL",
  images: "Image URLs",
  description: "Description",
};

const fieldNames = new Set<string>(Object.keys(fieldLabels));

// A server field path, already stripped of its `listing.` prefix; `images[3]` names a row.
function isFieldName(name: string): name is FieldName {
  return fieldNames.has(name) || /^images\[\d+\]$/.test(name);
}

function labelOf(name: string): string {
  const row = /^images\[(\d+)\]$/.exec(name)?.[1];
  return row === undefined ? (fieldLabels[name] ?? name) : `Image URL ${Number(row) + 1}`;
}

// The fields showing an error now, in form order (image rows after the image list).
function problemsOf(meta: Record<string, { errors?: readonly unknown[] } | undefined>) {
  const order = Object.keys(fieldLabels);
  const rank = (name: string) => order.indexOf(name.replace(/\[\d+\]$/, ""));
  return Object.entries(meta)
    .flatMap(([name, m]) => {
      const message = messageOf(m?.errors ?? []);
      return message === undefined ? [] : [{ name, label: labelOf(name), message }];
    })
    .toSorted((a, b) => rank(a.name) - rank(b.name) || a.name.localeCompare(b.name))
    .map(({ label, message }): FieldProblem => ({ label, message }));
}

const fixedCategories = CATEGORIES.flatMap((c) => (c.value === null ? [] : [c]));

// The listing form shared by the new-draft screen and the editor (admin plan A1-05, artboard
// 4b.1): one column, the artboard's helper text under each field. It renders a bare `<form id>`,
// so the page header's Save button submits it with `form={id}`. Validation first runs on submit,
// then on every change (A1-08, artboard 4b.4: Save stays disabled until the field error clears,
// so a fixed field must clear itself). A failed save's field messages land on their fields until
// that field changes. `onProblemsChange` tells the page which fields show an error.
//
// In the editor (A1-06) a published listing passes `frozen`: Solo price, Currency and Stock turn
// read-only with their 4b.3 reasons and the server's Sold count joins them. `guardUnsaved` asks
// before a navigation would drop unsaved edits; a navigation made by the save itself passes.
// `unsavedElsewhere` puts other unsaved edits on the page (the bloc price rule form) behind the
// same prompt, so the page has one guard.
export function ListingForm({
  id,
  defaultValues,
  onSubmit,
  frozen = false,
  soldCount,
  guardUnsaved = false,
  unsavedElsewhere = false,
  onDirtyChange,
  onProblemsChange,
}: Readonly<{
  id: string;
  defaultValues: ListingFormValues;
  onSubmit: (values: ListingFormValues) => Promise<SubmitProblem | undefined>;
  frozen?: boolean;
  soldCount?: number | undefined;
  guardUnsaved?: boolean;
  unsavedElsewhere?: boolean;
  onDirtyChange?: (dirty: boolean) => void;
  onProblemsChange?: (problems: FieldProblem[]) => void;
}>) {
  const loadedCategory =
    defaultValues.category !== "" &&
    !fixedCategories.some((c) => c.value === defaultValues.category)
      ? defaultValues.category
      : undefined;
  const categories = loadedCategory
    ? [...fixedCategories, { value: loadedCategory, label: categoryLabel(loadedCategory) }]
    : fixedCategories;
  const [alert, setAlert] = useState<FormAlertState>();
  const form = useForm({
    defaultValues,
    validationLogic: revalidateLogic(),
    validators: {
      onDynamic: listingFormSchema(loadedCategory ? { loadedCategory } : {}),
    },
    // A server's message on a field (or on one of its image rows) goes once that field changes.
    listeners: {
      onChange: ({ formApi, fieldApi }) => {
        for (const name of Object.keys(formApi.state.fieldMeta)) {
          if (name !== fieldApi.name && !name.startsWith(`${fieldApi.name}[`)) continue;
          if (!isFieldName(name) || formApi.getFieldMeta(name)?.errorMap.onServer === undefined)
            continue;
          formApi.setFieldMeta(name, (prev) => ({
            ...prev,
            errorMap: { ...prev.errorMap, onServer: undefined },
          }));
        }
      },
    },
    onSubmit: async ({ value, formApi }) => {
      setAlert(undefined);
      const problem = await onSubmit(value);
      if (!problem) return;
      setAlert(
        placeProblem(problem, (name, message) => {
          if (!isFieldName(name) || !formApi.getFieldMeta(name)) return false;
          formApi.setFieldMeta(name, (prev) => ({
            ...prev,
            errorMap: { ...prev.errorMap, onServer: message },
          }));
          return true;
        }),
      );
    },
  });

  // Still true while a save is in flight: a failed save must find its form mounted, and an unload
  // would cancel the request. A successful save remounts the form (keyed by version), so the guard
  // never stands in front of our own navigation; any that must leave a dirty form passes
  // `ignoreBlocker`.
  const hasUnsavedChanges = () => form.state.isDirty || unsavedElsewhere;
  // `enableBeforeUnload` defaults to `true`, which the browser history treats as "always prompt on
  // reload or tab close"; give it the same predicate so a clean form never prompts.
  const blocker = useBlocker({
    shouldBlockFn: hasUnsavedChanges,
    enableBeforeUnload: hasUnsavedChanges,
    disabled: !guardUnsaved,
    withResolver: true,
  });
  // The editor keeps Publish disabled while edits are unsaved (A1-07): the publish dialog confirms
  // the saved values, which are the ones that freeze.
  const isDirty = useStore(form.store, (state) => state.isDirty);
  useEffect(() => {
    onDirtyChange?.(isDirty);
  }, [isDirty, onDirtyChange]);
  // Compared by content: a fresh array on every store change would re-render the page each time.
  const problems = useStore(
    form.store,
    (state) => problemsOf(state.fieldMeta),
    (a, b) => JSON.stringify(a) === JSON.stringify(b),
  );
  useEffect(() => {
    onProblemsChange?.(problems);
  }, [problems, onProblemsChange]);
  const help = frozen ? publishedHelp : draftHelp;

  return (
    <form
      id={id}
      noValidate
      className="border-border bg-surface flex flex-col gap-5 rounded-lg border p-6"
      onSubmit={(e) => {
        e.preventDefault();
        void form.handleSubmit();
      }}
    >
      {/* Announced when it appears: the save was pressed in the header, away from this alert. */}
      <UnsavedChangesDialog
        isOpen={blocker.status === "blocked"}
        onKeepEditing={() => blocker.reset?.()}
        onDiscard={() => blocker.proceed?.()}
      />
      <FormAlert alert={alert} />

      <form.Field name="title">
        {(field) => (
          <TextField
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Title</Label>
            <Input />
            <Description>{help.title}</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </TextField>
        )}
      </form.Field>

      <form.Field name="subtitle">
        {(field) => (
          <TextField
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Subtitle</Label>
            <Input />
            <Description>{help.subtitle}</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </TextField>
        )}
      </form.Field>

      <form.Field name="category">
        {(field) => (
          <Select
            placeholder="Choose a category"
            value={field.state.value === "" ? null : field.state.value}
            onChange={(key) => field.handleChange(key === null ? "" : String(key))}
            onBlur={field.handleBlur}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Category</Label>
            <Select.Trigger>
              <Select.Value />
              <Select.Indicator />
            </Select.Trigger>
            <Description>{help.category}</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
            <Select.Popover>
              <ListBox>
                {categories.map((c) => (
                  <ListBox.Item key={c.value} id={c.value} textValue={c.label}>
                    {c.label}
                    <ListBox.ItemIndicator />
                  </ListBox.Item>
                ))}
              </ListBox>
            </Select.Popover>
          </Select>
        )}
      </form.Field>

      <div className="grid grid-cols-3 gap-4">
        {/* A text field, not a NumberField: the price is a decimal string and never a float. */}
        <form.Field name="originalPrice">
          {(field) => (
            <TextField
              value={field.state.value}
              onChange={field.handleChange}
              onBlur={field.handleBlur}
              isReadOnly={frozen}
              isInvalid={messageOf(field.state.meta.errors) !== undefined}
            >
              {frozen ? <FrozenLabel>Solo price</FrozenLabel> : <Label>Solo price</Label>}
              <InputGroup className={frozen ? frozenInput : ""}>
                <InputGroup.Prefix>S$</InputGroup.Prefix>
                <InputGroup.Input inputMode="decimal" placeholder="0.00" className="tabular-nums" />
              </InputGroup>
              <Description>{help.price}</Description>
              <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
            </TextField>
          )}
        </form.Field>

        <TextField value="SGD" isReadOnly>
          {frozen ? <FrozenLabel>Currency</FrozenLabel> : <Label>Currency</Label>}
          <Input className={frozen ? frozenInput : ""} />
          <Description>{help.currency}</Description>
        </TextField>

        <form.Field name="stock">
          {(field) => (
            <NumberField
              value={field.state.value}
              onChange={field.handleChange}
              onBlur={field.handleBlur}
              minValue={0}
              formatOptions={{ maximumFractionDigits: 0, useGrouping: false }}
              isReadOnly={frozen}
              isInvalid={messageOf(field.state.meta.errors) !== undefined}
            >
              {frozen ? <FrozenLabel>Stock</FrozenLabel> : <Label>Stock</Label>}
              <NumberField.Group className={frozen ? frozenInput : ""}>
                <NumberField.Input className="tabular-nums" />
              </NumberField.Group>
              <Description>{help.stock}</Description>
              <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
            </NumberField>
          )}
        </form.Field>
      </div>

      {soldCount === undefined ? null : (
        <div className="grid grid-cols-3 gap-4">
          <TextField value={String(soldCount)} isReadOnly>
            <Label>Sold</Label>
            <Input className={`${frozenInput} tabular-nums`} />
            <Description>Read-only, maintained by the server.</Description>
          </TextField>
        </div>
      )}

      <form.Subscribe selector={(state) => state.values}>
        {(values) =>
          [values.coverImage, ...values.images].some(
            (url) => url.trim() !== "" && !isApprovedImageUrl(url.trim()),
          ) ? (
            <p role="alert" className="text-danger-soft-foreground text-sm">
              {imageHostError} Existing URLs are kept in the form for correction, not silently
              removed.
            </p>
          ) : null
        }
      </form.Subscribe>

      <form.Field name="coverImage">
        {(field) => (
          <TextField
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Cover image URL</Label>
            <Input inputMode="url" placeholder="https://…" />
            <Description>
              Used on cards and as the gallery’s first image. {imageHostHelp}
            </Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </TextField>
        )}
      </form.Field>

      <form.Field name="images" mode="array">
        {(field) => (
          <form.Subscribe selector={(s) => s.fieldMeta}>
            {(meta) => (
              <div className="flex flex-col gap-1">
                <ImageUrlsField
                  values={field.state.value}
                  errorAt={(i) => messageOf(meta[`images[${i}]`]?.errors ?? [])}
                  onChange={(i, value) => field.replaceValue(i, value)}
                  onAdd={() => field.pushValue("")}
                  onRemove={(i) => field.removeValue(i)}
                />
                {messageOf(field.state.meta.errors) === undefined ? null : (
                  <p role="alert" className="text-danger-soft-foreground text-sm">
                    {messageOf(field.state.meta.errors)}
                  </p>
                )}
              </div>
            )}
          </form.Subscribe>
        )}
      </form.Field>

      {/* Not drawn in 4b.1 (admin plan §11 D7); the contract carries up to 10,000 characters. */}
      <form.Field name="description">
        {(field) => (
          <TextField
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Description</Label>
            <TextArea rows={6} />
            <Description>Shown on the listing page, under the gallery.</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </TextField>
        )}
      </form.Field>
    </form>
  );
}

// Helper text per field: 4b.1 for a draft, 4b.3 once the listing has been published.
const draftHelp = {
  title: "Shown on cards and on the listing page.",
  subtitle: "One short line under the title.",
  category: "One of five fixed categories.",
  price: "Decimal string, SGD only. Freezes on publish.",
  currency: "BlocBuy is Singapore-only.",
  stock: "Must be positive to publish. Freezes on publish.",
};

const publishedHelp: typeof draftHelp = {
  title: "Editable. Changes appear on the storefront immediately.",
  subtitle: "Editable.",
  category: "Editable. Moves the listing between filter chips.",
  price: "Frozen at first publish, so quotes already issued stay honest.",
  currency: "Frozen. BlocBuy is Singapore-only.",
  stock: "Frozen at first publish. Sold count moves; stock does not.",
};
