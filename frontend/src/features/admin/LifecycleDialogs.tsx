import { Alert, AlertDialog, Button, toast } from "@heroui/react";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";

import { ApiError } from "@/api/envelope";
import type { AdminListingResponse } from "@/api/generated/types.gen";

import { formatSgd } from "./money";
import { type LifecycleChange, useLifecycleChange } from "./use-listing-mutations";

export type DialogKind = LifecycleChange;

// The lifecycle confirmations (admin plan A1-07). Publish is artboard 4b.2: it names the three
// fields that freeze with their *saved* values, so the admin confirms numbers, not intent. The
// other three are not drawn; their copy is the §11 D2 proposal. Every dialog is pessimistic: it
// stays open while the request runs and closes only once the server agreed. A refusal stays in
// the dialog (A1-08 turns a 412 into the conflict banner).
const copy: Record<
  DialogKind,
  {
    heading: string;
    body: string;
    confirm: string;
    icon: "warning" | "danger" | "default";
    danger?: boolean;
  }
> = {
  publish: {
    heading: "Publish this listing?",
    body: "It becomes visible on the storefront immediately and buyers can start blocs on it.",
    confirm: "Publish listing",
    icon: "warning",
  },
  takeOffSale: {
    heading: "Take this listing off sale?",
    body: "Buyers can no longer start or join blocs on it. Blocs already filled keep their orders. You can put it back on sale later.",
    confirm: "Take off sale",
    icon: "warning",
  },
  putBackOnSale: {
    heading: "Put this listing back on sale?",
    body: "It becomes visible on the storefront again. Price, currency and stock are unchanged.",
    confirm: "Put back on sale",
    icon: "default",
  },
  delete: {
    heading: "Delete this draft?",
    body: "It disappears from the console. This cannot be undone.",
    confirm: "Delete draft",
    icon: "danger",
    danger: true,
  },
};

// A refused change in plain words; raw codes never reach the screen.
function refusal(error: unknown): string {
  if (error instanceof ApiError) {
    if (error.code === "CATALOG_409_004")
      return "Not available. This draft already has price or bloc history.";
    if (error.status < 500)
      return "The server refused this change. Reload the listing and try again.";
  }
  return "Something went wrong on our side. Try again.";
}

export function LifecycleDialog({
  kind,
  listing,
  onClose,
}: Readonly<{
  kind: DialogKind | null;
  listing: AdminListingResponse;
  onClose: () => void;
}>) {
  const navigate = useNavigate();
  const { run, isPending } = useLifecycleChange(listing.id ?? "", listing.version ?? 0);
  const [problem, setProblem] = useState<string>();
  const close = () => {
    setProblem(undefined);
    onClose();
  };
  const confirm = async (current: DialogKind) => {
    setProblem(undefined);
    try {
      await run(current);
    } catch (error) {
      setProblem(refusal(error));
      return;
    }
    if (current === "delete") {
      toast.success("Draft deleted");
      // Leaving a deleted draft's editor must not ask about its unsaved edits.
      await navigate({ to: "/admin/listings", search: { tab: "draft" }, ignoreBlocker: true });
    }
    close();
  };
  const text = kind ? copy[kind] : undefined;
  return (
    <AlertDialog.Backdrop
      isOpen={kind !== null}
      onOpenChange={(isOpen) => {
        if (!isOpen && !isPending) close();
      }}
    >
      <AlertDialog.Container size="sm">
        <AlertDialog.Dialog>
          {kind && text ? (
            <>
              <AlertDialog.Header>
                <AlertDialog.Icon status={text.icon} />
                <AlertDialog.Heading>{text.heading}</AlertDialog.Heading>
              </AlertDialog.Header>
              <AlertDialog.Body className="flex flex-col gap-3">
                <p>{text.body}</p>
                {kind === "publish" ? <FreezeList listing={listing} /> : null}
                {problem ? (
                  <Alert status="danger" role="alert">
                    <Alert.Content>
                      <Alert.Title>The listing was not changed</Alert.Title>
                      <Alert.Description>{problem}</Alert.Description>
                    </Alert.Content>
                  </Alert>
                ) : null}
              </AlertDialog.Body>
              <AlertDialog.Footer>
                <Button variant="tertiary" onPress={close} isDisabled={isPending}>
                  Cancel
                </Button>
                <Button
                  variant={text.danger ? "danger" : "secondary"}
                  onPress={() => void confirm(kind)}
                  isDisabled={isPending}
                >
                  {text.confirm}
                </Button>
              </AlertDialog.Footer>
            </>
          ) : null}
        </AlertDialog.Dialog>
      </AlertDialog.Container>
    </AlertDialog.Backdrop>
  );
}

// The 4b.2 box: the values the server holds now, which are the ones that freeze. Publish is only
// offered on a saved form, so these match what the admin sees in the editor.
function FreezeList({ listing }: Readonly<{ listing: AdminListingResponse }>) {
  const price = listing.listing?.originalPrice;
  const rows = [
    ["Solo price", price ? formatSgd(price) : "—"],
    ["Currency", "SGD"],
    ["Stock", String(listing.listing?.stock ?? "—")],
  ] as const;
  return (
    <>
      <fieldset className="bg-surface-secondary rounded-lg p-4">
        <legend className="float-left mb-2 font-semibold">
          These three fields freeze for good
        </legend>
        <dl className="clear-left grid grid-cols-[auto_1fr] gap-x-6 gap-y-1">
          {rows.map(([label, value]) => (
            <div key={label} className="contents">
              <dt className="text-muted">{label}</dt>
              <dd className="text-right font-medium tabular-nums">{value}</dd>
            </div>
          ))}
        </dl>
      </fieldset>
      <p className="text-muted">
        Title, subtitle, description, images and category stay editable. Price, currency and stock
        do not — and there is no route back to draft.
      </p>
    </>
  );
}
