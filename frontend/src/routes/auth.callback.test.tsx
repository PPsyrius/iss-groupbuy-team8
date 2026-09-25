import { screen, waitFor } from "@testing-library/react";
import { beforeEach, describe, expect, it } from "vitest";

import { rememberReturnTo } from "@/features/session/return-to";
import { renderRoute, serveAnonymous, serveSignedIn } from "@/test/render-route";

describe("/auth/callback", () => {
  beforeEach(() => sessionStorage.clear());

  it("re-resolves the session and returns to the parked path", async () => {
    serveSignedIn();
    rememberReturnTo("/account");
    const { router } = renderRoute("/auth/callback");
    await waitFor(() => expect(router.state.location.pathname).toBe("/account"));
    expect(await screen.findByRole("heading", { level: 1, name: "My blocs" })).toBeVisible();
    expect(screen.getByTestId("session-user")).toHaveTextContent("Ada");
    expect(sessionStorage.getItem("blocbuy.returnTo")).toBeNull();
  });

  it("treats a missing session as a provider error", async () => {
    serveAnonymous();
    rememberReturnTo("/products/abc");
    const { router } = renderRoute("/auth/callback");
    await waitFor(() => expect(router.state.location.pathname).toBe("/login"));
    expect(router.state.location.search).toEqual({
      returnTo: "/products/abc",
      error: "no_session",
    });
    expect(await screen.findByText("Google couldn’t sign you in")).toBeVisible();
  });
});
