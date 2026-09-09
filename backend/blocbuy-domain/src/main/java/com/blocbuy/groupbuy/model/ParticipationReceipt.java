package com.blocbuy.groupbuy.model;

/** Locked idempotency record. Result is null only for a newly reserved uncommitted receipt. */
public record ParticipationReceipt(String fingerprint, ParticipationResult result) {}
