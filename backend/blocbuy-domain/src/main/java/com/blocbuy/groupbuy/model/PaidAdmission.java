package com.blocbuy.groupbuy.model;

/**
 * Outcome of admitting one verified payment to an existing team.
 *
 * <p>{@code teamCompleted} describes this admission's transition, not a request to inspect a
 * reconstituted team's current status. The application persists {@code deal} and publishes the
 * completion fact in the same transaction; the aggregate does not depend on an event publisher.
 *
 * @param deal the immutable team snapshot after admission
 * @param teamCompleted whether this payment filled the final place
 */
public record PaidAdmission(GroupDeal deal, boolean teamCompleted) {

    /** Rejects an outcome that disagrees with the aggregate's post-admission state. */
    public PaidAdmission {
        if (deal == null || teamCompleted != (deal.status() == DealStatus.SUCCEEDED)) {
            throw new IllegalArgumentException("Paid admission must match its team state");
        }
    }
}
