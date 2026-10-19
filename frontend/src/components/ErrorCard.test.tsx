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
import { AccessDeniedCard, ErrorCard, NotFoundCard, RouteErrorCard } from "./ErrorCard";

afterEach(() => {
  vi.restoreAllMocks();
});

function receiptCard(props: { mark?: true }) {
  return (
    <ErrorCard code="Paid" title="Payment received" body="Saved." moneyLead="$10 paid." {...props}>
      <span />
    </ErrorCard>
  );
}

describe("ErrorCard", () => {
  // The mark is decorative (alt=""), which hides it from role queries.
  it("draws the brand mark only when asked", () => {
    const { container, rerender } = render(receiptCard({ mark: true }));
    expect(container.querySelector("img")).toBeInTheDocument();
    rerender(receiptCard({}));
    expect(container.querySelector("img")).toBeNull();
  });
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

describe("AccessDeniedCard", () => {
  it("renders the §4.5 403 card with the Errors artboard copy", async () => {
    const rootRoute = createRootRoute({ component: () => <AccessDeniedCard /> });
    const router = createRouter({
      routeTree: rootRoute,
      history: createMemoryHistory({ initialEntries: ["/admin"] }),
    });
    render(<RouterProvider router={router} />);
    expect(
      await screen.findByRole("heading", { name: "You don’t have access to the admin console" }),
    ).toBeVisible();
    expect(screen.getByText("403")).toBeInTheDocument();
    expect(
      screen.getByText(
        "The admin console is for the team that publishes listings. Your BlocBuy account works normally everywhere else.",
      ),
    ).toBeVisible();
    expect(screen.getByText("Nothing has been charged.")).toBeInTheDocument();
    expect(
      screen.getByText("Your blocs, payments and refunds are unaffected by this."),
    ).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Browse listings" })).toHaveAttribute("href", "/");
    expect(screen.getByRole("link", { name: "My blocs" })).toHaveAttribute("href", "/account");
    expect(screen.getByRole("heading", { name: "Should you have access?" })).toBeVisible();
    expect(
      screen.getByText(
        "Admin is assigned by hand, not requested in the app. Ask the team to add your account.",
      ),
    ).toBeVisible();
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
    expect(screen.getByRole("img", { name: "BlocBuy" }).closest("a")).toHaveAttribute("href", "/");
  });
});
