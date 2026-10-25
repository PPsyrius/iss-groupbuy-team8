package com.blocbuy.integration.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.repository.IntegrationRecipientRepository;
import com.blocbuy.payment.event.PaymentSucceeded;
import com.blocbuy.payment.model.CheckoutQuote;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.slf4j.MDC;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

class IntegrationOutboxWriterTest {

    private final OutboxMapper outbox = mock(OutboxMapper.class);
    private final IntegrationRecipientRepository recipients =
            mock(IntegrationRecipientRepository.class);
    private final IntegrationOutboxWriter writer =
            new IntegrationOutboxWriter(outbox, recipients, new ObjectMapper());

    @AfterEach
    void clearTrace() {
        MDC.remove(ApiConstants.TRACE_ID_MDC_KEY);
    }

    @Test
    void teamSuccessEnrichesActiveRecipientsAndPreservesTraceInJsonPayload() {
        UUID dealId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        UUID recipient = UUID.randomUUID();
        when(recipients.findActiveMemberIdsByDealId(dealId)).thenReturn(List.of(recipient));
        MDC.put(ApiConstants.TRACE_ID_MDC_KEY, "trace-test");

        writer.onTeamSucceeded(
                new TeamSucceeded(dealId, productId, 2, Instant.parse("2026-10-12T00:00:00Z")));

        ArgumentCaptor<OutboxInsert> row = ArgumentCaptor.forClass(OutboxInsert.class);
        verify(outbox).insert(row.capture());
        assertThat(row.getValue().eventType()).isEqualTo("TeamSucceeded");
        assertThat(row.getValue().eventVersion()).isEqualTo(1);
        assertThat(row.getValue().aggregateId()).isEqualTo(dealId);
        assertThat(row.getValue().traceId()).isEqualTo("trace-test");
        assertThat(row.getValue().payload()).contains(recipient.toString(), productId.toString());
    }

    @Test
    void expiredTeamUsesSameTransactionRecipientLookup() {
        UUID dealId = UUID.randomUUID();
        UUID productId = UUID.randomUUID();
        when(recipients.findActiveMemberIdsByDealId(dealId)).thenReturn(List.of());

        writer.onTeamExpired(
                new TeamExpired(dealId, productId, Instant.parse("2026-10-12T00:00:00Z")));

        verify(recipients).findActiveMemberIdsByDealId(dealId);
        verify(outbox).insert(any(OutboxInsert.class));
    }

    @Test
    void paymentPayloadContainsReferencesButNoAmountOrProviderSecrets() {
        UUID paymentId = UUID.randomUUID();
        UUID intentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID dealId = UUID.randomUUID();
        var quote =
                new CheckoutQuote(
                        intentId,
                        userId,
                        "JOIN",
                        dealId,
                        new BigDecimal("9.99"),
                        "sgd",
                        Instant.parse("2026-10-13T00:00:00Z"),
                        Instant.parse("2026-10-14T00:00:00Z"));

        writer.onPaymentSucceeded(
                new PaymentSucceeded(paymentId, quote, Instant.parse("2026-10-12T00:00:00Z")));

        ArgumentCaptor<OutboxInsert> row = ArgumentCaptor.forClass(OutboxInsert.class);
        verify(outbox).insert(row.capture());
        assertThat(row.getValue().eventType()).isEqualTo("PaymentSucceeded");
        assertThat(row.getValue().aggregateId()).isEqualTo(paymentId);
        assertThat(row.getValue().payload())
                .contains(
                        paymentId.toString(),
                        intentId.toString(),
                        userId.toString(),
                        dealId.toString())
                .doesNotContain("9.99", "sk_test_", "email", "token");
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "\t"})
    void missingOrBlankTraceUsesFallbackUuid(String trace) {
        if (trace != null) {
            MDC.put(ApiConstants.TRACE_ID_MDC_KEY, trace);
        }
        UUID dealId = UUID.randomUUID();
        when(recipients.findActiveMemberIdsByDealId(dealId)).thenReturn(List.of());

        writer.onTeamExpired(new TeamExpired(dealId, UUID.randomUUID(), Instant.now()));

        ArgumentCaptor<OutboxInsert> row = ArgumentCaptor.forClass(OutboxInsert.class);
        verify(outbox).insert(row.capture());
        String generated = row.getValue().traceId();
        assertThat(UUID.fromString(generated).toString()).isEqualTo(generated);
    }

    @Test
    void serializationFailureAbortsTheBusinessTransactionBeforeInsert() {
        UUID dealId = UUID.randomUUID();
        ObjectMapper brokenJson = mock(ObjectMapper.class);
        when(brokenJson.writeValueAsString(any())).thenThrow(mock(JacksonException.class));
        var brokenWriter = new IntegrationOutboxWriter(outbox, recipients, brokenJson);

        var event = new TeamExpired(dealId, UUID.randomUUID(), Instant.now());
        assertThatThrownBy(() -> brokenWriter.onTeamExpired(event))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("Could not serialize outbox event TeamExpired");
        verifyNoInteractions(outbox);
    }
}
