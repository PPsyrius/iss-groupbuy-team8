package com.blocbuy.payment.gateway;

/** Sandbox invariants for provider-reported facts, independent of API key configuration. */
public final class StripeSandbox {
    private StripeSandbox() {}

    public static boolean isSandboxMode(Boolean liveMode) {
        return !Boolean.TRUE.equals(liveMode);
    }
}
