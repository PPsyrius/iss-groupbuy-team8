import { createFileRoute, Outlet, redirect } from "@tanstack/react-router";

// Pathless guard layout: anonymous visitors go to the sign-in screen with the path they wanted,
// which the callback route brings them back to. This is UX only; the backend enforces auth on
// every protected endpoint (ADR 0002). Only `beforeLoad` guards redirect, never the interceptor.
export const Route = createFileRoute("/_authenticated")({
  beforeLoad: ({ context, location }) => {
    if (context.session.status !== "authenticated") {
      throw redirect({ to: "/login", search: { returnTo: location.href } });
    }
  },
  component: Outlet,
});
