import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { UnsavedChangesDialog } from "./UnsavedChangesDialog";

describe("UnsavedChangesDialog", () => {
  it.each([
    ["Keep editing", 1, 0],
    ["Discard", 0, 1],
    ["Escape", 0, 0],
  ] as const)(
    "handles %s without choosing the other action",
    async (action, keepCalls, discardCalls) => {
      const user = userEvent.setup();
      const onKeepEditing = vi.fn<() => void>();
      const onDiscard = vi.fn<() => void>();
      render(<UnsavedChangesDialog isOpen onKeepEditing={onKeepEditing} onDiscard={onDiscard} />);
      await screen.findByRole("alertdialog", { name: "Discard your changes?" });
      if (action === "Escape") await user.keyboard("{Escape}");
      else await user.click(screen.getByRole("button", { name: action }));
      expect(onKeepEditing).toHaveBeenCalledTimes(keepCalls);
      expect(onDiscard).toHaveBeenCalledTimes(discardCalls);
      expect(screen.getByRole("alertdialog", { name: "Discard your changes?" })).toBeVisible();
    },
  );
});
