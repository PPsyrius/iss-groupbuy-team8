import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { GatedActionSheet } from "./GatedActionSheet";

vi.mock("./return-to", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./return-to")>()),
  startGoogleLogin: vi.fn<(returnTo: string) => void>(),
}));

const { startGoogleLogin } = await import("./return-to");

describe("GatedActionSheet", () => {
  it("explains why and offers Google or a way out", async () => {
    const onOpenChange = vi.fn<(open: boolean) => void>();
    render(<GatedActionSheet isOpen onOpenChange={onOpenChange} returnTo="/products/1" />);
    const dialog = await screen.findByRole("dialog", { name: "Sign in to hold your seat" });
    expect(dialog).toHaveTextContent(/Starting a bloc means paying first/);
    await userEvent.click(screen.getByRole("button", { name: "Not now" }));
    expect(onOpenChange).toHaveBeenCalledWith(false);
  });

  it("starts the Google login with the listing as the return path", async () => {
    render(<GatedActionSheet isOpen onOpenChange={() => {}} returnTo="/products/1" />);
    await userEvent.click(await screen.findByRole("button", { name: "Continue with Google" }));
    expect(startGoogleLogin).toHaveBeenCalledWith("/products/1");
  });

  // FB-10: a controlled sheet has no trigger, so it must not mount react-aria's PressResponder
  // (the HeroUI `Modal` root does), which warns that it has no pressable child.
  it.each([true, false])("mounts without a PressResponder warning (open: %s)", async (isOpen) => {
    const warn = vi.spyOn(console, "warn");
    render(<GatedActionSheet isOpen={isOpen} onOpenChange={() => {}} returnTo="/products/1" />);
    if (isOpen) await screen.findByRole("dialog");
    const warnings = warn.mock.calls.flat();
    warn.mockRestore();
    expect(warnings).not.toContainEqual(expect.stringContaining("PressResponder"));
  });

  it("renders nothing while closed", () => {
    render(<GatedActionSheet isOpen={false} onOpenChange={() => {}} returnTo="/products/1" />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
