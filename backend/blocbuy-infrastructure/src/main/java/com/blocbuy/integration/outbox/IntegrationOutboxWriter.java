package com.blocbuy.integration.outbox;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.events.contract.PaymentSucceededV1;
import com.blocbuy.events.contract.TeamExpiredV1;
import com.blocbuy.events.contract.TeamSucceededV1;
import com.blocbuy.groupbuy.event.TeamExpired;
import com.blocbuy.groupbuy.event.TeamSucceeded;
import com.blocbuy.groupbuy.repository.IntegrationRecipientRepository;
import com.blocbuy.payment.event.PaymentSucceeded;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.slf4j.MDC;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

/** Writes versioned cross-service facts atomically with their business transaction. */
@Component
@RequiredArgsConstructor
@ConditionalOnBooleanProperty("blocbuy.events.enabled")
public class IntegrationOutboxWriter {

    private static final int VERSION = 1;
    private static final String TEAM_SUCCEEDED = "TeamSucceeded";
    private static final String TEAM_EXPIRED = "TeamExpired";
    private static final String PAYMENT_SUCCEEDED = "PaymentSucceeded";

    private final OutboxMapper outbox;
    private final IntegrationRecipientRepository recipients;
    private final ObjectMapper json;

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTeamSucceeded(TeamSucceeded event) {
        var payload =
                new TeamSucceededV1(
                        event.dealId(),
                        event.productId(),
                        event.size(),
                        recipients.findActiveMemberIdsByDealId(event.dealId()));
        write(TEAM_SUCCEEDED, event.dealId(), payload);
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onTeamExpired(TeamExpired event) {
        var payload =
                new TeamExpiredV1(
                        event.dealId(),
                        event.productId(),
                        event.at(),
                        recipients.findActiveMemberIdsByDealId(event.dealId()));
        write(TEAM_EXPIRED, event.dealId(), payload);
    }

    @Order(Ordered.LOWEST_PRECEDENCE)
    @TransactionalEventListener(phase = TransactionPhase.BEFORE_COMMIT)
    public void onPaymentSucceeded(PaymentSucceeded event) {
        var quote = event.quote();
        var payload =
                new PaymentSucceededV1(
                        event.paymentId(),
                        quote.intentId(),
                        quote.userId(),
                        quote.operation(),
                        quote.businessId(),
                        event.confirmedAt());
        write(PAYMENT_SUCCEEDED, event.paymentId(), payload);
    }

    private void write(String eventType, UUID aggregateId, Object payload) {
        try {
            String body = json.writeValueAsString(payload);
            outbox.insert(new OutboxInsert(eventType, VERSION, aggregateId, body, traceId()));
        } catch (JacksonException e) {
            throw new IllegalStateException("Could not serialize outbox event " + eventType, e);
        }
    }

    private static String traceId() {
        String traceId = MDC.get(ApiConstants.TRACE_ID_MDC_KEY);
        return traceId == null || traceId.isBlank() ? UUID.randomUUID().toString() : traceId;
    }
}
