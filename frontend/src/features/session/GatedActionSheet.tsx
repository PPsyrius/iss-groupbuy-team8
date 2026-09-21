import { Button, Modal } from "@heroui/react";

import { GoogleSignInButton } from "./GoogleSignInButton";

// The gate sits at the paid action, never at the door (flow map "Gate", docs/design-screens.md
// §4.3): an anonymous buyer who presses Start a bloc / Buy alone gets this sheet over the listing,
// which stays visible behind it. `placement="auto"` is the bottom sheet on phone and the centred
// 520px modal on desktop, both dismissible by tap-outside and Escape; "Not now" is a real exit.
export function GatedActionSheet({
  isOpen,
  onOpenChange,
  returnTo,
}: {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  returnTo: string;
}) {
  return (
    <Modal isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Backdrop isDismissable>
        <Modal.Container placement="auto" size="md">
          <Modal.Dialog aria-labelledby="gated-action-heading" className="gap-4 p-5 xl:p-8">
            <Modal.Header className="flex flex-col gap-2">
              <Modal.Heading id="gated-action-heading" className="text-xl font-semibold">
                Sign in to hold your seat
              </Modal.Heading>
              <p className="text-sm text-zinc-600">
                Starting a bloc means paying first — that’s what holds your seat and gives you a
                link to share. Sign in and we’ll bring you right back here.
              </p>
            </Modal.Header>
            <Modal.Footer className="flex flex-col gap-2 xl:flex-row-reverse">
              <GoogleSignInButton returnTo={returnTo} className="xl:flex-1" />
              <Button
                variant="ghost"
                className="min-h-11 w-full text-base font-medium xl:w-auto"
                onPress={() => onOpenChange(false)}
              >
                Not now
              </Button>
            </Modal.Footer>
          </Modal.Dialog>
        </Modal.Container>
      </Modal.Backdrop>
    </Modal>
  );
}
