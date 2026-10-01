package com.blocbuy.bootstrap.scheduling;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

/** Proves that Spring's timer invokes the enabled job without a direct test call to {@code run}. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.purchase-intent-expiry.initial-delay=PT0.1S",
            "blocbuy.scheduling.purchase-intent-expiry.interval=PT1H"
        })
class PurchaseIntentExpiryTimerTest {

    @MockitoBean private LeasedJobRunner runner;

    @Test
    void enabledTimerInvokesTheLeasedJob() {
        verify(runner, timeout(5000))
                .run(eq(PurchaseIntentExpiryJob.JOB), eq(Duration.ofMinutes(2)), any());
    }
}
