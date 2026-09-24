package com.blocbuy.payment.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.ApiConstants;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.common.web.Result;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * CheckoutPayment resource controller. Protocol adaptation only: parse/validate requests, convert
 * to commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct provider SDK usage. Checkout retries use the Idempotency-Key header.
 */
@Tag(name = "payments", description = "CheckoutPayment resource")
@SecurityRequirement(name = "blocbuyToken")
@Validated
@RestController
@RequiredArgsConstructor
@RequestMapping(ApiConstants.API_V1 + "/payments")
public class StripeCheckoutController {
    private final PaymentBrowserService service;
    private final StripeFeatureSwitch feature;

    public record PaymentCapabilities(boolean enabled) {}

    @GetMapping("/capabilities")
    @Operation(
            operationId = "getPaymentCapabilities",
            summary = "Read Stripe rollout availability without database access")
    public Result<PaymentCapabilities> capabilities(HttpServletResponse response) {
        StpUtil.checkLogin();
        response.setHeader("Cache-Control", "no-store");
        return Result.ok(new PaymentCapabilities(feature.enabled()));
    }

    public record IntentRequest(
            @NotNull @Pattern(regexp = "START|JOIN") String operation,
            @NotNull UUID businessId,
            @Schema(
                            nullable = true,
                            description =
                                    "Optional invite reference (UC7). Attributes a JOIN to the"
                                            + " member who shared it; ignored for START and when"
                                            + " it cannot attribute this join")
                    @Pattern(regexp = "[A-Za-z0-9_-]{1,64}")
                    String ref) {}

    public record IntentResponse(UUID intentId) {}

    public record CheckoutRequest(@NotNull UUID intentId) {}

    @PostMapping("/intents")
    @Operation(
            operationId = "createPaymentIntent",
            summary = "Freeze a server-owned group purchase intent")
    public Result<IntentResponse> intent(
            @RequestHeader("X-BlocBuy-Request") String csrf,
            @Valid @RequestBody IntentRequest request) {
        checkCsrf(csrf);
        feature.requireEnabled();
        return Result.ok(
                new IntentResponse(
                        service.createIntent(
                                user(), request.operation(), request.businessId(), request.ref())));
    }

    @PostMapping("/checkout-sessions")
    @Operation(
            operationId = "createPaymentCheckout",
            summary = "Create or reuse an idempotent Stripe Hosted Checkout")
    public Result<StripeCheckoutService.CheckoutResult> checkout(
            @RequestHeader("X-BlocBuy-Request") String csrf,
            @RequestHeader("Idempotency-Key") @Pattern(regexp = "[A-Za-z0-9._-]{1,128}") String key,
            @Valid @RequestBody CheckoutRequest request) {
        checkCsrf(csrf);
        feature.requireEnabled();
        return Result.ok(service.prepare(user(), request.intentId(), key));
    }

    @GetMapping("/{id}")
    @Operation(
            operationId = "getPaymentReceipt",
            summary = "Read only the signed-in buyer's verified payment and order")
    public Result<PaymentBrowserService.Receipt> receipt(
            @PathVariable UUID id, HttpServletResponse response) {
        response.setHeader("Cache-Control", "no-store");
        feature.requireEnabled();
        return Result.ok(service.receipt(user(), id));
    }

    private UUID user() {
        StpUtil.checkLogin();
        return UUID.fromString(StpUtil.getLoginIdAsString());
    }

    private void checkCsrf(String value) {
        // JSON plus a non-simple header requires preflight cross-origin; no CORS is enabled.
        if (!"checkout".equals(value)) throw new BizException(CommonErrorCode.FORBIDDEN);
    }
}
