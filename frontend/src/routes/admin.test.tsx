import { screen, waitFor } from "@testing-library/react";
import { http } from "msw";
import { describe, expect, it, vi } from "vitest";

import { AdminRouteError } from "@/features/admin/AdminShell";
import { adminHandlers, createAdminStore } from "@/test/admin-msw";
import { server } from "@/test/msw";
import { renderWithRouter } from "@/test/render";
import {
  renderRoute,
  serveAdmin,
  serveAnonymous,
  serveNonAdmin,
  serveSignedIn,
} from "@/test/render-route";

// The admin guard matrix (admin plan A1-03, A3-01, §5): anonymous, buyer (no admin role), admin,
// and an admin whose role went away mid-session (the admin API answers 403). The console never
// shows the storefront header.
describe("/admin", () => {
  it("uses the user id when an admin has no nickname", async () => {
    serveAdmin();
    serveSignedIn({ userId: "admin-1", nickname: null, avatarUrl: null, roles: ["admin"] });
    renderRoute("/admin");
    expect(await screen.findByText("admin-1")).toBeVisible();
  });

  it("shows the global card for a non-permission failure at the admin layout boundary", async () => {
    await renderWithRouter(<AdminRouteError error={new Error("Layout failed")} reset={() => {}} />);
    expect(screen.getByRole("heading", { name: "Something went wrong on our side" })).toBeVisible();
    expect(screen.getByRole("link", { name: "BlocBuy" })).toHaveAttribute("href", "/");
  });
  it("sends anonymous visitors to /login with the wanted path", async () => {
    serveAnonymous();
    const { router } = renderRoute("/admin");
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toEqual({ returnTo: "/admin" });
  });

  it("shows the Access denied card to a signed-in buyer, without the storefront header", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    serveNonAdmin();
    let adminReads = 0;
    server.use(
      http.get("*/api/v1/admin/*", () => {
        adminReads += 1;
      }),
    );
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
    // The session's roles decide (#282); the guard no longer asks the admin API.
    expect(adminReads).toBe(0);
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

  it("shows Access denied when the role went away mid-session", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    // `me` still says admin (cached up to a minute), but the backend now refuses the role.
    serveAdmin();
    server.use(...adminHandlers(createAdminStore(), { role: "buyer" }));
    renderRoute("/admin/listings");
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
    expect(screen.queryByTestId("session-user")).not.toBeInTheDocument();
  });
});
