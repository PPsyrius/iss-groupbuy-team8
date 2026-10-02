import { describe, expect, it } from "vitest";

import { messageOf } from "./field-message";

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
