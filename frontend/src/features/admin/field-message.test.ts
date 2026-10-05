import { describe, expect, it } from "vitest";

import { messageOf, placeProblem } from "./field-message";

describe("messageOf", () => {
  it("joins schema issues and server strings, and skips anything else", () => {
    expect(messageOf(["Enter a title.", { message: "Too long." }, { code: 1 }, null, 3])).toBe(
      "Enter a title. Too long.",
    );
  });

  it("is undefined without a message", () => {
    expect(messageOf([])).toBeUndefined();
    expect(messageOf([{ kind: "x" }])).toBeUndefined();
  });
});

// A form whose only field is the title, recording what was placed on it.
const onlyTitle = (placed: Record<string, string>) => (name: string, message: string) => {
  if (name !== "title") return false;
  placed[name] = message;
  return true;
};

describe("placeProblem", () => {
  it("puts each field message on its field and leaves no alert", () => {
    const placed: Record<string, string> = {};
    expect(placeProblem({ fields: { title: "Too long." } }, onlyTitle(placed))).toBeUndefined();
    expect(placed).toEqual({ title: "Too long." });
  });

  it("gathers messages with no field into the alert, after its own description", () => {
    const placed: Record<string, string> = {};
    expect(
      placeProblem(
        {
          fields: { title: "Too long.", productNo: "Taken." },
          alert: { title: "Not saved", description: "Check the form." },
        },
        onlyTitle(placed),
      ),
    ).toEqual({ title: "Not saved", description: "Check the form. Taken." });
    expect(placed).toEqual({ title: "Too long." });
  });

  it("names unplaced field messages when no alert came with them", () => {
    expect(placeProblem({ fields: { productNo: "Taken." } }, () => false)).toEqual({
      title: "Some fields need another look",
      description: "Taken.",
    });
  });

  it("keeps the sign-in flag of an ended session", () => {
    expect(
      placeProblem(
        { alert: { title: "Not saved", description: "Your session ended." }, signIn: true },
        () => false,
      ),
    ).toEqual({ title: "Not saved", description: "Your session ended.", signIn: true });
  });
});
