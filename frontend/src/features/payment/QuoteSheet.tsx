import { Alert, Button, Modal, Skeleton } from "@heroui/react";

import { formatCountdown } from "@/features/catalog/deal-time";
import { formatPrice } from "@/features/catalog/Price";
import type { QuoteState } from "@/features/payment/use-quote";

// The first-team quote step (UC3, plan T3-01): "Start a bloc" opens this sheet over the listing,
// the same `placement="auto"` sheet/modal as the sign-in gate (§4.3). It shows the server's
// quoted price with its hold countdown and the refund promise (design brief §6 "Checkout"); an
// expired quote disables payment and offers a fresh price. The quote is a preview only: START
// re-prices from the latest policy and Stripe's page carries the charged amount (ADR 0014).
export function QuoteSheet({
  isOpen,
  onOpenChange,
  state,
  now,
  isPaying,
  onContinue,
  onRefresh,
  onRetry,
}: {
  isOpen: boolean;
  onOpenChange: (open: boolean) => void;
  state: QuoteState;
  now: number;
  isPaying: boolean;
  onContinue: () => void;
  onRefresh: () => void;
  onRetry: () => void;
}) {
  const quote = state.status === "ready" ? state.quote : null;
  const expired = quote !== null && quote.expiresMs <= now;
  return (
    <Modal isOpen={isOpen} onOpenChange={onOpenChange}>
      <Modal.Backdrop isDismissable>
        <Modal.Container placement="auto" size="md">
          <Modal.Dialog aria-labelledby="quote-heading" className="gap-4 p-5 xl:p-8">
            <Modal.Header className="flex flex-col gap-2">
              <Modal.Heading id="quote-heading" className="text-xl font-semibold">
                Your bloc price
              </Modal.Heading>
            </Modal.Header>
            <div className="flex flex-col gap-3" aria-busy={state.status === "loading"}>
              {state.status === "idle" || state.status === "loading" ? (
                <>
                  <p className="text-muted text-sm">Getting your bloc price…</p>
                  <Skeleton className="h-10 w-[46%] rounded-full" />
                  <Skeleton className="h-[68px] rounded-lg" />
                </>
              ) : null}
              {state.status === "unavailable" ? (
                <p className="text-muted text-sm">
                  No bloc price is set for this listing yet, so a bloc can’t be started right now.
                  Nothing has been charged.
                </p>
              ) : null}
              {state.status === "error" ? (
                <Alert status="danger">
                  <Alert.Content>
                    <Alert.Title>We couldn’t get a price</Alert.Title>
                    <Alert.Description>
                      Something went wrong on our side. Nothing has been charged.
                    </Alert.Description>
                  </Alert.Content>
                </Alert>
              ) : null}
              {quote ? (
                <>
                  <p className="text-accent-soft-foreground text-4xl font-semibold tracking-tight tabular-nums">
                    {formatPrice(quote.amount, quote.currency)}
                  </p>
                  <QuoteTerms
                    payNow={formatPrice(quote.amount, quote.currency)}
                    held={formatCountdown(quote.expiresMs, now)}
                  />
                  {expired ? (
                    <Alert status="warning" aria-live="polite">
                      <Alert.Content>
                        <Alert.Title>This price has expired</Alert.Title>
                        <Alert.Description>
                          Get a new price to continue. Nothing has been charged.
                        </Alert.Description>
                      </Alert.Content>
                    </Alert>
                  ) : (
                    <p className="text-muted text-sm">
                      Prices are held for up to 15 minutes. Stripe shows the final amount before you
                      pay, and nothing is charged until you confirm there.
                    </p>
                  )}
                </>
              ) : null}
            </div>
            <Modal.Footer className="flex flex-col gap-2 xl:flex-row-reverse">
              {quote && !expired ? (
                <Button
                  variant="secondary"
                  size="lg"
                  className="min-h-12 w-full text-lg xl:flex-1"
                  isPending={isPaying}
                  onPress={onContinue}
                >
                  Continue to payment
                </Button>
              ) : null}
              {expired ? (
                <Button
                  variant="secondary"
                  size="lg"
                  className="min-h-12 w-full text-lg xl:flex-1"
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
    </Modal>
  );
}

// Same three cells as the listing's trust triptych (§4.2), with the hold countdown in the middle.
// `role="timer"` keeps the per-second tick out of screen-reader announcements.
function QuoteTerms({ payNow, held }: { payNow: string; held: { text: string; urgent: boolean } }) {
  const cell = "flex flex-col gap-0.5 rounded-lg border-border border p-3";
  return (
    <dl className="grid grid-cols-3 gap-2">
      <div className={cell}>
        <dt className="text-muted text-xs">You pay now</dt>
        <dd className="text-accent-soft-foreground text-sm font-semibold tabular-nums">{payNow}</dd>
      </div>
      <div className={cell}>
        <dt className="text-muted text-xs">Price held for</dt>
        <dd role="timer" className="text-sm font-semibold tabular-nums">
          {held.text}
        </dd>
      </div>
      <div className={cell}>
        <dt className="text-muted text-xs">If it doesn’t fill</dt>
        <dd className="text-success-soft-foreground text-sm font-semibold">Refunded in full</dd>
      </div>
    </dl>
  );
}
