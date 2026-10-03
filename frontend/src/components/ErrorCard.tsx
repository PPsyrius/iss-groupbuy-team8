import { buttonVariants, Chip } from "@heroui/react";
import { type ErrorComponentProps, Link, useRouter } from "@tanstack/react-router";
import type { ReactNode } from "react";

// The one terminal-screen card of docs/design-screens.md §4.5 (Q11): code chip, heading, body,
// separator, money line, actions, optional hint. Phone: full-width card, padding 24, stacked
// actions; desktop: centred 560px, padding 40, actions side by side. No accent, no illustration.
// The payment receipt and return pages reuse it (§4.6, Q23/Q26 A): the chip carries the payment
// status, `references` holds the §4.4 refund-statement rows, and `live` announces a polled change.
const card = "border-border bg-surface rounded-lg border";

export const primaryAction =
  buttonVariants({ variant: "secondary" }) + " min-h-12 w-full text-base xl:w-auto xl:px-6";
export const secondaryAction =
  buttonVariants({ variant: "outline" }) + " min-h-11 w-full xl:w-auto xl:px-6";

interface ErrorCardProps {
  code: string;
  color?: "default" | "success" | "danger";
  title: string;
  body: string;
  alert?: boolean;
  live?: boolean;
  moneyLead: string;
  moneyDetail?: string | undefined;
  references?: { label: string; value: string }[];
  children: ReactNode;
  hint?: { title: string; body: string };
}

export function ErrorCard({
  code,
  color = "default",
  title,
  body,
  alert = false,
  live = false,
  moneyLead,
  moneyDetail,
  references,
  children,
  hint,
}: Readonly<ErrorCardProps>) {
  return (
    <main className="mx-auto flex max-w-[1280px] justify-center p-6 xl:py-16">
      <title>{`${title} · BlocBuy`}</title>
      <div className={card + " flex w-full max-w-[560px] flex-col gap-3 p-6 xl:p-10"}>
        {/* One live region for everything a poll can change: a refund moving to review keeps the
            title, so announcing the heading alone would miss it. An error is announced by the
            body's alert instead, so the status role steps aside rather than wrap it. */}
        <div className="flex flex-col gap-3" role={live && !alert ? "status" : undefined}>
          <Chip color={color} variant="soft" size="sm" className="self-start font-mono">
            {code}
          </Chip>
          <h1 className="text-2xl font-semibold tracking-tight xl:text-3xl">{title}</h1>
          <p className="text-base text-[#52525B]" role={alert ? "alert" : undefined}>
            {body}
          </p>
          <hr className="border-separator" />
          <p className="text-sm">
            <span className="text-success-soft-foreground font-semibold">{moneyLead}</span>
            {moneyDetail ? (
              <>
                {" "}
                <span className="text-muted">{moneyDetail}</span>
              </>
            ) : null}
          </p>
        </div>
        {references && references.length > 0 ? (
          <dl className="grid grid-cols-[auto_1fr] gap-x-4 gap-y-1 text-sm">
            {references.map(({ label, value }) => (
              <div key={label} className="contents">
                <dt className="text-muted">{label}</dt>
                <dd className="font-mono text-xs break-all">{value}</dd>
              </div>
            ))}
          </dl>
        ) : null}
        <div className="mt-1 flex flex-col gap-2 xl:flex-row xl:flex-wrap">{children}</div>
        {hint ? (
          <>
            <hr className="border-separator" />
            <div className="flex flex-col gap-1">
              <h2 className="text-sm font-semibold">{hint.title}</h2>
              <p className="text-muted text-sm">{hint.body}</p>
            </div>
          </>
        ) : null}
      </div>
    </main>
  );
}

// §4.5 "404 Not found". The artboard's second action, "My blocs", waits for a route that works
// signed out (plan §12 note 41(e)); until then the single action is "Browse listings".
export function NotFoundCard() {
  return (
    <ErrorCard
      code="404"
      title="We can’t find that page"
      body="The link may be old, or the listing may have been removed. Links pasted from chat sometimes lose a character at the end."
      moneyLead="Nothing has been charged."
      moneyDetail="Any bloc you have already paid into is safe and still listed under My blocs."
      hint={{
        title: "Came from an invite link?",
        body: "Ask the person who shared it to send it again — invite links expire when the bloc closes.",
      }}
    >
      <Link to="/" className={primaryAction}>
        Browse listings
      </Link>
    </ErrorCard>
  );
}

// §4.5 "403 Access denied" (Errors artboard, copy verbatim): a signed-in account without the
// admin role opens /admin. Admin is assigned by hand, so the copy offers no request flow.
export function AccessDeniedCard() {
  return (
    <ErrorCard
      code="403"
      title="You don’t have access to the admin console"
      body="The admin console is for the team that publishes listings. Your BlocBuy account works normally everywhere else."
      moneyLead="Nothing has been charged."
      moneyDetail="Your blocs, payments and refunds are unaffected by this."
      hint={{
        title: "Should you have access?",
        body: "Admin is assigned by hand, not requested in the app. Ask the team to add your account.",
      }}
    >
      <Link to="/" className={primaryAction}>
        Browse listings
      </Link>
      <Link to="/account" className={secondaryAction}>
        My blocs
      </Link>
    </ErrorCard>
  );
}

// §4.5 "500 Global error"."Browse listings" is a full navigation on purpose: after a crash the
// safest catalogue is a fresh one, and this card also renders outside the router (the boundary
// in main.tsx), where `Link` is unavailable.
export function GlobalErrorCard({ onRetry }: Readonly<{ onRetry: () => void }>) {
  return (
    <ErrorCard
      code="500"
      color="danger"
      title="Something went wrong on our side"
      body="This isn’t you. The page failed to load and we’ve logged it. Trying again usually works."
      moneyLead="No payment was taken."
      moneyDetail="If you were part-way through paying, check My blocs before trying again — a seat is only held once a payment is confirmed."
    >
      <button type="button" className={primaryAction} onClick={onRetry}>
        Try again
      </button>
      <a href="/" className={secondaryAction}>
        Browse listings
      </a>
    </ErrorCard>
  );
}

// Router `errorComponent` / `defaultErrorComponent`: "Try again" re-runs the failed loaders and
// clears the route's error boundary (TanStack `reset`).
export function RouteErrorCard({ reset }: ErrorComponentProps) {
  const router = useRouter();
  return (
    <GlobalErrorCard
      onRetry={() => {
        void router.invalidate();
        reset();
      }}
    />
  );
}
