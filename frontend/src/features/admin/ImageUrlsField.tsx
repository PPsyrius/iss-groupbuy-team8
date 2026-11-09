import { Button, CloseButton, Description, FieldError, Input, TextField } from "@heroui/react";
import { useState } from "react";

import { ImageFallback } from "@/components/ImageFallback";

import { imageHostHelp, isApprovedImageUrl } from "./image-policy";

const MAX_ROWS = 20;

// The gallery as a list of URL rows (artboard 4b.1 "Image URLs · up to 20"). There is no upload:
// the contract and the design take URLs only (admin plan §12 note 12). Each row previews its image
// on the striped fallback tile once CSP permits the URL, so a mistyped or vanished host shows
// as stripes rather than a broken image. The rows are controlled: the form owns the values, and this
// field owns only a stable id per row, so removing a middle row keeps every other row's input.
// The form remounts this field on reset (the editor is keyed by version), which re-seeds the ids.
export function ImageUrlsField({
  values,
  errorAt,
  onChange,
  onAdd,
  onRemove,
}: Readonly<{
  values: readonly string[];
  errorAt: (index: number) => string | undefined;
  onChange: (index: number, value: string) => void;
  onAdd: () => void;
  onRemove: (index: number) => void;
}>) {
  const [ids, setIds] = useState(() => values.map(() => crypto.randomUUID()));
  const rows = values.map((value, i) => ({ value, id: ids[i] }));
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-sm font-medium">
        Image URLs <span className="text-muted font-normal">· up to {MAX_ROWS}</span>
      </legend>
      {rows.map(({ value, id }, i) => (
        <div key={id} className="flex items-start gap-3">
          <ImageFallback
            src={isApprovedImageUrl(value.trim()) ? value.trim() : null}
            className="size-11 shrink-0 overflow-hidden rounded-md"
          />
          <TextField
            aria-label={`Image URL ${i + 1}`}
            className="flex-1"
            value={value}
            onChange={(next) => onChange(i, next)}
            isInvalid={errorAt(i) !== undefined}
          >
            <Input placeholder="https://…" inputMode="url" />
            <FieldError>{errorAt(i)}</FieldError>
          </TextField>
          <CloseButton
            aria-label={`Remove image URL ${i + 1}`}
            onPress={() => {
              setIds((prev) => prev.filter((_, j) => j !== i));
              onRemove(i);
            }}
          />
        </div>
      ))}
      <Button
        variant="ghost"
        className="min-h-11 self-start px-3 text-sm"
        isDisabled={values.length >= MAX_ROWS}
        onPress={() => {
          setIds((prev) => [...prev, crypto.randomUUID()]);
          onAdd();
        }}
      >
        Add another URL
      </Button>
      <Description className="text-muted text-xs">
        URLs only — there is no file upload. {imageHostHelp}
      </Description>
    </fieldset>
  );
}
