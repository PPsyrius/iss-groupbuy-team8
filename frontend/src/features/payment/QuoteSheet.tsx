import { Alert, Button, Modal, Skeleton } from "@heroui/react";

import { formatCountdown } from "@/features/catalog/deal-time";
import { formatPrice } from "@/features/catalog/Price";
import type { QuoteState } from "@/features/payment/use-quote";

// The first-team quote step (UC3, plan T3-01): "Start a bloc" opens this sheet over the listing,
// the same `placement="auto"` sheet/modal as the sign-in gate (§4.3). It shows the server's
// quoted price with its validity countdown and the refund promise (design brief §6 "Checkout");
// an expired quote disables payment and offers a fresh price. START sends the quote id and the
// server charges the quoted amount while the quote is valid (US26, #183; ADR 0014 addendum
// 2026-09-29). A quote the server refused at START (`isRejected`) reads like an expired one.
export function QuoteSheet({
  isOpen,
  onOpenChange,
  state,
  now,
  isPaying,
  isRejected = false,
  onContinue,
  onRefresh,
  onRetry,
}: Readonly<{
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  state: QuoteState;
  now: number;
  isPaying: boolean;
  isRejected?: boolean;
  onContinue: (quoteId: string) => void;
  onRefresh: () => void;
  onRetry: () => void;
}>) {
  const quote = state.status === "ready" ? state.quote : null;
  const expired = quote !== null && quote.expiresMs <= now;
  const stale = quote !== null && (expired || isRejected);
  return (
    <Modal.Backdrop isOpen={isOpen} onOpenChange={onOpenChange} isDismissable>
      <Modal.Container placement="auto" size="md">
        <Modal.Dialog aria-labelledby="quote-heading" className="gap-4 p-5 xl:p-8">
          <Modal.Header className="flex flex-col gap-2">
            <Modal.Heading id="quote-heading" className="text-xl font-semibold">
              Your bloc price
            </Modal.Heading>
          </Modal.Header>
          <div className="flex flex-col gap-3" aria-busy={state.status === "loading"}>
            <QuoteStatus status={state.status} />
            {quote ? <QuotePrice quote={quote} now={now} expired={expired} stale={stale} /> : null}
          </div>
          <Modal.Footer className="flex flex-col gap-2 xl:flex-row-reverse">
            {quote && !stale ? (
              <Button
                variant="secondary"
                size="lg"
                className="min-h-12 w-full text-lg xl:flex-1"
                isPending={isPaying}
                // `now` is floored to the second and ticks once a second, so the button can
                // outlive the quote by up to two seconds; the press re-reads the clock and
                // leaves the expired state to the next tick (CodeRabbit r4104866984).
                onPress={() => {
                  if (quote.expiresMs > Date.now()) onContinue(quote.id);
                }}
              >
                Continue to payment
              </Button>
            ) : null}
            {stale ? (
              <Button
                variant="secondary"
                size="lg"
                className="min-h-12 w-full text-lg xl:flex-1"
                // A quote can expire while its checkout is still opening; a new price then would
                // be closed by that checkout's outcome, so it waits (CodeRabbit, PR #269).
                isDisabled={isPaying}
                onPress={onRefresh}
              >
                Get a new price
              </Button>
            ) : null}
            {state.status === "error" ? (
              <Button
                variant="outline"
                className="min-h-11 w-full text-base xl:flex-1"
                onPress={onRetry}
              >
                Try again
              </Button>
            ) : null}
            <Button
              variant="ghost"
              className="min-h-11 w-full text-base font-medium xl:w-auto"
              onPress={() => onOpenChange(false)}
            >
              {state.status === "unavailable" ? "Close" : "Not now"}
            </Button>
          </Modal.Footer>
        </Modal.Dialog>
      </Modal.Container>
    </Modal.Backdrop>
  );
}

// Everything the sheet says before (or instead of) a price: loading, no policy, or a failure.
function QuoteStatus({ status }: Readonly<{ status: QuoteState["status"] }>) {
  if (status === "idle" || status === "loading") {
    return (
      <>
        <p className="text-muted text-sm">Getting your bloc price…</p>
        <Skeleton className="h-10 w-[46%] rounded-full" />
        <Skeleton className="h-[68px] rounded-lg" />
      </>
    );
  }
  if (status === "unavailable") {
    return (
      <p className="text-muted text-sm">
        No bloc price is set for this listing yet, so a bloc can’t be started right now. Nothing has
        been charged.
      </p>
    );
  }
  if (status === "error") {
    return (
      <Alert status="danger">
        <Alert.Content>
          <Alert.Title>We couldn’t get a price</Alert.Title>
          <Alert.Description>
            Something went wrong on our side. Nothing has been charged.
          </Alert.Description>
        </Alert.Content>
      </Alert>
    );
  }
  return null;
}

type Quote = Extract<QuoteState, { status: "ready" }>["quote"];

function QuotePrice({
  quote,
  now,
  expired,
  stale,
}: Readonly<{ quote: Quote; now: number; expired: boolean; stale: boolean }>) {
  const price = formatPrice(quote.amount, quote.currency);
  return (
    <>
      <p className="text-accent-soft-foreground text-4xl font-semibold tracking-tight tabular-nums">
        {price}
      </p>
      <QuoteTerms payNow={price} validFor={formatCountdown(quote.expiresMs, now)} />
      {stale ? (
        <Alert status="warning" aria-live="polite">
          <Alert.Content>
            <Alert.Title>
              {expired ? "This price has expired" : "This price is no longer available"}
            </Alert.Title>
            <Alert.Description>
              Get a new price to continue. Nothing has been charged.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : (
        <p className="text-muted text-sm">
          This is the price you pay if you continue before it expires. Nothing is charged until you
          confirm on Stripe.
        </p>
      )}
    </>
  );
}

// Same three cells as the listing's trust triptych (§4.2), with the quote's validity countdown in
// the middle.
// `role="timer"` keeps the per-second tick out of screen-reader announcements.
function QuoteTerms({
  payNow,
  validFor,
}: Readonly<{ payNow: string; validFor: { text: string; urgent: boolean } }>) {
  const cell = "flex flex-col gap-0.5 rounded-lg border-border border p-3";
  return (
    <dl className="grid grid-cols-3 gap-2">
      <div className={cell}>
        <dt className="text-muted text-xs">You pay now</dt>
        <dd className="text-accent-soft-foreground text-sm font-semibold tabular-nums">{payNow}</dd>
      </div>
      <div className={cell}>
        <dt className="text-muted text-xs">Quote valid for</dt>
        <dd role="timer" className="text-sm font-semibold tabular-nums">
          {validFor.text}
        </dd>
      </div>
      <div className={cell}>
        <dt className="text-muted text-xs">If it doesn’t fill</dt>
        <dd className="text-success-soft-foreground text-sm font-semibold">Refunded in full</dd>
      </div>
    </dl>
  );
}
