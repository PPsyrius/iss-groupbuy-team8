package com.blocbuy.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/**
 * Row-count mapping and argument guards; the SQL itself is proven in SchedulerLeaseRepositoryIT.
 */
@ExtendWith(MockitoExtension.class)
class SchedulerLeaseRepositoryImplTest {
    private static final String KEY = "team-expiry-sweep";
    private static final String OWNER = "host/0199";

    @Mock private SysSchedulerLockMapper mapper;
    @InjectMocks private SchedulerLeaseRepositoryImpl leases;

    @Test
    void oneRowMeansAcquiredAndTheLeaseIsPassedInSeconds() {
        when(mapper.acquire(KEY, OWNER, 120.0)).thenReturn(1);

        assertThat(leases.tryAcquire(KEY, OWNER, Duration.ofMinutes(2))).isTrue();
    }

    @Test
    void zeroRowsMeansAnotherOwnerHoldsTheLease() {
        when(mapper.acquire(KEY, OWNER, 1.5)).thenReturn(0);

        assertThat(leases.tryAcquire(KEY, OWNER, Duration.ofMillis(1500))).isFalse();
    }

    @Test
    void anyOtherRowCountIsATechnicalFailure() {
        Duration lease = Duration.ofMinutes(2);
        when(mapper.acquire(anyString(), anyString(), anyDouble())).thenReturn(2);

        assertThatThrownBy(() -> leases.tryAcquire(KEY, OWNER, lease))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void releaseReportsWhetherTheLeaseWasStillOurs() {
        when(mapper.release(KEY, OWNER)).thenReturn(1, 0, 2);

        assertThat(leases.release(KEY, OWNER)).isTrue();
        assertThat(leases.release(KEY, OWNER)).isFalse();
        assertThatThrownBy(() -> leases.release(KEY, OWNER))
                .isInstanceOf(IllegalStateException.class);
        verify(mapper, times(3)).release(KEY, OWNER);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   "})
    void blankKeysAndOwnersAreRejected(String blank) {
        Duration lease = Duration.ofMinutes(2);

        assertThatThrownBy(() -> leases.tryAcquire(blank, OWNER, lease))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.tryAcquire(KEY, blank, lease))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.release(blank, OWNER))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.release(KEY, blank))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void valuesLongerThanTheV13ColumnsAreRejected() {
        String longKey = "k".repeat(SchedulerLeaseRepositoryImpl.MAX_KEY_LENGTH + 1);
        String longOwner = "o".repeat(SchedulerLeaseRepositoryImpl.MAX_OWNER_LENGTH + 1);
        Duration lease = Duration.ofMinutes(2);

        assertThatThrownBy(() -> leases.tryAcquire(longKey, OWNER, lease))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.tryAcquire(KEY, longOwner, lease))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }

    @Test
    void theLeaseMustLastAtLeastOneMillisecond() {
        Duration tooShort = Duration.ofNanos(999_999);
        Duration negative = Duration.ofSeconds(-1);

        assertThatThrownBy(() -> leases.tryAcquire(KEY, OWNER, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.tryAcquire(KEY, OWNER, Duration.ZERO))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.tryAcquire(KEY, OWNER, tooShort))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> leases.tryAcquire(KEY, OWNER, negative))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(mapper);
    }
}
