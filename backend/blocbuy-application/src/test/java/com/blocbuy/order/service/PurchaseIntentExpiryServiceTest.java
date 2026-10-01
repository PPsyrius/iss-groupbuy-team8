package com.blocbuy.order.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.order.model.PurchaseIntent;
import com.blocbuy.order.model.PurchaseOperation;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.PaymentIntentExpirySupport;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class PurchaseIntentExpiryServiceTest {
    private static final Instant NOW = Instant.parse("2026-09-30T05:00:00Z");
    private final PurchaseIntentRepository intents = mock(PurchaseIntentRepository.class);
    private final GroupDealSettlementSupport teams = mock(GroupDealSettlementSupport.class);
    private final CheckoutPaymentRepository payments = mock(CheckoutPaymentRepository.class);
    private final PurchaseIntentExpiryService expiry =
            new PurchaseIntentExpiryService(
                    intents, teams, new PaymentIntentExpirySupport(payments));

    private PurchaseIntent intent() {
        return PurchaseIntent.issue(
                new PurchaseIntent.Terms(
                        UUID.randomUUID(),
                        PurchaseOperation.START,
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Product",
                        null,
                        BigDecimal.TEN,
                        "sgd",
                        2),
                NOW.plusSeconds(60),
                NOW.plusSeconds(3600),
                NOW);
    }

    @Test
    void aMissingOrSettlingIntentIsSkipped() {
        PurchaseIntent before = intent();
        when(intents.findById(before.id()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(before));
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.SKIPPED);
        when(intents.tryLockForExpiry(before.id())).thenReturn(Optional.empty());
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.SKIPPED);
        verifyNoInteractions(payments);
        verify(intents, never()).markExpired(any(), any());
    }

    @Test
    void onlyOverdueUnpaidUnfulfilledIntentsAreMarked() {
        PurchaseIntent before = intent();
        when(intents.findById(before.id())).thenReturn(Optional.of(before));
        when(intents.tryLockForExpiry(before.id())).thenReturn(Optional.of(before));
        when(intents.currentTime()).thenReturn(NOW).thenReturn(NOW.plusSeconds(60));
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.SKIPPED);
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.EXPIRED);
        var captured = org.mockito.ArgumentCaptor.forClass(PurchaseIntent.class);
        verify(intents).markExpired(eq(before), captured.capture());
        assertThat(captured.getValue().expiredAt()).isEqualTo(NOW.plusSeconds(60));
        var order = inOrder(teams, intents, payments);
        order.verify(intents).findById(before.id());
        order.verify(teams).lockTeam(before.dealId());
        order.verify(intents).tryLockForExpiry(before.id());
        order.verify(intents).currentTime();
        when(payments.hasSucceededIntent(before.id())).thenReturn(true);
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.PAID);
        when(intents.tryLockForExpiry(before.id()))
                .thenReturn(Optional.of(captured.getValue()))
                .thenReturn(Optional.of(before.fulfilBy(UUID.randomUUID())));
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.SKIPPED);
        assertThat(expiry.expireOne(before.id()))
                .isEqualTo(PurchaseIntentExpiryService.Outcome.SKIPPED);
    }
}
