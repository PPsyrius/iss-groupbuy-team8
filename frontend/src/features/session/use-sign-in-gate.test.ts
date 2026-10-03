import { act, renderHook } from "@testing-library/react";
import { describe, expect, it } from "vitest";

import { useSignInGate } from "./use-sign-in-gate";

const trust = { payNow: "S$71.00", closesMs: 1_800_000_000_000 };

describe("useSignInGate", () => {
  it("gives a signed-in buyer no gate handlers", () => {
    const { result } = renderHook(() => useSignInGate(false, trust));
    expect(result.current.onGated).toBeUndefined();
    expect(result.current.onGatedAlone).toBeUndefined();
    expect(result.current.sheet.isOpen).toBe(false);
  });

  it("opens with the bloc terms for Start or Join", () => {
    const { result } = renderHook(() => useSignInGate(true, trust));
    act(() => result.current.onGated?.());
    expect(result.current.sheet).toMatchObject({ isOpen: true, trust });
  });

  it("opens without the bloc terms for Buy alone and keeps them off while closing", () => {
    const { result } = renderHook(() => useSignInGate(true, trust));
    act(() => result.current.onGatedAlone?.());
    expect(result.current.sheet.isOpen).toBe(true);
    expect(result.current.sheet.trust).toBeUndefined();
    act(() => result.current.sheet.onOpenChange(false));
    expect(result.current.sheet.isOpen).toBe(false);
    expect(result.current.sheet.trust).toBeUndefined();
  });
});
