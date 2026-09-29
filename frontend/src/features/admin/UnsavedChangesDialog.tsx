import { AlertDialog, Button } from "@heroui/react";

// Leaving the editor with unsaved changes (admin plan A1-06, §11 D2/D5; the prompt is not drawn,
// so the copy is the plan's proposal). Keep editing is the safe default; Discard lets the
// navigation through.
export function UnsavedChangesDialog({
  isOpen,
  onKeepEditing,
  onDiscard,
}: {
  isOpen: boolean;
  onKeepEditing: () => void;
  onDiscard: () => void;
}) {
  return (
    <AlertDialog.Backdrop
      isOpen={isOpen}
      onOpenChange={(open) => {
        if (!open) onKeepEditing();
      }}
    >
      <AlertDialog.Container size="sm">
        <AlertDialog.Dialog>
          <AlertDialog.Header>
            <AlertDialog.Icon status="warning" />
            <AlertDialog.Heading>Discard your changes?</AlertDialog.Heading>
          </AlertDialog.Header>
          <AlertDialog.Body>
            <p>You have edits on this listing that are not saved. Leaving now loses them.</p>
          </AlertDialog.Body>
          <AlertDialog.Footer>
            <Button variant="tertiary" onPress={onKeepEditing}>
              Keep editing
            </Button>
            <Button variant="danger" onPress={onDiscard}>
              Discard
            </Button>
          </AlertDialog.Footer>
        </AlertDialog.Dialog>
      </AlertDialog.Container>
    </AlertDialog.Backdrop>
  );
}
