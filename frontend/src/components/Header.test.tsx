import { screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { HttpResponse, http } from "msw";
import { describe, expect, it } from "vitest";

import { server } from "@/test/msw";
import { renderWithRouter } from "@/test/render";

import { Header } from "./Header";

describe("Header", () => {
  it("shows the neutral Sign in control pointing at /login when /auth/me answers 401", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({ code: "AUTH_401", message: "unauthenticated" }, { status: 401 }),
      ),
    );
    await renderWithRouter(<Header />);
    const signIn = await screen.findByRole("link", { name: "Sign in" });
    expect(signIn.getAttribute("href")).toMatch(/^\/login\?returnTo=/);
    expect(signIn.className).not.toMatch(/primary/);
    // The SVG logo's alt names the home link (CR-7).
    expect(screen.getByRole("link", { name: "BlocBuy" })).toHaveAttribute("href", "/");
    expect(screen.queryByTestId("session-user")).not.toBeInTheDocument();
  });

  it("shows the avatar initials and nickname when /auth/me answers 200", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: "Ada Lim", avatarUrl: null },
          message: "ok",
        }),
      ),
    );
    await renderWithRouter(<Header />);
    expect(await screen.findByTestId("session-user")).toHaveTextContent("Ada Lim");
    expect(screen.getByText("AL")).toBeInTheDocument();
    expect(screen.queryByRole("link", { name: "Sign in" })).not.toBeInTheDocument();
  });

  it("signs out: posts to /auth/logout, then the Sign in control replaces the user", async () => {
    const user = userEvent.setup();
    let logoutCalls = 0;
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: "Ada Lim", avatarUrl: null },
          message: "ok",
        }),
      ),
      http.post("*/api/v1/auth/logout", () => {
        logoutCalls += 1;
        return HttpResponse.json({ code: "COMMON_200_000", message: "ok" });
      }),
    );
    const { router } = await renderWithRouter(<Header />);
    await screen.findByTestId("session-user");
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("link", { name: "Sign in" })).toBeInTheDocument();
    expect(logoutCalls).toBe(1);
    expect(screen.queryByTestId("session-user")).not.toBeInTheDocument();
    expect(router.state.location.pathname).toBe("/");
  });

  it("keeps the user and says so when /auth/logout fails", async () => {
    const user = userEvent.setup();
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: "Ada Lim", avatarUrl: null },
          message: "ok",
        }),
      ),
      http.post("*/api/v1/auth/logout", () =>
        HttpResponse.json({ code: "COMMON_500_000", message: "boom" }, { status: 500 }),
      ),
    );
    await renderWithRouter(<Header />);
    await screen.findByTestId("session-user");
    await user.click(screen.getByRole("button", { name: "Sign out" }));
    expect(await screen.findByRole("alert")).toHaveTextContent("Could not sign out");
    expect(screen.getByTestId("session-user")).toHaveTextContent("Ada Lim");
    expect(screen.queryByRole("link", { name: "Sign in" })).not.toBeInTheDocument();
  });

  it("falls back to the user id when the nickname is null", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: null, avatarUrl: "https://example.test/avatar.jpg" },
          message: "ok",
        }),
      ),
    );
    await renderWithRouter(<Header />);
    expect(await screen.findByTestId("session-user")).toHaveTextContent("u1");
  });
});
