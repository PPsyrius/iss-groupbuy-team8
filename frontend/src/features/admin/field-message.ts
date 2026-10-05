import type { SubmitProblem } from "./ListingForm";

// TanStack Form keeps every validator's errors: valibot issues from the schema, or the plain
// strings a failed save puts there. Shared by the listing form, the rule panel's form and the
// group terms form.
export function messageOf(errors: readonly unknown[]): string | undefined {
  const messages = errors.flatMap((e) => {
    if (typeof e === "string") return [e];
    if (e && typeof e === "object" && "message" in e && typeof e.message === "string")
      return [e.message];
    return [];
  });
  return messages.length > 0 ? messages.join(" ") : undefined;
}

// The form-level alert after a failed save; `signIn` links to sign-in when the session ended.
export type FormAlertState = NonNullable<SubmitProblem["alert"]> & { signIn?: boolean };

// Where a failed save's problem goes on a form: `place` puts a field's message on that field and
// says whether it could; the alert gathers the rest after the problem's own description. No alert
// when every message found its field.
export function placeProblem(
  problem: SubmitProblem,
  place: (name: string, message: string) => boolean,
): FormAlertState | undefined {
  const unplaced = Object.entries(problem.fields ?? {}).flatMap(([name, message]) =>
    place(name, message) ? [] : [message],
  );
  if (!problem.alert && unplaced.length === 0) return undefined;
  return {
    title: problem.alert?.title ?? "Some fields need another look",
    description: [problem.alert?.description, ...unplaced].filter(Boolean).join(" "),
    ...(problem.signIn ? { signIn: true } : {}),
  };
}
