import { screen } from "@testing-library/react";
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

  it("falls back to the user id when the nickname is null", async () => {
    server.use(
      http.get("*/api/v1/auth/me", () =>
        HttpResponse.json({
          code: "COMMON_200_000",
          data: { userId: "u1", nickname: null, avatarUrl: null },
          message: "ok",
        }),
      ),
    );
    await renderWithRouter(<Header />);
    expect(await screen.findByTestId("session-user")).toHaveTextContent("u1");
  });
});
