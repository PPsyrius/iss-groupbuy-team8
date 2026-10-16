package com.blocbuy.service.commerce.identity;

import jakarta.validation.constraints.NotNull;
import java.net.URI;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

/**
 * How commerce-svc reaches identity-svc (#468).
 *
 * @param baseUrl identity-svc over ECS Service Connect, {@code IDENTITY_BASE_URL} (for example
 *     {@code http://identity-svc:8589}); required, so a missing value fails startup
 * @param connectTimeout time to open the connection
 * @param readTimeout time to wait for the answer once connected; with the connect timeout it bounds
 *     how long a request (or the Stripe webhook transaction) waits for identity
 */
@Validated
@ConfigurationProperties("blocbuy.identity")
public record IdentityClientProperties(
        @NotNull URI baseUrl,
        @DefaultValue("500ms") Duration connectTimeout,
        @DefaultValue("1500ms") Duration readTimeout) {}
