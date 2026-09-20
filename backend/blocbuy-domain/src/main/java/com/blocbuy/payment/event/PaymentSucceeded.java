package com.blocbuy.payment.event;

import com.blocbuy.payment.model.CheckoutQuote;
import java.time.Instant;
import java.util.UUID;

/** Stable fact consumed synchronously in the payment transaction; replay uses paymentId. */
public record PaymentSucceeded(UUID paymentId, CheckoutQuote quote, Instant confirmedAt) {}
