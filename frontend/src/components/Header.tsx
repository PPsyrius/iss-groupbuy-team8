import { Avatar, Button, buttonVariants } from "@heroui/react";
import { Link, useLocation } from "@tanstack/react-router";

import { initials } from "@/features/session/initials";
import { useLogout } from "@/features/session/use-logout";
import { useSession } from "@/features/session/use-session";

// Storefront header per the artboards (docs/design-screens.md §3): 56px on phone, 64px at 1280,
// `surface` with a hairline border; wordmark left (text until the SVG asset lands, CR-7); right,
// either the neutral "Sign in" control (`Button variant="secondary"` look, accent-soft-foreground
// label, no accent fill: CR-8) or the avatar + name once signed in. No logout control is drawn
// on any artboard; T2-05 adds it in the signed-in slot.
export function Header() {
  const session = useSession();
  const href = useLocation({ select: (l) => l.href });
  const onLoginPage = useLocation({ select: (l) => l.pathname === "/login" });
  return (
    <header className="bg-surface border-border flex h-14 items-center justify-between gap-4 border-b px-4 xl:h-16 xl:px-8">
      <Link to="/" className="text-lg font-bold tracking-tight xl:text-xl">
        BlocBuy
      </Link>
      <div className="flex items-center gap-4">
        <span className="text-muted hidden text-sm xl:inline">
          Bringing people together to buy better
        </span>
        {session.status === "authenticated" && (
          <SignedIn name={session.nickname ?? session.userId} avatarUrl={session.avatarUrl} />
        )}
        {session.status !== "authenticated" && !onLoginPage && (
          <Link
            to="/login"
            search={{ returnTo: href }}
            className={
              buttonVariants({ variant: "secondary" }) +
              " text-accent-soft-foreground min-h-11 px-3 text-sm font-semibold xl:px-[18px] xl:text-base"
            }
          >
            Sign in
          </Link>
        )}
      </div>
    </header>
  );
}

function SignedIn({ name, avatarUrl }: { name: string; avatarUrl: string | null }) {
  const logout = useLogout();
  return (
    // No artboard draws a signed-in storefront header or a logout control (Q16). Until design
    // rules, the avatar + name link to /account (the My blocs screen) and a neutral ghost
    // "Sign out" button sits beside them: no accent, pairs with the "Sign in" wording.
    <div className="flex items-center gap-2">
      <Link to="/account" className="flex items-center gap-2">
        <Avatar size="sm" aria-hidden="true">
          {avatarUrl ? <Avatar.Image src={avatarUrl} alt="" /> : null}
          <Avatar.Fallback>{initials(name)}</Avatar.Fallback>
        </Avatar>
        {/* B4 (#97): nickname is nullable; the stable userId is the fallback, never nothing. */}
        <span data-testid="session-user" className="text-sm font-medium">
          {name}
        </span>
      </Link>
      {logout.isError ? (
        <span role="alert" className="text-danger text-sm">
          Could not sign out. Try again.
        </span>
      ) : null}
      <Button
        variant="ghost"
        size="sm"
        className="text-muted min-h-11 text-sm"
        isPending={logout.isPending}
        onPress={() => logout.mutate({})}
      >
        Sign out
      </Button>
    </div>
  );
}
