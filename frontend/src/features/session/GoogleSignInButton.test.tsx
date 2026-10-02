import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { GoogleSignInButton } from "./GoogleSignInButton";

vi.mock("./return-to", async (importOriginal) => ({
  ...(await importOriginal<typeof import("./return-to")>()),
  startGoogleLogin: vi.fn<(returnTo: string) => void>(),
}));

const { startGoogleLogin } = await import("./return-to");

describe("GoogleSignInButton", () => {
  // FB-13 (CR-16): the official Google "G" from Google's sign-in asset pack, served same-origin
  // from `public/` (the CSP's `img-src` has no `data:`), decorative because the label names Google.
  it("leads with the official Google G and keeps the label as its name", () => {
    const { container } = render(<GoogleSignInButton returnTo="/" />);
    const button = screen.getByRole("button", { name: "Continue with Google" });
    const mark = container.querySelector("img");
    expect(mark).toHaveAttribute("src", "/google-g.svg");
    expect(mark).toHaveAttribute("alt", "");
    expect(mark).toHaveAttribute("width", "20");
    expect(mark).toHaveAttribute("height", "20");
    expect(button).toContainElement(mark);
    expect(button).toHaveTextContent(/^Continue with Google$/);
  });

  it("takes a custom label and starts the login with the return path", async () => {
    render(<GoogleSignInButton returnTo="/account" label="Sign in with Google" />);
    await userEvent.click(screen.getByRole("button", { name: "Sign in with Google" }));
    expect(startGoogleLogin).toHaveBeenCalledWith("/account");
  });
});
