package com.blocbuy.payment.gateway;

import com.stripe.StripeClient;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import org.springframework.stereotype.Component;

/**
 * Shared, lazily built Stripe client. Built on first use rather than at startup so the application
 * still boots without Stripe secrets; every caller must pass the sandbox gate first.
 */
@Component
public class StripeClientProvider {
    private final StripeProperties properties;
    private StripeClient client;

    public StripeClientProvider(StripeProperties properties) {
        this.properties = properties;
    }

    @SuppressFBWarnings(
            value = "EI_EXPOSE_REP",
            justification = "StripeClient is a stateless SDK client meant to be shared by design")
    public synchronized StripeClient client() {
        if (client == null) {
            client =
                    StripeClient.builder()
                            .setApiKey(properties.apiKey())
                            .setConnectTimeout(3000)
                            .setReadTimeout(10000)
                            .setMaxNetworkRetries(1)
                            .build();
        }
        return client;
    }
}
