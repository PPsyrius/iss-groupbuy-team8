import { Avatar, Toast } from "@heroui/react";
import { type ErrorComponentProps, Link, Outlet } from "@tanstack/react-router";

import { ApiError } from "@/api/envelope";
import { AccessDeniedCard, RouteErrorCard } from "@/components/ErrorCard";
import { initials } from "@/features/session/initials";
import { useSession } from "@/features/session/use-session";

import { AdminAccessDenied } from "./admin-guard";
import isologo from "./isologo.png";

// The console's own shell (admin plan A1-03, artboard 4a): a 240px rail on `background-inverse`
// carrying the isologo, so the two shells are never confused, and the storefront header is
// hidden through `staticData.shell`. Desktop only, dense: the content column keeps 1024px and
// scrolls sideways below that (Q12). The isologo is the artboard's PNG until the SVG lands
// (CR-7). Payments and Bloc price rules stay out of the nav until their screens exist (Q3,
// A3-06).
const nav = "flex min-h-11 items-center rounded-lg px-3 text-sm";

export function AdminShell() {
  return (
    <div className="bg-background flex min-h-dvh">
      <aside className="bg-background-inverse text-background flex w-60 shrink-0 flex-col gap-6 px-3 py-5">
        <div className="flex items-center gap-2.5 px-2">
          <img src={isologo} alt="BlocBuy" className="h-8 w-auto" />
          <span className="font-mono text-xs text-[#A0A0A0]">ADMIN</span>
        </div>
        <nav aria-label="Admin" className="flex flex-col gap-0.5">
          <Link
            to="/admin"
            activeOptions={{ exact: true }}
            className={nav + " hover:text-background"}
            activeProps={{ className: "bg-white/10 font-semibold text-background" }}
            inactiveProps={{ className: "text-[#A0A0A0]" }}
          >
            Dashboard
          </Link>
          <Link
            to="/admin/listings"
            className={nav + " hover:text-background"}
            activeProps={{ className: "bg-white/10 font-semibold text-background" }}
            inactiveProps={{ className: "text-[#A0A0A0]" }}
          >
            Listings
          </Link>
        </nav>
        <AdminUser />
      </aside>
      <main className="min-w-[1024px] flex-1 p-6 text-sm">
        <Outlet />
      </main>
      {/* The console's toasts ("Draft deleted", A1-07); the storefront has none yet. */}
      <Toast.Provider placement="bottom end" />
    </div>
  );
}

function AdminUser() {
  const session = useSession();
  if (session.status !== "authenticated") return null;
  // B4 (#97): nickname is nullable; the stable userId is the fallback. "Admin" is a fixed label:
  // the probe proved the role, and `me` cannot name it (CR-9).
  const name = session.nickname ?? session.userId;
  return (
    <div className="mt-auto flex items-center gap-2.5 rounded-lg bg-[#232323] p-3">
      <Avatar size="sm" aria-hidden="true">
        <Avatar.Fallback>{initials(name)}</Avatar.Fallback>
      </Avatar>
      <div className="flex min-w-0 flex-col text-xs">
        <span className="text-background truncate font-semibold">{name}</span>
        <span className="text-[#A0A0A0]">Admin</span>
      </div>
    </div>
  );
}

// The layout's error boundary: a 403 from the probe, or from any admin call later (a role
// revoked mid-session), is the Access denied card; anything else is the global error card.
// Both sit under a wordmark-only bar, since the storefront header is hidden on this subtree.
export function AdminRouteError(props: ErrorComponentProps) {
  const denied =
    props.error instanceof AdminAccessDenied ||
    (props.error instanceof ApiError && props.error.status === 403);
  return (
    <>
      <header className="bg-surface border-border flex h-16 items-center border-b px-8">
        <Link to="/" className="text-xl font-bold tracking-tight">
          BlocBuy
        </Link>
      </header>
      {denied ? <AccessDeniedCard /> : <RouteErrorCard {...props} />}
    </>
  );
}
