package com.blocbuy.order.service;

import com.blocbuy.order.repository.PurchaseIntentRepository;
import java.time.Duration;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/** Bounded selection followed by independent transactions, matching the team-expiry pattern. */
@Slf4j
@Service
@RequiredArgsConstructor
public class PurchaseIntentExpirySweeper {
    static final Duration RETRY_DELAY = Duration.ofMinutes(5);
    private final PurchaseIntentRepository intents;
    private final PurchaseIntentExpiryService expiry;

    public record Result(int due, int expired, int skipped, int failed) {}

    public Result sweep(int limit, BooleanSupplier leaseValid) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be positive");
        }
        var due = intents.dueForExpiry(intents.currentTime(), limit);
        int expired = 0;
        int skipped = 0;
        int failed = 0;
        for (var id : due) {
            if (!leaseValid.getAsBoolean()) {
                break;
            }
            var result = expireCandidate(id);
            if (result == CandidateResult.EXPIRED) {
                expired++;
            } else if (result == CandidateResult.SKIPPED) {
                skipped++;
            } else {
                failed++;
            }
        }
        return new Result(due.size(), expired, skipped, failed);
    }

    private CandidateResult expireCandidate(UUID id) {
        RuntimeException failure = null;
        try {
            var outcome = expiry.expireOne(id);
            if (outcome == PurchaseIntentExpiryService.Outcome.EXPIRED) {
                return CandidateResult.EXPIRED;
            }
            if (outcome == PurchaseIntentExpiryService.Outcome.PAID) {
                intents.excludePaidFromExpiry(id);
                return CandidateResult.SKIPPED;
            }
        } catch (RuntimeException e) {
            failure = e;
        }
        // Temporary contention or failure yields its place to younger unpaid rows.
        try {
            intents.deferExpiryRetry(id, RETRY_DELAY);
        } catch (RuntimeException backoffFailure) {
            var combined =
                    new IllegalStateException(
                            "Purchase intent " + id + " expiry backoff could not be recorded",
                            backoffFailure);
            if (failure != null) {
                combined.addSuppressed(failure);
            }
            throw combined;
        }
        if (failure != null) {
            log.warn("Intent {} expiry failed; retry deferred for {}", id, RETRY_DELAY, failure);
        }
        return failure == null ? CandidateResult.SKIPPED : CandidateResult.FAILED;
    }

    private enum CandidateResult {
        EXPIRED,
        SKIPPED,
        FAILED
    }
}
