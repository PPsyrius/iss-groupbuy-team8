import { screen, waitFor } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";

import { renderRoute, serveAnonymous, serveSignedIn } from "@/test/render-route";

vi.mock("@/features/session/return-to", async (importOriginal) => ({
  ...(await importOriginal<typeof import("@/features/session/return-to")>()),
  startGoogleLogin: vi.fn<(returnTo: string) => void>(),
}));

const { startGoogleLogin } = await import("@/features/session/return-to");

describe("/login", () => {
  it("renders the sign-in screen and starts Google with the return path", async () => {
    serveAnonymous();
    renderRoute("/login?returnTo=/products/abc");
    expect(await screen.findByRole("heading", { name: "Sign in to hold your seat" })).toBeVisible();
    expect(document.title).toBe("Sign in · BlocBuy");
    // The card's mark (phone logo, desktop isologo; both render in happy-dom) beside the header's.
    expect(screen.getAllByRole("img", { name: "BlocBuy" }).length).toBeGreaterThanOrEqual(2);
    expect(screen.getByRole("link", { name: "Back to the listing" })).toHaveAttribute(
      "href",
      "/products/abc",
    );
    await userEvent.click(screen.getByRole("button", { name: "Continue with Google" }));
    expect(startGoogleLogin).toHaveBeenCalledWith("/products/abc");
  });

  it("uses the general copy and Back to browse when nothing is parked", async () => {
    serveAnonymous();
    renderRoute("/login");
    expect(await screen.findByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Back to browse" })).toHaveAttribute("href", "/");
    expect(screen.queryByRole("link", { name: "Sign in" })).not.toBeInTheDocument();
  });

  it("shows the provider error state for ?error=", async () => {
    serveAnonymous();
    renderRoute("/login?error=access_denied&returnTo=/products/abc");
    expect(await screen.findByText("Google couldn’t sign you in")).toBeVisible();
    expect(document.title).toBe("Sign in · BlocBuy");
    // One page heading, as on the default variant, so a screen reader can land on the page;
    // it stays visually hidden because the artboard leads this card with the alert.
    expect(screen.getAllByRole("heading", { level: 1 })).toHaveLength(1);
    expect(screen.getByRole("heading", { level: 1, name: "Sign in to BlocBuy" })).toHaveClass(
      "sr-only",
    );
    expect(screen.getByRole("button", { name: "Try again with Google" })).toBeVisible();
    expect(screen.getByRole("link", { name: "Back to the listing" })).toBeVisible();
  });

  it("sends an already signed-in buyer straight to the return path", async () => {
    serveSignedIn();
    const { router } = renderRoute("/login?returnTo=/account");
    await waitFor(() => expect(router.state.location.pathname).toBe("/account"));
    expect(await screen.findByRole("heading", { name: "My blocs" })).toBeVisible();
  });
});
