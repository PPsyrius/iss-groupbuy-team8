package com.blocbuy.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Intent persistence: the claim race, the replayed claim and the vanished row. */
class PurchaseIntentRepositoryImplTest {

    private static final Instant NOW = Instant.parse("2026-09-22T10:00:00Z");

    private final BizPurchaseIntentMapper mapper = mock(BizPurchaseIntentMapper.class);
    private final PurchaseIntentRepositoryImpl repository =
            new PurchaseIntentRepositoryImpl(mapper);

    private final UUID intentId = UUID.randomUUID();
    private final UUID userId = UUID.randomUUID();
    private final UUID dealId = UUID.randomUUID();
    private final UUID productId = UUID.randomUUID();

    private PurchaseIntent intent(UUID fulfilledPayment, int version) {
        return PurchaseIntent.reconstitute(
                intentId,
                new PurchaseIntent.Terms(
                        userId,
                        PurchaseOperation.JOIN,
                        dealId,
                        productId,
                        "Sourdough starter kit",
                        null,
                        new BigDecimal("12.50"),
                        "sgd",
                        3),
                NOW.plusSeconds(2400),
                NOW.plusSeconds(86_400),
                fulfilledPayment,
                NOW,
                version);
    }

    private BizPurchaseIntentDO row(UUID fulfilledPayment, int version) {
        return PurchaseIntentConverter.toRow(intent(fulfilledPayment, version));
    }

    @Test
    void saveWritesTheRowAndReturnsTheIssuedIntent() {
        PurchaseIntent issued = intent(null, 0);
        assertThat(repository.save(issued)).isSameAs(issued);
        verify(mapper).insert(any(BizPurchaseIntentDO.class));
    }

    @Test
    void readsMapThroughTheConverterAndKeepMissesEmpty() {
        when(mapper.selectById(intentId)).thenReturn(row(null, 0));
        when(mapper.lockById(intentId)).thenReturn(null);

        assertThat(repository.findById(intentId))
                .hasValueSatisfying(
                        found -> {
                            assertThat(found.id()).isEqualTo(intentId);
                            assertThat(found.operation()).isEqualTo(PurchaseOperation.JOIN);
                            assertThat(found.amount()).isEqualByComparingTo("12.50");
                            assertThat(found.targetSize()).isEqualTo(3);
                        });
        assertThat(repository.lockById(intentId)).isEmpty();
    }

    @Test
    void aWonClaimReturnsTheRowWithTheBumpedVersion() {
        UUID payment = UUID.randomUUID();
        when(mapper.markFulfilled(intentId, payment, 0)).thenReturn(1);
        when(mapper.selectById(intentId)).thenReturn(row(payment, 1));

        PurchaseIntent claimed = repository.markFulfilled(intent(null, 0), payment);

        assertThat(claimed.fulfilledPaymentId()).isEqualTo(payment);
        assertThat(claimed.version()).isEqualTo(1);
    }

    @Test
    void theSamePaymentReplayingItsOwnClaimWritesNothing() {
        UUID payment = UUID.randomUUID();
        PurchaseIntent alreadyMine = intent(payment, 1);

        assertThat(repository.markFulfilled(alreadyMine, payment)).isSameAs(alreadyMine);
        verify(mapper, never()).markFulfilled(any(), any(), org.mockito.ArgumentMatchers.anyInt());
    }

    @Test
    void aLostRaceAgainstTheSamePaymentStillReadsAsFulfilled() {
        UUID payment = UUID.randomUUID();
        when(mapper.markFulfilled(intentId, payment, 0)).thenReturn(0);
        when(mapper.selectById(intentId)).thenReturn(row(payment, 1));

        assertThat(repository.markFulfilled(intent(null, 0), payment).fulfilledPaymentId())
                .isEqualTo(payment);
    }

    @Test
    void aLostRaceAgainstAnotherPaymentIsRejected() {
        UUID mine = UUID.randomUUID();
        when(mapper.markFulfilled(intentId, mine, 0)).thenReturn(0);
        when(mapper.selectById(intentId)).thenReturn(row(UUID.randomUUID(), 1));

        assertThatThrownBy(() -> repository.markFulfilled(intent(null, 0), mine))
                .isInstanceOf(BizException.class);
    }

    @Test
    void aRowMissingItsNotNullCountersFailsTheDomainGuardRatherThanUnboxing() {
        BizPurchaseIntentDO broken = row(null, 0);
        broken.setTargetSize(null);
        when(mapper.selectById(intentId)).thenReturn(broken);

        // target_size and version are NOT NULL columns; the converter defaults rather than
        // throwing an NPE, and the aggregate then rejects the impossible value.
        assertThatThrownBy(() -> repository.findById(intentId)).isInstanceOf(BizException.class);
    }

    @Test
    void aRowWithoutAVersionReadsAsVersionZero() {
        BizPurchaseIntentDO noVersion = row(null, 0);
        noVersion.setVersion(null);
        when(mapper.selectById(intentId)).thenReturn(noVersion);

        assertThat(repository.findById(intentId))
                .hasValueSatisfying(found -> assertThat(found.version()).isZero());
    }

    @Test
    void aVanishedRowIsNotFound() {
        UUID payment = UUID.randomUUID();
        when(mapper.markFulfilled(intentId, payment, 0)).thenReturn(0);
        when(mapper.selectById(intentId)).thenReturn(null);

        assertThatThrownBy(() -> repository.markFulfilled(intent(null, 0), payment))
                .isInstanceOf(BizException.class);
    }
}
