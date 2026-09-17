package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import com.blocbuy.common.web.Result;
import com.blocbuy.payment.service.PaymentAppService;
import com.blocbuy.payment.web.StripeWebhookController;
import org.junit.jupiter.api.Test;

/** Stripe webhook adapter: raw payload forwarding and success acknowledgement. */
class StripeWebhookControllerTest {

    @Test
    void forwardsRawPayloadAndSignatureThenAcknowledgesEvent() {
        PaymentAppService paymentAppService = mock(PaymentAppService.class);
        StripeWebhookController controller = new StripeWebhookController(paymentAppService);
        String payload = "{ \"type\" : \"payment_intent.succeeded\" }";

        Result<Void> response = controller.handleStripeEvent(payload, "t=123,v1=signature");

        verify(paymentAppService).handleStripeWebhook(payload, "t=123,v1=signature");
        assertThat(response).isEqualTo(new Result<>("COMMON_200_000", null, "Success", null, null));
    }
}
