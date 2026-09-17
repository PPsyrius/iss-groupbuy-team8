import { buttonVariants, Link as HeroLink } from "@heroui/react";
import { useQuery } from "@tanstack/react-query";
import { Link } from "@tanstack/react-router";

import { unwrapResult } from "@/api/envelope";
import { meOptions } from "@/api/generated/@tanstack/react-query.gen";

export function Header() {
  const me = useQuery({ ...meOptions(), select: (r) => unwrapResult(r), retry: false });
  return (
    <header className="flex items-center justify-between border-b px-6 py-3">
      <Link to="/" className="text-lg font-semibold">
        BlocBuy
      </Link>
      {me.data ? (
        // B4 (#97): `nickname` and `avatarUrl` are nullable for accounts registered before the
        // Google profile was wired; fall back to the stable `userId` rather than render nothing.
        <span data-testid="session-user">{me.data.nickname ?? me.data.userId}</span>
      ) : (
        <HeroLink
          href="/api/v1/auth/oauth2/google"
          className={buttonVariants({ variant: "primary" })}
        >
          Log in
        </HeroLink>
      )}
    </header>
  );
}
