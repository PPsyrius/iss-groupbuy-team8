import { Button, Chip, Skeleton } from "@heroui/react";
import { useQuery, useQueryClient } from "@tanstack/react-query";
import { type ReactNode, useState } from "react";

import type { PolicyResponse } from "@/api/generated/types.gen";

import {
  adminListingOptions,
  adminPolicyOptions,
  adminPolicyVersionOptions,
  forgetListingPages,
  useConfigurePolicy,
} from "./admin-api";
import { isAdminDenied } from "./AdminShell";
import type { SubmitProblem } from "./ListingForm";
import { formatSgd } from "./money";
import {
  type PolicyState,
  policyRefusalOf,
  policyState,
  ruleChip,
  type RuleTiming,
  ruleTiming,
} from "./policy";
import { type PolicyFormValues, toPolicyRequest } from "./policy-form";
import { PolicyForm } from "./PolicyForm";
import { formatWindow } from "./sgt";
import { problemFor, useRouteBoundary } from "./use-listing-mutations";
import { useMinuteClock } from "./use-minute-clock";

// The 4b.5 body copy per state; the scheduled line has no artboard (§11 D3). A rule is configured
// only on sale (BA-11), so a draft and an off-sale listing say how to get there.
const bodies: Record<PolicyState, string> = {
  none: "Buyers see the solo price only, and the card shows a quiet “start a bloc” affordance.",
  active: "Quotes issued now lock this amount for 15 minutes each.",
  expired:
    "No bloc price is shown on the storefront until a new revision starts. Blocs already filled keep their locked price.",
  scheduled: "Buyers see no bloc price until this rule starts.",
  "unavailable-draft":
    "Add a rule after you publish. The bloc price must be strictly below the solo price.",
  "unavailable-off-sale": "Put the listing back on sale to add a revision.",
};

const FOOTNOTE = "Saving appends a revision. Prices already locked into a quote never change.";

// A refused revision the panel shows in place of the rule: a stale version (412) or a listing
// that left on sale meanwhile (409).
type Stop =
  | { kind: "conflict"; loaded: number; serverVersion: number | null; amount: string }
  | { kind: "unavailable" };

// The bloc price rule panel on the editor page (admin plan A2-01, artboards 4b.3 and 4b.5): the
// latest revision over the struck solo price, its window in Singapore time, and the one way to
// change it, a new revision. Writes are pessimistic: the saved revision becomes the panel's entry
// and the table pages are dropped so their rule line follows. A 412 shows the conflict state
// (only Reload resolves it, no merge), a 409 the unavailable state, and a 403 Access denied.
export function PricePolicyPanel({
  productId,
  status,
  originalPrice,
}: Readonly<{ productId: string; status: string | undefined; originalPrice: string }>) {
  const nowMs = useMinuteClock();
  const qc = useQueryClient();
  const toBoundary = useRouteBoundary();
  const configure = useConfigurePolicy();
  const isDraft = status?.toLowerCase() === "draft";
  const policy = useQuery({ ...adminPolicyOptions(productId), enabled: !isDraft });
  const [editing, setEditing] = useState(false);
  const [stop, setStop] = useState<Stop | null>(null);
  const [reloading, setReloading] = useState(false);
  const [reloadFailed, setReloadFailed] = useState(false);
  const askVersion = stop?.kind === "conflict" && stop.serverVersion === null;
  const serverRead = useQuery({ ...adminPolicyVersionOptions(productId), enabled: askVersion });
  // A refetch keeps the cached rule and does not throw, so a role revoked after load is rethrown
  // here to reach Access denied, as on the rest of the editor.
  if (isAdminDenied(policy.error)) throw policy.error;
  if (isAdminDenied(serverRead.error)) throw serverRead.error;

  const rule = policy.data ?? null;
  const state = policyState(rule, status, nowMs);
  const timing = ruleTiming(rule, nowMs);

  // Reload reads the rule again (and, after a 409, the listing, whose state changed) and leaves
  // the stop. A failed read keeps it and says so; a revoked role goes to Access denied.
  const reload = async () => {
    setReloading(true);
    setReloadFailed(false);
    try {
      if (stop?.kind === "unavailable")
        await qc.refetchQueries({ queryKey: adminListingOptions(productId).queryKey });
      await qc.fetchQuery({ ...adminPolicyOptions(productId), staleTime: 0, retry: false });
      qc.removeQueries({ queryKey: adminPolicyVersionOptions(productId).queryKey });
      setStop(null);
      setEditing(false);
    } catch (error) {
      if (isAdminDenied(error)) toBoundary(error);
      else setReloadFailed(true);
    } finally {
      setReloading(false);
    }
  };

  const save = async (values: PolicyFormValues): Promise<SubmitProblem | undefined> => {
    const loaded = rule?.version ?? 0;
    try {
      const saved = await configure.mutateAsync({
        productId,
        ...toPolicyRequest(values, loaded),
      });
      qc.setQueryData(adminPolicyOptions(productId).queryKey, saved);
      forgetListingPages(qc);
      setEditing(false);
      return undefined;
    } catch (error) {
      const refusal = policyRefusalOf(error);
      switch (refusal.kind) {
        case "conflict":
          setStop({
            kind: "conflict",
            loaded,
            serverVersion: refusal.serverVersion,
            amount: values.amount.trim(),
          });
          return {};
        case "unavailable":
          setStop({ kind: "unavailable" });
          return {};
        case "forbidden":
          toBoundary(error);
          return {};
        default:
          return problemFor(refusal, "The rule wasn’t saved");
      }
    }
  };

  const reloadProps = {
    onReload: () => void reload(),
    isReloading: reloading,
    failed: reloadFailed,
  };
  let body: ReactNode;
  let version = rule ? `v${rule.version ?? 0}` : "—";
  if (stop?.kind === "conflict") {
    const server = stop.serverVersion ?? serverRead.data;
    version = `v${stop.loaded} → ${server === undefined ? "…" : `v${server}`}`;
    body = (
      <ConflictBody
        amount={stop.amount}
        loaded={stop.loaded}
        server={server}
        originalPrice={originalPrice}
        {...reloadProps}
      />
    );
  } else if (stop?.kind === "unavailable") {
    body = (
      <>
        <p className="text-warning-soft-foreground">
          This listing changed state and can’t take a rule now. Reload to see what you can do.
        </p>
        <ReloadAction {...reloadProps} />
      </>
    );
  } else if (isDraft) {
    body = <RuleBody state={state} timing="none" rule={null} originalPrice={originalPrice} />;
  } else if (policy.isPending) {
    body = <Skeleton className="h-24 rounded-md" />;
  } else if (policy.isError) {
    body = (
      <>
        <p className="text-muted">We couldn’t load the bloc price rule.</p>
        <Button
          variant="outline"
          fullWidth
          className="min-h-11"
          onPress={() => void policy.refetch()}
        >
          Reload
        </Button>
      </>
    );
  } else {
    body = (
      <RuleBody
        state={state}
        timing={timing}
        rule={rule}
        originalPrice={originalPrice}
        onRevise={editing ? undefined : () => setEditing(true)}
      >
        {editing && !state.startsWith("unavailable") ? (
          <PolicyForm
            rule={rule}
            timing={timing}
            originalPrice={originalPrice}
            nowMs={nowMs}
            onSubmit={save}
            onCancel={() => setEditing(false)}
          />
        ) : null}
      </RuleBody>
    );
  }

  return (
    <section
      aria-labelledby="price-rule"
      className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5"
    >
      <div className="flex items-baseline justify-between">
        <h2 id="price-rule" className="font-semibold">
          Bloc price rule
        </h2>
        {isDraft ? null : <span className="text-muted font-mono text-xs">{version}</span>}
      </div>
      {body}
    </section>
  );
}

// The bloc price in accent only while buyers see it (active); otherwise muted.
function Price({
  amount,
  originalPrice,
  emphasis,
}: Readonly<{ amount: string; originalPrice: string; emphasis: boolean }>) {
  return (
    <div className="flex items-baseline gap-2.5">
      <span
        className={`text-2xl font-semibold tabular-nums ${emphasis ? "text-accent-soft-foreground" : "text-muted"}`}
      >
        {formatSgd(amount)}
      </span>
      <span className="text-muted text-sm tabular-nums line-through">
        {formatSgd(originalPrice)}
      </span>
    </div>
  );
}

function RuleBody({
  state,
  timing,
  rule,
  originalPrice,
  onRevise,
  children,
}: Readonly<{
  state: PolicyState;
  timing: RuleTiming;
  rule: PolicyResponse | null;
  originalPrice: string;
  onRevise?: (() => void) | undefined;
  children?: ReactNode;
}>) {
  const unavailable = state === "unavailable-draft" || state === "unavailable-off-sale";
  const window = rule ? formatWindow(rule.validFrom, rule.validUntil) : null;
  // The action shows unless the form is open; on a draft or off sale it stays, disabled, as 4b.1.
  const showAction = onRevise !== undefined || unavailable;
  return (
    <>
      {rule?.amount ? (
        <Price amount={rule.amount} originalPrice={originalPrice} emphasis={state === "active"} />
      ) : null}
      {state === "unavailable-draft" ? null : (
        <Chip
          size="sm"
          variant="soft"
          color={timing === "active" ? "success" : "default"}
          className="self-start"
        >
          {ruleChip(timing, rule)}
        </Chip>
      )}
      {window ? <span className="text-muted text-xs tabular-nums">{window}</span> : null}
      <p className="text-muted">{bodies[state]}</p>
      {showAction ? (
        <Button
          variant={state === "active" || state === "scheduled" ? "outline" : "secondary"}
          fullWidth
          className="min-h-11"
          isDisabled={unavailable}
          {...(onRevise ? { onPress: onRevise } : {})}
        >
          {rule ? "New revision" : "Add a rule"}
        </Button>
      ) : null}
      {state === "active" && onRevise !== undefined ? (
        <p className="text-muted text-xs">{FOOTNOTE}</p>
      ) : null}
      {children}
    </>
  );
}

// 4b.5's conflict state, in the editor banner's words: the amount that was not saved, both
// versions, and Reload as the one way out. Until a 412 without a version is answered by the
// background read, the line only says the server is newer.
function ConflictBody({
  amount,
  loaded,
  server,
  originalPrice,
  ...reload
}: Readonly<{
  amount: string;
  loaded: number;
  server: number | undefined;
  originalPrice: string;
  onReload: () => void;
  isReloading: boolean;
  failed: boolean;
}>) {
  const serverLine =
    server === undefined ? "the server has a newer version" : `the server now has v${server}`;
  return (
    <>
      <Price amount={amount} originalPrice={originalPrice} emphasis={false} />
      <Chip size="sm" variant="soft" color="warning" className="self-start">
        Not saved
      </Chip>
      <span className="text-muted text-xs tabular-nums">
        You loaded v{loaded}; {serverLine}
      </span>
      <p className="text-warning-soft-foreground">
        Someone else added a revision while this panel was open. Reload to see it — revisions
        append, so nothing you saved was lost.
      </p>
      <ReloadAction {...reload} />
    </>
  );
}

function ReloadAction({
  onReload,
  isReloading,
  failed,
}: Readonly<{ onReload: () => void; isReloading: boolean; failed: boolean }>) {
  return (
    <>
      {failed ? (
        <output className="text-danger-soft-foreground font-medium">
          We couldn’t reload the rule. Try again.
        </output>
      ) : null}
      <Button
        variant="outline"
        fullWidth
        className="min-h-11"
        onPress={onReload}
        isPending={isReloading}
      >
        Reload
      </Button>
    </>
  );
}
