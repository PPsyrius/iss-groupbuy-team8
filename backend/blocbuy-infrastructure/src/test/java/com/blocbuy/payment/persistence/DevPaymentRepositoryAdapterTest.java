package com.blocbuy.payment.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class DevPaymentRepositoryAdapterTest {
    private final CheckoutPaymentRepository checkout = mock(CheckoutPaymentRepository.class);
    private final DevPaymentRepositoryAdapter adapter = new DevPaymentRepositoryAdapter(checkout);

    @Test
    void readsCheckoutUsingOriginalStringIdContract() {
        UUID id = UUID.randomUUID();
        CheckoutPayment payment = mock(CheckoutPayment.class);
        when(payment.id()).thenReturn(id);
        when(checkout.findById(id)).thenReturn(Optional.of(payment));
        assertThat(adapter.findById(id.toString()).orElseThrow().id()).isEqualTo(id.toString());
    }

    @Test
    void unknownUuidIsEmpty() {
        UUID id = UUID.randomUUID();
        when(checkout.findById(id)).thenReturn(Optional.empty());
        assertThat(adapter.findById(id.toString())).isEmpty();
    }

    @Test
    void invalidOrMissingIdDoesNotQueryDatabase() {
        assertThat(adapter.findById(null)).isEmpty();
        assertThat(adapter.findById(" ")).isEmpty();
        assertThat(adapter.findById("legacy-not-a-uuid")).isEmpty();
        verifyNoInteractions(checkout);
    }

    @Test
    void databaseFailureIsNotReportedAsMissingPayment() {
        UUID id = UUID.randomUUID();
        when(checkout.findById(id)).thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> adapter.findById(id.toString()))
                .isInstanceOf(IllegalStateException.class);
    }
}
