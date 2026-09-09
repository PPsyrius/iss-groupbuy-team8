package com.blocbuy.payment.web;

import com.blocbuy.common.constant.ApiConstants;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Payment resource controller. Protocol adaptation only: parse/validate requests, convert to
 * commands/queries, call the application service. No business transactions, no mapper calls, no
 * direct provider SDK usage. Endpoints land with their use cases. TODO(TEAM8-payment): add
 * endpoints; write endpoints define idempotency ({@code Idempotency-Key} header, see {@link
 * ApiConstants#IDEMPOTENCY_KEY_HEADER}).
 */
@Tag(name = "payments", description = "Payment resource")
@Validated
@RestController
@RequestMapping(ApiConstants.API_V1 + "/payments")
public class PaymentController {}
