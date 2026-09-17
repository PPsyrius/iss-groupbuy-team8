package com.blocbuy.catalog.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** TypeSafe System One connection settings; the API key always comes from the environment. */
@ConfigurationProperties(prefix = "blocbuy.catalog.search.typesafe")
public record TypeSafeJevProperties(
        boolean enabled,
        String apiKey,
        String endpoint,
        String model,
        int connectTimeoutMillis,
        int readTimeoutMillis) {

    /** Applies safe defaults and requires an API key only when the integration is enabled. */
    public TypeSafeJevProperties {
        if (endpoint == null || endpoint.isBlank()) {
            endpoint = "https://api.typesafe.ai/v1/systemone";
        }
        if (model == null || model.isBlank()) {
            model = "jev-latest";
        }
        if (connectTimeoutMillis <= 0) {
            connectTimeoutMillis = 1000;
        }
        if (readTimeoutMillis <= 0) {
            readTimeoutMillis = 2000;
        }
        if (enabled && (apiKey == null || apiKey.isBlank())) {
            throw new IllegalArgumentException(
                    "TYPESAFE_API_KEY must be configured when TypeSafe search is enabled");
        }
    }
}
