import { Alert, buttonVariants } from "@heroui/react";
import { createFileRoute, Link, redirect, useNavigate } from "@tanstack/react-router";

import { GoogleSignInButton } from "@/features/session/GoogleSignInButton";
import { loginSearchSchema } from "@/features/session/login-search";

// The artboards' sign-in screen (`BlocBuy Buyer Sign In.dc.html`, docs/design-screens.md §4.3):
// Google only, reached on the way to a paid action (`returnTo`) or from the backend's failure
// landing (`?error={errorCode}`, contracts/openapi.json googleAuthorize / googleCallback).
export const Route = createFileRoute("/login")({
  validateSearch: loginSearchSchema,
  beforeLoad: ({ context, search }) => {
    // Already signed in and nothing to explain: straight back to where they were going.
    if (context.session.status === "authenticated" && !search.error) {
      throw redirect({ href: search.returnTo, replace: true });
    }
  },
  component: LoginPage,
});

const card =
  "bg-surface flex flex-col gap-6 xl:w-[480px] xl:rounded-lg xl:border xl:border-border xl:p-10 xl:shadow-[0_2px_4px_0_rgba(0,0,0,0.04),0_1px_2px_0_rgba(0,0,0,0.06)]";

function LoginPage() {
  const { returnTo, error } = Route.useSearch();
  const fromListing = returnTo.startsWith("/products/");
  const backLabel = fromListing ? "Back to the listing" : "Back to browse";
  return (
    <main className="mx-auto flex max-w-[1280px] flex-col gap-6 px-6 py-8 xl:items-center xl:px-8 xl:py-16">
      {/* The error state carries its own exit beside the retry, so the crumb steps aside. */}
      {error ? null : (
        <nav className="text-sm font-medium xl:self-end">
          <BackLink returnTo={returnTo} fromListing={fromListing} label={backLabel} />
        </nav>
      )}
      <div className={card}>
        <span className="text-[26px] leading-none font-bold tracking-tight">BlocBuy</span>
        {error ? (
          <ErrorBody returnTo={returnTo} backLabel={backLabel} />
        ) : (
          <DefaultBody returnTo={returnTo} fromListing={fromListing} />
        )}
      </div>
    </main>
  );
}

// "Back to the listing" returns to the parked listing path (a same-origin path by construction of
// `loginSearchSchema`); it is a prebuilt href rather than a typed `to`, so the router navigates
// it from the click handler. Everything else goes back to browsing.
function BackLink({
  returnTo,
  fromListing,
  label,
  className,
}: Readonly<{
  returnTo: string;
  fromListing: boolean;
  label: string;
  className?: string;
}>) {
  const navigate = useNavigate();
  if (!fromListing) {
    return (
      <Link to="/" className={className}>
        {label}
      </Link>
    );
  }
  return (
    <a
      href={returnTo}
      className={className}
      onClick={(event) => {
        event.preventDefault();
        void navigate({ href: returnTo });
      }}
    >
      {label}
    </a>
  );
}

function DefaultBody({
  returnTo,
  fromListing,
}: Readonly<{ returnTo: string; fromListing: boolean }>) {
  return (
    <>
      <div className="flex flex-col gap-3">
        <h1 className="text-3xl font-semibold tracking-tight">
          {fromListing ? "Sign in to hold your seat" : "Sign in to BlocBuy"}
        </h1>
        <p className="text-base text-zinc-600">
          {fromListing
            ? "Nothing is charged until you confirm payment on the next step, and we’ll bring you straight back to this bloc."
            : "You only need an account to hold a seat in a bloc. Browsing stays open to everyone."}
        </p>
      </div>
      <div className="flex flex-col gap-3">
        <GoogleSignInButton returnTo={returnTo} />
        <p className="text-sm text-zinc-600">
          We use your Google account only to identify you and to keep your blocs together. We never
          post anything. Sessions last 24 hours.
        </p>
      </div>
      <section className="bg-surface-secondary mt-auto flex flex-col gap-1 rounded-lg p-4">
        <h2 className="text-sm font-semibold">Why we ask</h2>
        <p className="text-muted text-sm">
          A seat in a bloc is paid, and a refund has to go back to a person. Signing in is how we
          know whose seat it is.
        </p>
      </section>
    </>
  );
}

function ErrorBody({ returnTo, backLabel }: Readonly<{ returnTo: string; backLabel: string }>) {
  const fromListing = returnTo.startsWith("/products/");
  return (
    <>
      {/* The artboard leads with the alert, not a title; the page still needs its one h1. */}
      <h1 className="sr-only">Sign in to BlocBuy</h1>
      <Alert status="danger">
        <Alert.Indicator />
        <Alert.Content>
          <Alert.Title>Google couldn’t sign you in</Alert.Title>
          <Alert.Description>
            The sign-in was cancelled or timed out. Nothing has been charged and no seat was held.
          </Alert.Description>
        </Alert.Content>
      </Alert>
      <div className="flex flex-col gap-2 xl:flex-row">
        <GoogleSignInButton
          returnTo={returnTo}
          label="Try again with Google"
          className="xl:flex-1"
        />
        <BackLink
          returnTo={returnTo}
          fromListing={fromListing}
          label={backLabel}
          className={buttonVariants({ variant: "ghost" }) + " min-h-11 justify-center text-base"}
        />
      </div>
      <section className="bg-surface-secondary flex flex-col gap-1 rounded-lg p-4">
        <h2 className="text-sm font-semibold">Still stuck?</h2>
        <p className="text-muted text-sm">
          Check that pop-ups are allowed for this site, then try again. There is no other way to
          sign in — BlocBuy has no password.
        </p>
      </section>
    </>
  );
}
