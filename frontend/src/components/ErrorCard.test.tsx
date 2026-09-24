import {
  createMemoryHistory,
  createRootRoute,
  createRoute,
  createRouter,
  RouterProvider,
} from "@tanstack/react-router";
import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { afterEach, describe, expect, it, vi } from "vitest";

import { AppErrorBoundary } from "./AppErrorBoundary";
import { NotFoundCard, RouteErrorCard } from "./ErrorCard";

afterEach(() => {
  vi.restoreAllMocks();
});

describe("NotFoundCard", () => {
  it("renders the §4.5 404 card with one way out", async () => {
    const rootRoute = createRootRoute({ component: () => <NotFoundCard /> });
    const router = createRouter({
      routeTree: rootRoute,
      history: createMemoryHistory({ initialEntries: ["/nowhere"] }),
    });
    render(<RouterProvider router={router} />);
    expect(await screen.findByRole("heading", { name: "We can’t find that page" })).toBeVisible();
    expect(screen.getByText("404")).toBeInTheDocument();
    expect(screen.getByText("Nothing has been charged.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse listings" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("heading", { name: "Came from an invite link?" })).toBeVisible();
  });
});

describe("RouteErrorCard", () => {
  it("replaces a route whose loader throws, and Try again re-runs the loader", async () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    let attempts = 0;
    const rootRoute = createRootRoute({ errorComponent: RouteErrorCard });
    const indexRoute = createRoute({
      getParentRoute: () => rootRoute,
      path: "/",
      loader: () => {
        attempts += 1;
        if (attempts === 1) throw new Error("backend down");
        return { ok: true };
      },
      component: () => <p>Catalogue loaded</p>,
    });
    const router = createRouter({
      routeTree: rootRoute.addChildren([indexRoute]),
      history: createMemoryHistory({ initialEntries: ["/"] }),
      defaultErrorComponent: RouteErrorCard,
    });
    render(<RouterProvider router={router} />);

    expect(
      await screen.findByRole("heading", { name: "Something went wrong on our side" }),
    ).toBeVisible();
    expect(screen.getByText("500")).toBeInTheDocument();
    expect(screen.getByText("No payment was taken.")).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse listings" })).toHaveAttribute("href", "/");

    await userEvent.click(screen.getByRole("button", { name: "Try again" }));
    expect(await screen.findByText("Catalogue loaded")).toBeVisible();
    expect(attempts).toBe(2);
  });
});

function Thrower(): never {
  throw new Error("render crash");
}

describe("AppErrorBoundary", () => {
  it("shows the 500 card when a child throws while rendering", () => {
    vi.spyOn(console, "error").mockImplementation(() => {});
    render(
      <AppErrorBoundary>
        <Thrower />
      </AppErrorBoundary>,
    );
    expect(screen.getByRole("heading", { name: "Something went wrong on our side" })).toBeVisible();
    expect(screen.getByRole("button", { name: "Try again" })).toBeInTheDocument();
  });
});
