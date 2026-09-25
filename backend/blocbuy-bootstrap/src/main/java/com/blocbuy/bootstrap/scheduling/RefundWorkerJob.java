package com.blocbuy.bootstrap.scheduling;

import com.blocbuy.payment.service.RefundService;
import io.micrometer.core.instrument.MeterRegistry;
import java.util.function.BooleanSupplier;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;

/** Attempts one due refund per tick when scheduling is enabled, regardless of checkout profile. */
@Slf4j
@RequiredArgsConstructor
public class RefundWorkerJob {
    static final String JOB = "refund-worker";
    static final String REFUNDS = "blocbuy.refund.worker.attempts";

    private final LeasedJobRunner runner;
    private final RefundService refundService;
    private final SchedulingProperties.RefundWorker settings;
    private final MeterRegistry meters;

    @Scheduled(
            fixedDelayString = "${blocbuy.scheduling.refund-worker.interval:PT30S}",
            initialDelayString = "${blocbuy.scheduling.refund-worker.initial-delay:PT45S}")
    public void run() {
        runner.run(JOB, settings.lease(), this::attemptOne);
    }

    private void attemptOne(BooleanSupplier leaseValid) {
        if (!leaseValid.getAsBoolean()) {
            return;
        }
        if (refundService.processNext()) {
            meters.counter(REFUNDS).increment();
            log.info("Refund worker attempted one obligation");
        } else {
            log.debug("Refund worker: nothing due");
        }
    }
}
