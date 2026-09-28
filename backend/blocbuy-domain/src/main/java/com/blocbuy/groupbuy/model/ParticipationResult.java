package com.blocbuy.groupbuy.model;

import java.util.UUID;

/** Persisted historical outcome; replay never derives it from mutable membership or deal rows. */
public record ParticipationResult(
        String outcome, UUID dealId, String dealStatus, Integer currentSize, String reasonCode) {
    private static final String APPLIED = "applied";
    private static final String COMPENSATION_REQUIRED = "compensation_required";

    /** Rejects malformed terminal outcomes so persisted replay facts have a usable result shape. */
    public ParticipationResult {
        if (dealId == null
                || !(APPLIED.equals(outcome) || COMPENSATION_REQUIRED.equals(outcome))
                || (APPLIED.equals(outcome)
                        && (dealStatus == null || currentSize == null || reasonCode != null))
                || (COMPENSATION_REQUIRED.equals(outcome) && reasonCode == null)) {
            throw new IllegalArgumentException("Invalid participation result");
        }
    }

    /** True when the payment was admitted; false when it recorded a compensation requirement. */
    public boolean applied() {
        return APPLIED.equals(outcome);
    }

    /** Freezes successful admission's status and count for later replay, not a live deal view. */
    public static ParticipationResult applied(GroupDeal deal) {
        return new ParticipationResult(
                APPLIED, deal.id(), deal.status().code(), deal.currentSize(), null);
    }

    /** Records a rejected paid admission requiring compensation; does not execute a refund. */
    public static ParticipationResult compensation(UUID dealId, String reasonCode) {
        return new ParticipationResult(COMPENSATION_REQUIRED, dealId, null, null, reasonCode);
    }
}
