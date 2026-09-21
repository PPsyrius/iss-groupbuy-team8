import { screen, waitFor } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { renderRoute, serveAnonymous, serveSignedIn } from "@/test/render-route";

describe("/account behind the _authenticated guard", () => {
  it("sends anonymous visitors to /login with the wanted path", async () => {
    serveAnonymous();
    const { router } = renderRoute("/account");
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toEqual({ returnTo: "/account" });
    expect(await screen.findByRole("heading", { name: "Sign in to BlocBuy" })).toBeVisible();
  });

  it("renders the interim account page for a signed-in buyer", async () => {
    serveSignedIn({ userId: "u1", nickname: null, avatarUrl: null });
    const { router } = renderRoute("/account");
    expect(await screen.findByRole("heading", { name: "My blocs" })).toBeVisible();
    expect(screen.getByTestId("account-user")).toHaveTextContent("u1");
    expect(screen.getByText("You haven’t joined a bloc yet")).toBeVisible();
    expect(screen.getByRole("link", { name: "See what’s filling" })).toHaveAttribute("href", "/");
    expect(router.state.location.pathname).toBe("/account");
  });
});
