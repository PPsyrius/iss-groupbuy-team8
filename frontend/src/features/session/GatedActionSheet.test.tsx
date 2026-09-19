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

  it("renders nothing while closed", () => {
    render(<GatedActionSheet isOpen={false} onOpenChange={() => {}} returnTo="/products/1" />);
    expect(screen.queryByRole("dialog")).not.toBeInTheDocument();
  });
});
