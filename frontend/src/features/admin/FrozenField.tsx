import { Chip, Label } from "@heroui/react";

// A field that froze at first publish (artboard 4b.3) keeps its place in the form, so an admin
// looking for the price finds it and learns why it cannot move: a "Frozen" chip beside the
// label, a `surface-secondary` fill with no shadow, and a one-line reason as its description.
export const frozenInput = "bg-surface-secondary text-muted shadow-none";

export function FrozenLabel({ children }: Readonly<{ children: string }>) {
  return (
    <div className="flex items-center justify-between gap-2">
      <Label>{children}</Label>
      <Chip size="sm" variant="soft">
        Frozen
      </Chip>
    </div>
  );
}
