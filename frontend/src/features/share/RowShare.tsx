import { Button } from "@heroui/react";
import { useId, useState } from "react";

import { ShareIcon, ShareInvitePanel, type ShareMessageFacts } from "./ShareBlock";

// Sharing from a My blocs row (UC7, #30; design-screens §4.4). The artboard puts the share block
// on the bloc-detail screen, which does not exist yet, so an open row carries a compact version:
// an `outline` "Share invite link" button (44px, full width, under a separator) that expands the
// member-view block, link field and Copy with the inline "Invite link copied", in place. The
// invite is minted only when the panel first opens, never for every row on page load; collapsing
// keeps the minted ref in the query cache, so reopening does not ask again. The button keeps its
// name and reports the state through `aria-expanded`.
export function RowShare({
  userId,
  dealId,
  productId,
  message,
}: {
  userId: string;
  dealId: string;
  productId: string;
  message: ShareMessageFacts;
}) {
  const [open, setOpen] = useState(false);
  const panelId = useId();
  return (
    <div className="border-separator flex flex-col gap-3 border-t pt-3">
      <Button
        variant="outline"
        className="min-h-11 w-full text-sm font-semibold"
        aria-expanded={open}
        {...(open ? { "aria-controls": panelId } : {})}
        onPress={() => setOpen((v) => !v)}
      >
        <ShareIcon />
        Share invite link
      </Button>
      {open ? (
        <section
          id={panelId}
          aria-label={`Invite link for ${message.title}`}
          className="flex flex-col gap-3"
        >
          <ShareInvitePanel
            userId={userId}
            dealId={dealId}
            productId={productId}
            message={message}
            compact
          />
        </section>
      ) : null}
    </div>
  );
}
