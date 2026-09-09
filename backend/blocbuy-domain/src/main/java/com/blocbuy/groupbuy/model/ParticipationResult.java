package com.blocbuy.groupbuy.model;

import java.util.UUID;

/** Persisted historical outcome; replay never derives it from mutable membership or deal rows. */
public record ParticipationResult(
        String outcome, UUID dealId, String dealStatus, Integer currentSize, String reasonCode) {
    public ParticipationResult {
        if (dealId == null
                || !("applied".equals(outcome) || "compensation_required".equals(outcome))
                || ("applied".equals(outcome)
                        && (dealStatus == null || currentSize == null || reasonCode != null))
                || ("compensation_required".equals(outcome) && reasonCode == null)) {
            throw new IllegalArgumentException("Invalid participation result");
        }
    }

    public static ParticipationResult applied(GroupDeal deal) {
        return new ParticipationResult(
                "applied", deal.id(), deal.status().code(), deal.currentSize(), null);
    }

    public static ParticipationResult compensation(UUID dealId, String reasonCode) {
        return new ParticipationResult("compensation_required", dealId, null, null, reasonCode);
    }
}
