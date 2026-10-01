package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.order.service.PurchaseIntentExpirySweeper;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Soft expiry only: late money still reaches the settlement/refund engine. */
@Slf4j
@RequiredArgsConstructor
public class PurchaseIntentExpiryJob {
    static final String JOB = "purchase-intent-expiry";
    static final String INTENTS = "blocbuy.purchase.intent.expiry.intents";
    private static final String RESULT_TAG = "result";
    private final LeasedJobRunner runner;
    private final PurchaseIntentExpirySweeper sweeper;
    private final SchedulingProperties.ExpirySweep settings;
    private final MeterRegistry meters;

    @Scheduled(
            fixedDelayString = "${blocbuy.scheduling.purchase-intent-expiry.interval:PT1M}",
            initialDelayString = "${blocbuy.scheduling.purchase-intent-expiry.initial-delay:PT30S}")
    public void run() {
        runner.run(JOB, settings.lease(), this::sweepOnce);
    }

    private void sweepOnce(BooleanSupplier leaseValid) {
        long started = System.nanoTime();
        var result = sweeper.sweep(settings.batchSize(), leaseValid);
        meters.counter(INTENTS, RESULT_TAG, "expired").increment(result.expired());
        meters.counter(INTENTS, RESULT_TAG, "skipped").increment(result.skipped());
        meters.counter(INTENTS, RESULT_TAG, "failed").increment(result.failed());
        long tookMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - started);
        if (result.due() > 0) {
            log.info(
                    "Intent expiry sweep: due={} expired={} skipped={} failed={} tookMs={}",
                    result.due(),
                    result.expired(),
                    result.skipped(),
                    result.failed(),
                    tookMs);
        } else {
            log.debug("Intent expiry sweep: nothing due, tookMs={}", tookMs);
        }
    }
}
