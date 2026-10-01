import {
  Alert,
  Button,
  Calendar,
  DateField,
  DatePicker,
  Description,
  FieldError,
  InputGroup,
  Label,
  TextField,
} from "@heroui/react";
import type { ZonedDateTime } from "@internationalized/date";
import { revalidateLogic, useForm, useStore } from "@tanstack/react-form";
import { Link, useLocation } from "@tanstack/react-router";
import { useState } from "react";

import type { PolicyResponse } from "@/api/generated/types.gen";

import { messageOf } from "./field-message";
import type { SubmitProblem } from "./ListingForm";
import { formatSgd } from "./money";
import { replacesActiveNow, type RuleTiming } from "./policy";
import { type PolicyFormValues, policyFormDefaults } from "./policy-form";
import { policyFormSchema } from "./policy-form.schema";
import { formatSgtMinute } from "./sgt";

type FieldName = keyof PolicyFormValues;
const fieldNames = new Set<string>(["amount", "validFrom", "validUntil"]);
const isFieldName = (name: string): name is FieldName => fieldNames.has(name);

// The revision form inside the bloc price rule panel (admin plan A2-01 step 4, artboard 4b.4): the
// bloc price as a decimal string, and both ends on Singapore minute pickers. Valid until is the
// exact end, which the rule does not include (§6 Q13). Validation first runs on submit, then on
// every change; a failed save's field messages stay on their fields until that field changes, as
// on the listing form. A later start on an active rule raises the replace-now warning (step 5).
export function PolicyForm({
  rule,
  timing,
  originalPrice,
  nowMs,
  onSubmit,
  onCancel,
}: Readonly<{
  rule: PolicyResponse | null;
  timing: RuleTiming;
  originalPrice: string;
  nowMs: number;
  onSubmit: (values: PolicyFormValues) => Promise<SubmitProblem | undefined>;
  onCancel: () => void;
}>) {
  const [alert, setAlert] = useState<SubmitProblem["alert"] & { signIn?: boolean }>();
  const { href } = useLocation();
  const form = useForm({
    defaultValues: policyFormDefaults(rule, nowMs),
    validationLogic: revalidateLogic(),
    validators: { onDynamic: policyFormSchema({ originalPrice, nowMs }) },
    listeners: {
      onChange: ({ formApi, fieldApi }) => {
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
  const validFrom = useStore(form.store, (state) => state.values.validFrom);
  const startMs = validFrom ? validFrom.toDate().getTime() : null;
  const isSubmitting = useStore(form.store, (state) => state.isSubmitting);

  return (
    <form
      noValidate
      aria-label="New bloc price rule"
      className="border-border flex flex-col gap-4 border-t pt-4"
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

      {/* A text field, not a NumberField: the price is a decimal string and never a float. */}
      <form.Field name="amount">
        {(field) => (
          <TextField
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            isInvalid={messageOf(field.state.meta.errors) !== undefined}
          >
            <Label>Bloc price</Label>
            <InputGroup>
              <InputGroup.Prefix>S$</InputGroup.Prefix>
              <InputGroup.Input inputMode="decimal" placeholder="0.00" className="tabular-nums" />
            </InputGroup>
            <Description>Strictly below the solo price of {formatSgd(originalPrice)}.</Description>
            <FieldError>{messageOf(field.state.meta.errors)}</FieldError>
          </TextField>
        )}
      </form.Field>

      <form.Field name="validFrom">
        {(field) => (
          <MinutePicker
            label="Valid from"
            help="Singapore time."
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            error={messageOf(field.state.meta.errors)}
          />
        )}
      </form.Field>

      <form.Field name="validUntil">
        {(field) => (
          <MinutePicker
            label="Valid until"
            help="Singapore time. The rule ends at this minute."
            value={field.state.value}
            onChange={field.handleChange}
            onBlur={field.handleBlur}
            error={messageOf(field.state.meta.errors)}
          />
        )}
      </form.Field>

      {/* The backend applies only the latest revision, with no fallback (§11 D3). */}
      {startMs !== null && replacesActiveNow(timing, startMs, nowMs) ? (
        <Alert status="warning">
          <Alert.Indicator />
          <Alert.Content>
            <Alert.Description>
              Saving replaces the active rule now. Buyers see no bloc price until{" "}
              {formatSgtMinute(startMs)} SGT.
            </Alert.Description>
          </Alert.Content>
        </Alert>
      ) : null}

      <div className="flex gap-2">
        <Button type="submit" variant="secondary" className="min-h-11" isDisabled={isSubmitting}>
          Save rule
        </Button>
        <Button variant="ghost" className="min-h-11" onPress={onCancel} isDisabled={isSubmitting}>
          Cancel
        </Button>
      </div>
      <p className="text-muted text-xs">
        Saving appends a revision. Prices already locked into a quote never change.
      </p>
    </form>
  );
}

// A Singapore date and time to the minute. The value is a `ZonedDateTime` in Asia/Singapore, so
// the segments show Singapore wall time whatever the admin's device zone; the zone name is hidden
// because the help text says it.
function MinutePicker({
  label,
  help,
  value,
  onChange,
  onBlur,
  error,
}: Readonly<{
  label: string;
  help: string;
  value: ZonedDateTime | null;
  onChange: (value: ZonedDateTime | null) => void;
  onBlur: () => void;
  error: string | undefined;
}>) {
  return (
    <DatePicker
      value={value}
      onChange={onChange}
      onBlur={onBlur}
      granularity="minute"
      hideTimeZone
      isInvalid={error !== undefined}
    >
      <Label>{label}</Label>
      <DateField.Group fullWidth>
        <DateField.Input>{(segment) => <DateField.Segment segment={segment} />}</DateField.Input>
        <DateField.Suffix>
          <DatePicker.Trigger>
            <DatePicker.TriggerIndicator />
          </DatePicker.Trigger>
        </DateField.Suffix>
      </DateField.Group>
      <Description>{help}</Description>
      <FieldError>{error}</FieldError>
      <DatePicker.Popover>
        <Calendar aria-label={label}>
          <Calendar.Header>
            <Calendar.Heading />
            <Calendar.NavButton slot="previous" />
            <Calendar.NavButton slot="next" />
          </Calendar.Header>
          <Calendar.Grid>
            <Calendar.GridHeader>
              {(day) => <Calendar.HeaderCell>{day}</Calendar.HeaderCell>}
            </Calendar.GridHeader>
            <Calendar.GridBody>{(date) => <Calendar.Cell date={date} />}</Calendar.GridBody>
          </Calendar.Grid>
        </Calendar>
      </DatePicker.Popover>
    </DatePicker>
  );
}
