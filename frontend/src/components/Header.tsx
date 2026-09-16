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
        <span data-testid="login-id">{me.data.loginId}</span>
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
