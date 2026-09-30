import {
  Alert,
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
import { useForm } from "@tanstack/react-form";
import { useState } from "react";

import { CATEGORIES, categoryLabel } from "@/features/catalog/categories";

import { ImageUrlsField } from "./ImageUrlsField";
import type { ListingFormValues } from "./listing-form";
import { listingFormSchema } from "./listing-form.schema";

// What a failed save hands back: messages for named fields, and a form-level alert for the rest.
export interface SubmitProblem {
  fields?: Record<string, string>;
  alert?: { title: string; description: string };
}

type FieldName = keyof ListingFormValues | `images[${number}]`;

const fieldNames = new Set<string>([
  "productNo",
  "title",
  "subtitle",
  "description",
  "category",
  "originalPrice",
  "stock",
  "coverImage",
  "images",
]);

// A server field path, already stripped of its `listing.` prefix; `images[3]` names a row.
function isFieldName(name: string): name is FieldName {
  return fieldNames.has(name) || /^images\[\d+\]$/.test(name);
}

// TanStack Form keeps every validator's errors: valibot issues from the schema, or the plain
// strings a failed save puts there.
function messageOf(errors: readonly unknown[]): string | undefined {
  const messages = errors.flatMap((e) => {
    if (typeof e === "string") return [e];
    if (e && typeof e === "object" && "message" in e && typeof e.message === "string")
      return [e.message];
    return [];
  });
  return messages.length > 0 ? messages.join(" ") : undefined;
}

const fixedCategories = CATEGORIES.flatMap((c) => (c.value === null ? [] : [c]));

// The listing form shared by the new-draft screen and the editor (admin plan A1-05, artboard
// 4b.1): one column, the artboard's helper text under each field. It renders a bare `<form id>`,
// so the page header's Save button submits it with `form={id}`. Validation runs on submit only;
// a failed save's field messages land on their fields until the next submit.
export function ListingForm({
  id,
  mode,
  defaultValues,
  onSubmit,
}: Readonly<{
  id: string;
  mode: "create" | "edit";
  defaultValues: ListingFormValues;
  onSubmit: (values: ListingFormValues) => Promise<SubmitProblem | undefined>;
}>) {
  const loadedCategory =
    defaultValues.category !== "" &&
    !fixedCategories.some((c) => c.value === defaultValues.category)
      ? defaultValues.category
      : undefined;
  const categories = loadedCategory
    ? [...fixedCategories, { value: loadedCategory, label: categoryLabel(loadedCategory) }]
    : fixedCategories;
  const [alert, setAlert] = useState<SubmitProblem["alert"]>();
  const form = useForm({
    defaultValues,
    validators: {
      onSubmit: listingFormSchema({ mode, ...(loadedCategory ? { loadedCategory } : {}) }),
    },
    onSubmit: async ({ value, formApi }) => {
      setAlert(undefined);
      const problem = await onSubmit(value);
      if (!problem) return;
      const unplaced: string[] = [];
      for (const [name, message] of Object.entries(problem.fields ?? {})) {
        if (isFieldName(name) && formApi.getFieldMeta(name)) {
          // Marked as a form-level error so the next submit's validation clears it.
          formApi.setFieldMeta(name, (prev) => ({
            ...prev,
            errorMap: { ...prev.errorMap, onSubmit: message },
            errorSourceMap: { ...prev.errorSourceMap, onSubmit: "form" },
          }));
        } else {
          unplaced.push(message);
        }
      }
      if (problem.alert || unplaced.length > 0) {
        setAlert({
          title: problem.alert?.title ?? "Some fields need another look",
          description: [problem.alert?.description, ...unplaced].filter(Boolean).join(" "),
        });
      }
    },
  });

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
      {alert ? (
        <Alert status="danger" role="alert">
          <Alert.Content>
            <Alert.Title>{alert.title}</Alert.Title>
            {alert.description ? <Alert.Description>{alert.description}</Alert.Description> : null}
          </Alert.Content>
        </Alert>
      ) : null}

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
            <Description>Shown on cards and on the listing page.</Description>
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
            <Description>One short line under the title.</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </TextField>
        )}
      </form.Field>

      {/* The artboard leaves the product number out ("the server assigns it on save"), but the
          contract's CreateListingRequest requires it from the client (admin plan §2.2). */}
      {mode === "create" ? (
        <form.Field name="productNo">
          {(field) => (
            <TextField
              value={field.state.value}
              onChange={field.handleChange}
              onBlur={field.handleBlur}
              isInvalid={messageOf(field.state.meta.errors) !== undefined}
            >
              <Label>Product number</Label>
              <Input className="font-mono" placeholder="BB-100490" />
              <Description>
                Your reference for this listing. It can’t change after saving.
              </Description>
              <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
            </TextField>
          )}
        </form.Field>
      ) : null}

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
            <Description>One of five fixed categories.</Description>
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
              isInvalid={messageOf(field.state.meta.errors) !== undefined}
            >
              <Label>Solo price</Label>
              <InputGroup>
                <InputGroup.Prefix>S$</InputGroup.Prefix>
                <InputGroup.Input inputMode="decimal" placeholder="0.00" className="tabular-nums" />
              </InputGroup>
              <Description>Decimal string, SGD only. Freezes on publish.</Description>
              <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
            </TextField>
          )}
        </form.Field>

        <TextField value="SGD" isReadOnly>
          <Label>Currency</Label>
          <Input />
          <Description>BlocBuy is Singapore-only.</Description>
        </TextField>

        <form.Field name="stock">
          {(field) => (
            <NumberField
              value={field.state.value}
              onChange={field.handleChange}
              onBlur={field.handleBlur}
              minValue={0}
              formatOptions={{ maximumFractionDigits: 0, useGrouping: false }}
              isInvalid={messageOf(field.state.meta.errors) !== undefined}
            >
              <Label>Stock</Label>
              <NumberField.Group>
                <NumberField.Input className="tabular-nums" />
              </NumberField.Group>
              <Description>Must be positive to publish. Freezes on publish.</Description>
              <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
            </NumberField>
          )}
        </form.Field>
      </div>

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
            <Description>Used on cards and as the gallery’s first image.</Description>
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
