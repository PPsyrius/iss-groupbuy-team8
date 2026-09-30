import { Button, Dropdown, Label } from "@heroui/react";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";

import type { AdminListingResponse } from "@/api/generated/types.gen";

import { actionsFor, type LifecycleKind } from "./lifecycle";
import { type DialogKind, LifecycleDialog } from "./LifecycleDialogs";

const labels: Record<DialogKind, string> = {
  publish: "Publish",
  takeOffSale: "Take off sale",
  putBackOnSale: "Put back on sale",
  delete: "Delete draft",
};

const isDialogKind = (kind: LifecycleKind): kind is DialogKind => kind !== "deleteBlocked";

// A table row's menu (admin plan A1-07, §12 note 10: HeroUI `Dropdown` for menu semantics and
// keyboard support): Edit, then what `actionsFor` allows on the saved row, opening the same
// dialogs as the editor. A published row has no Delete item; the editor explains why.
export function RowMenu({ listing }: Readonly<{ listing: AdminListingResponse }>) {
  const navigate = useNavigate();
  const [open, setOpen] = useState<DialogKind | null>(null);
  const actions = actionsFor(listing.status, {
    dirty: false,
    stock: listing.listing?.stock ?? 0,
    price: listing.listing?.originalPrice ?? "",
  }).filter((a) => isDialogKind(a.kind));
  const title = listing.listing?.title ?? listing.productNo ?? "listing";
  return (
    <>
      <Dropdown>
        <Button isIconOnly size="sm" variant="ghost" aria-label={`Actions for ${title}`}>
          <svg aria-hidden="true" viewBox="0 0 16 16" className="size-4 fill-current">
            <circle cx="3" cy="8" r="1.5" />
            <circle cx="8" cy="8" r="1.5" />
            <circle cx="13" cy="8" r="1.5" />
          </svg>
        </Button>
        <Dropdown.Popover placement="bottom end">
          <Dropdown.Menu
            aria-label={`Actions for ${title}`}
            onAction={(key) => {
              if (key === "edit") {
                void navigate({
                  to: "/admin/listings/$productId",
                  params: { productId: listing.id ?? "" },
                });
                return;
              }
              const chosen = actions.find((a) => a.kind === key)?.kind;
              if (chosen && isDialogKind(chosen)) setOpen(chosen);
            }}
          >
            <Dropdown.Item id="edit" textValue="Edit">
              <Label>Edit</Label>
            </Dropdown.Item>
            {actions.map(({ kind, enabled }) =>
              isDialogKind(kind) ? (
                <Dropdown.Item
                  key={kind}
                  id={kind}
                  textValue={labels[kind]}
                  isDisabled={!enabled}
                  variant={kind === "delete" ? "danger" : "default"}
                >
                  <Label>{labels[kind]}</Label>
                </Dropdown.Item>
              ) : null,
            )}
          </Dropdown.Menu>
        </Dropdown.Popover>
      </Dropdown>
      <LifecycleDialog kind={open} listing={listing} onClose={() => setOpen(null)} />
    </>
  );
}
