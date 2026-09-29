import { Button, CloseButton, Description, FieldError, Input, TextField } from "@heroui/react";

import { ImageFallback } from "@/components/ImageFallback";

import { isHttpsUrl } from "./listing-form";

const MAX_ROWS = 20;

// The gallery as a list of URL rows (artboard 4b.1 "Image URLs · up to 20"). There is no upload:
// the contract and the design take URLs only (admin plan §12 note 12). Each row previews its image
// on the striped fallback tile once the URL is an https one, so a mistyped or vanished host shows
// as stripes rather than a broken image. The rows are controlled: the form owns the values.
export function ImageUrlsField({
  values,
  errorAt,
  onChange,
  onAdd,
  onRemove,
}: {
  values: readonly string[];
  errorAt: (index: number) => string | undefined;
  onChange: (index: number, value: string) => void;
  onAdd: () => void;
  onRemove: (index: number) => void;
}) {
  return (
    <fieldset className="flex flex-col gap-2">
      <legend className="mb-1 text-sm font-medium">
        Image URLs <span className="text-muted font-normal">· up to {MAX_ROWS}</span>
      </legend>
      {values.map((value, i) => (
        // Rows have no identity beyond their position, as the contract's `images` array.
        // oxlint-disable-next-line react/no-array-index-key
        <div key={i} className="flex items-start gap-3">
          <ImageFallback
            src={isHttpsUrl(value.trim()) ? value.trim() : null}
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
          <CloseButton aria-label={`Remove image URL ${i + 1}`} onPress={() => onRemove(i)} />
        </div>
      ))}
      <Button
        variant="ghost"
        className="min-h-11 self-start px-3 text-sm"
        isDisabled={values.length >= MAX_ROWS}
        onPress={onAdd}
      >
        Add another URL
      </Button>
      <Description className="text-muted text-xs">
        URLs only — there is no file upload. Every image needs a fallback tile, because these hosts
        can disappear.
      </Description>
    </fieldset>
  );
}
