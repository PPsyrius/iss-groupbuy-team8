import { Alert, AlertDialog, Button, toast } from "@heroui/react";
import { useQueryClient } from "@tanstack/react-query";
import { useNavigate } from "@tanstack/react-router";
import { useState } from "react";

import type { AdminListingResponse } from "@/api/generated/types.gen";

import { forgetListingPages } from "./admin-api";
import { refusalOf } from "./listing-errors";
import { formatSgd } from "./money";
import {
  type LifecycleChange,
  useLifecycleChange,
  useRouteBoundary,
} from "./use-listing-mutations";

export type DialogKind = LifecycleChange;

// The lifecycle confirmations (admin plan A1-07). Publish is artboard 4b.2: it names the three
// fields that freeze with their *saved* values, so the admin confirms numbers, not intent. The
// other three are not drawn; their copy is the §11 D2 proposal. Every dialog is pessimistic: it
// stays open while the request runs and closes only once the server agreed. A refusal stays in
// the dialog in plain words (A1-08), except a 412: the dialog closes and `onConflict` raises the
// page's conflict banner with the server's version from the 412 (#286), and a 403, which goes to
// the route boundary.
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

// Where no page banner listens (the table's row menu), a 412 reads as a message and the table's
// rows are fetched again, so a second try works from the fresh version.
const staleRow =
  "Someone else changed this listing while the table was open. Its row has been refreshed; try again.";

export function LifecycleDialog({
  kind,
  listing,
  onClose,
  onConflict,
}: Readonly<{
  kind: DialogKind | null;
  listing: AdminListingResponse;
  onClose: () => void;
  onConflict?: (serverVersion: number | null) => void;
}>) {
  const navigate = useNavigate();
  const toBoundary = useRouteBoundary();
  const qc = useQueryClient();
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
      const refusal = refusalOf(error);
      switch (refusal.kind) {
        case "forbidden":
          toBoundary(error);
          return;
        case "conflict":
          if (onConflict) {
            close();
            onConflict(refusal.serverVersion);
          } else {
            setProblem(staleRow);
            forgetListingPages(qc);
          }
          return;
        case "session":
          setProblem("Your session ended. Sign in again to change this listing.");
          return;
        case "fields":
          setProblem("The server refused this change. Reload the listing and try again.");
          return;
        case "message":
          setProblem(refusal.message);
          return;
      }
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
    <AlertDialog.Backdrop isOpen={kind !== null}>
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
