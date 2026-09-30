import { screen, waitFor } from "@testing-library/react";
import { HttpResponse, http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { server } from "@/test/msw";
import {
  renderRoute,
  serveAdmin,
  serveAnonymous,
  serveNonAdmin,
  serveSignedIn,
} from "@/test/render-route";

// The admin guard matrix (admin plan A1-03, §5): anonymous, buyer (probe 403), admin, and a
// probe that fails for any other reason. The console never shows the storefront header.
describe("/admin", () => {
  it("sends anonymous visitors to /login with the wanted path", async () => {
    serveAnonymous();
    const { router } = renderRoute("/admin");
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toEqual({ returnTo: "/admin" });
  });

  it("shows the Access denied card to a signed-in buyer, without the storefront header", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    serveNonAdmin();
    const { router } = renderRoute("/admin");
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
    expect(router.state.location.pathname).toBe("/admin");
    expect(screen.getByText("403")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse listings" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "My blocs" })).toHaveAttribute("href", "/account");
    expect(screen.queryByTestId("session-user")).not.toBeInTheDocument();
    expect(screen.queryByRole("button", { name: "Sign out" })).not.toBeInTheDocument();
  });

  it("renders the admin rail for an admin, without the storefront header", async () => {
    serveAdmin();
    renderRoute("/admin");
    expect(await screen.findByRole("heading", { name: "Dashboard" })).toBeVisible();
    const rail = screen.getByRole("navigation", { name: "Admin" });
    expect(rail).toHaveTextContent("Dashboard");
    expect(screen.getByRole("link", { name: "Dashboard" })).toHaveAttribute("href", "/admin");
    expect(screen.getByRole("img", { name: "BlocBuy" })).toBeInTheDocument();
    expect(screen.getByText("Ada")).toBeVisible();
    expect(screen.getByText("Admin")).toBeVisible();
    expect(screen.queryByRole("link", { name: "Sign in" })).not.toBeInTheDocument();
    expect(screen.queryByTestId("session-user")).not.toBeInTheDocument();
  });

  it("shows the global error card when the probe fails for another reason", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    serveSignedIn();
    server.use(
      http.get("*/api/v1/admin/products", () =>
        HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 }),
      ),
    );
    renderRoute("/admin");
    expect(
      await screen.findByRole("heading", { name: "Something went wrong on our side" }),
    ).toBeVisible();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
    expect(screen.queryByTestId("session-user")).not.toBeInTheDocument();
  });
});
