import { Alert, Button, Chip, Description, FieldError, Label, NumberField } from "@heroui/react";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { useQueryClient } from "@tanstack/react-query";
import { Link, useLocation } from "@tanstack/react-router";
import { useEffect, useState } from "react";

import { OK_CODE } from "@/api/envelope";
import type { AdminListingResponse, TeamTermsView } from "@/api/generated/types.gen";

import { adminListingOptions, forgetListingPages, useConfigureTeamTerms } from "./admin-api";
import { messageOf } from "./field-message";
import type { SubmitProblem } from "./ListingForm";
import {
  type TeamTermsFormValues,
  termsChip,
  termsFormDefaults,
  termsRefusalOf,
  toTermsRequest,
} from "./team-terms";
import { teamTermsSchema } from "./team-terms.schema";
import { problemFor, useRouteBoundary } from "./use-listing-mutations";

type FieldName = keyof TeamTermsFormValues;
const fieldNames = new Set<string>(["seats", "hours", "minutes"]);
const isFieldName = (name: string): name is FieldName => fieldNames.has(name);

// No `minValue` or `maxValue`: React Aria would clamp a typed 1 to 2 on blur and save what the
// admin did not type; the schema names the bound instead.
const wholeNumber = { maximumFractionDigits: 0, useGrouping: false } as const;

// The group terms panel on the editor page (admin plan A3-04, §11 D6, decided 2026-10-05): a card
// under the bloc price rule panel, on every status, since terms are editable in every lifecycle
// state (#242). Seats per bloc and the bloc's lifetime, saved on their own through
// `configureTeamTerms`; it never sends `teamPrice`, which the rule panel owns.
//
// The terms are part of the listing: they come from the editor's listing read and the write
// carries and bumps the listing version. So a stale version is the editor's conflict (the banner,
// Reload as the one way out), and Save waits while the listing form has unsaved edits, because
// the saved version reloads that form. A listing save, in turn, leaves unsaved terms alone: the
// form remounts only when the saved terms change.
export function TeamTermsPanel({
  productId,
  version,
  terms,
  reloads,
  isDisabled,
  listingDirty,
  onConflict,
  onDirtyChange,
}: Readonly<{
  productId: string;
  version: number;
  terms: TeamTermsView | undefined;
  // The editor's Reload count: a Reload drops unsaved terms, as the banner says it drops edits.
  reloads: number;
  // A conflict elsewhere on the page closes every write until Reload.
  isDisabled: boolean;
  listingDirty: boolean;
  onConflict: (serverVersion: number | null) => void;
  // Unsaved edits, for the editor's unsaved-changes prompt.
  onDirtyChange?: ((dirty: boolean) => void) | undefined;
}>) {
  const qc = useQueryClient();
  const toBoundary = useRouteBoundary();
  const configure = useConfigureTeamTerms();
  const [saved, setSaved] = useState(false);
  const chip = termsChip(terms);

  const save = async (values: TeamTermsFormValues): Promise<SubmitProblem | undefined> => {
    setSaved(false);
    try {
      const result = await configure.mutateAsync({
        productId,
        ...toTermsRequest(values, version),
      });
      // Pessimistic: the answer's version and terms become the editor's listing, and the table
      // pages are dropped so their versions follow.
      qc.setQueryData(adminListingOptions(productId).queryKey, (cached) => {
        if (!cached?.data) return cached;
        const data: AdminListingResponse = {
          ...cached.data,
          version: result.version ?? version,
          ...(result.teamTerms ? { teamTerms: result.teamTerms } : {}),
        };
        return { ...cached, code: OK_CODE, data };
      });
      forgetListingPages(qc);
      setSaved(true);
      return undefined;
    } catch (error) {
      const refusal = termsRefusalOf(error);
      if (refusal.kind === "conflict") onConflict(refusal.serverVersion);
      if (refusal.kind === "forbidden") toBoundary(error);
      return problemFor(refusal, "The terms weren’t saved");
    }
  };

  return (
    <section
      aria-labelledby="group-terms"
      className="border-border bg-surface flex flex-col gap-3 rounded-lg border p-5"
    >
      <div className="flex items-baseline justify-between">
        <h2 id="group-terms" className="font-semibold">
          Group terms
        </h2>
        <Chip size="sm" variant="soft" color={chip.color}>
          {chip.label}
        </Chip>
      </div>
      <p className="text-muted">
        {terms?.configured
          ? "New blocs use these terms. Blocs already started keep theirs."
          : "Buyers can’t start a bloc on this listing until both are set."}
      </p>
      <TermsForm
        key={`${reloads}.${terms?.targetSize ?? ""}.${terms?.lifetimeSeconds ?? ""}`}
        terms={terms}
        isDisabled={isDisabled}
        listingDirty={listingDirty}
        saved={saved}
        onEdit={() => setSaved(false)}
        onSubmit={save}
        onDirtyChange={onDirtyChange}
      />
    </section>
  );
}

// The form itself, keyed by the saved terms and the editor's Reloads so a save or a Reload starts
// it clean. Validation first runs on submit, then on every change; a failed save's field
// messages stay on their fields until that field changes, as on the listing form.
function TermsForm({
  terms,
  isDisabled,
  listingDirty,
  saved,
  onEdit,
  onSubmit,
  onDirtyChange,
}: Readonly<{
  terms: TeamTermsView | undefined;
  isDisabled: boolean;
  listingDirty: boolean;
  saved: boolean;
  onEdit: () => void;
  onSubmit: (values: TeamTermsFormValues) => Promise<SubmitProblem | undefined>;
  onDirtyChange?: ((dirty: boolean) => void) | undefined;
}>) {
  const [alert, setAlert] = useState<SubmitProblem["alert"] & { signIn?: boolean }>();
  const { href } = useLocation();
  const form = useForm({
    defaultValues: termsFormDefaults(terms),
    validationLogic: revalidateLogic(),
    validators: { onDynamic: teamTermsSchema },
    listeners: {
      onChange: ({ formApi, fieldApi }) => {
        onEdit();
        const name = fieldApi.name;
        if (formApi.getFieldMeta(name)?.errorMap.onServer === undefined) return;
        formApi.setFieldMeta(name, (prev) => ({
          ...prev,
          errorMap: { ...prev.errorMap, onServer: undefined },
        }));
      },
    },
    onSubmit: async ({ value, formApi }) => {
      setAlert(undefined);
      const problem = await onSubmit(value);
      if (!problem) return;
      const unplaced: string[] = [];
      for (const [name, message] of Object.entries(problem.fields ?? {})) {
        if (isFieldName(name)) {
          formApi.setFieldMeta(name, (prev) => ({
            ...prev,
            errorMap: { ...prev.errorMap, onServer: message },
          }));
        } else {
          unplaced.push(message);
        }
      }
      if (problem.alert || unplaced.length > 0) {
        setAlert({
          title: problem.alert?.title ?? "Some fields need another look",
          description: [problem.alert?.description, ...unplaced].filter(Boolean).join(" "),
          ...(problem.signIn ? { signIn: true } : {}),
        });
      }
    },
  });
  const isSubmitting = useStore(form.store, (state) => state.isSubmitting);
  const isDirty = useStore(form.store, (state) => state.isDirty);
  useEffect(() => {
    onDirtyChange?.(isDirty);
  }, [isDirty, onDirtyChange]);
  useEffect(() => () => onDirtyChange?.(false), [onDirtyChange]);

  return (
    <form
      noValidate
      aria-label="Group terms"
      className="flex flex-col gap-4"
      onSubmit={(e) => {
        e.preventDefault();
        void form.handleSubmit();
      }}
    >
      {alert ? (
        <Alert status="danger" role="alert">
          <Alert.Content>
            <Alert.Title>{alert.title}</Alert.Title>
            {alert.description ? <Alert.Description>{alert.description}</Alert.Description> : null}
            {alert.signIn ? (
              <Link
                to="/login"
                search={{ returnTo: href }}
                className="font-medium underline underline-offset-4"
              >
                Sign in
              </Link>
            ) : null}
          </Alert.Content>
        </Alert>
      ) : null}

      <form.Field name="seats">
        {(field) => (
          <NumberField
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            formatOptions={wholeNumber}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Seats per bloc</Label>
            <NumberField.Group>
              <NumberField.Input className="tabular-nums" />
            </NumberField.Group>
            <Description>Paid members a bloc needs to complete. At least 2.</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </NumberField>
        )}
      </form.Field>

      <fieldset className="flex flex-col gap-1">
        <legend className="mb-1 text-sm font-medium">Bloc lifetime</legend>
        <div className="grid grid-cols-2 gap-3">
          <form.Field name="hours">
            {(field) => (
              <NumberField
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                formatOptions={wholeNumber}
                isInvalid={messageOf(field.state.meta.errors) !== undefined}
              >
                <Label>Hours</Label>
                <NumberField.Group>
                  <NumberField.Input className="tabular-nums" />
                </NumberField.Group>
                <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
              </NumberField>
            )}
          </form.Field>
          <form.Field name="minutes">
            {(field) => (
              <NumberField
                value={field.state.value}
                onChange={field.handleChange}
                onBlur={field.handleBlur}
                formatOptions={wholeNumber}
                isInvalid={messageOf(field.state.meta.errors) !== undefined}
              >
                <Label>Minutes</Label>
                <NumberField.Group>
                  <NumberField.Input className="tabular-nums" />
                </NumberField.Group>
                <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
              </NumberField>
            )}
          </form.Field>
        </div>
        <p className="text-muted text-xs">40 minutes to 24 hours, from the bloc’s start.</p>
      </fieldset>

      <Button
        type="submit"
        variant="secondary"
        fullWidth
        className="min-h-11"
        isDisabled={isDisabled || listingDirty || isSubmitting}
      >
        Save terms
      </Button>
      {listingDirty && !isDisabled ? (
        <p className="text-muted text-xs">Save your listing changes first.</p>
      ) : null}
      {saved ? <output className="text-muted text-xs">Terms saved.</output> : null}
    </form>
  );
}
