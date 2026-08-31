package com.blocbuy.payment.service;

import com.blocbuy.payment.gateway.PaymentGateway;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link PaymentAppService}. Transaction boundary: command methods {@code @Transactional},
 * queries {@code readOnly = true}. Talks to the provider only through the {@link PaymentGateway}
 * port.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentAppServiceImpl implements PaymentAppService {

  private final PaymentGateway paymentGateway;

  @Override
  @Transactional
  public void handleStripeWebhook(String payload, String signatureHeader) {
    // signature verification throws PAYMENT_400_001 on failure; irrelevant events are empty
    var event = paymentGateway.verifyWebhook(payload, signatureHeader);
    if (event.isEmpty()) {
      return;
    }
    // TODO(TEAM8-payment): deduplicate by event.eventId() (processed-event table or unique row),
    // load payment by event.providerPaymentIntentId(), transition state via the aggregate,
    // then trigger order-domain confirmation in the same local transaction.
    log.info("stripe webhook accepted: eventId={}, type={}", event.get().eventId(), event.get().eventType());
  }
}
