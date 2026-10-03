package com.blocbuy.common.model;

/**
 * How long a buyer has to pay a purchase intent once it is issued. Order enforces it on every
 * intent; catalog must cover it, so a team price policy that START reads stays in force for the
 * whole window and a team never closes before it. Shared by both contexts, so neither reaches into
 * the other's model for it (backend ADR 0001, addendum 2026-10-03).
 */
public final class PurchaseQuoteWindow {

    /** Forty minutes, in seconds. */
    public static final int SECONDS = 2400;

    private PurchaseQuoteWindow() {}
}
