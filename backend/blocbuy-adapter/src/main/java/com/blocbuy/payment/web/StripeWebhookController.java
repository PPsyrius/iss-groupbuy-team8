package com.blocbuy.payment.web;

import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.web.Result;
import com.blocbuy.payment.service.PaymentAppService;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Stripe webhook inbound adapter. Public path (no Sa-Token login — see SaTokenConfig); authenticity
 * is established by signature verification in the gateway, not by session auth. The raw request
 * body is forwarded untouched — re-serialization breaks signature verification. Always answers 2xx
 * quickly for handled/ignored events so the provider does not retry storms; failures surface via
 * the global exception handler.
 */
@Tag(name = "webhooks", description = "Payment provider callbacks")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/webhooks")
@RequiredArgsConstructor
public class StripeWebhookController {

    /** Header carrying Stripe's event signature. */
    public static final String STRIPE_SIGNATURE_HEADER = "Stripe-Signature";

    private final PaymentAppService paymentAppService;

    /** Receives a Stripe event notification. */
    @PostMapping(value = "/stripe", consumes = MediaType.APPLICATION_JSON_VALUE)
    public Result<Void> handleStripeEvent(
            @RequestBody String payload,
            @RequestHeader(name = STRIPE_SIGNATURE_HEADER) String signatureHeader) {
        paymentAppService.handleStripeWebhook(payload, signatureHeader);
        return Result.ok();
    }
}
