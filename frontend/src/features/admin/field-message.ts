// TanStack Form keeps every validator's errors: valibot issues from the schema, or the plain
// strings a failed save puts there. Shared by the listing form and the rule panel's form.
export function messageOf(errors: readonly unknown[]): string | undefined {
  const messages = errors.flatMap((e) => {
    if (typeof e === "string") return [e];
    if (e && typeof e === "object" && "message" in e && typeof e.message === "string")
      return [e.message];
    return [];
  });
  return messages.length > 0 ? messages.join(" ") : undefined;
}
