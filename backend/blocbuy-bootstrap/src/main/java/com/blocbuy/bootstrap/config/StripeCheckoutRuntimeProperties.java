package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.gateway.StripeProperties;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import java.util.regex.Pattern;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/** Required sandbox credentials when Checkout is enabled; never log these values. */
@Validated
@ConfigurationProperties(prefix = "blocbuy.payment.stripe")
public record StripeCheckoutRuntimeProperties(
        @NotBlank(message = "STRIPE_API_KEY must be set") String apiKey,
        @NotBlank(message = "STRIPE_WEBHOOK_SECRET must be set") String webhookSecret) {

    private static final Pattern SANDBOX_KEY =
            Pattern.compile(Pattern.quote(StripeProperties.SANDBOX_KEY_PREFIX) + ".+");

    /**
     * Derived check, so a failure report shows this boolean and never the rejected key. It keeps
     * the former {@code @Pattern("sk_test_.+")} semantics exactly (whole-string match, so a line
     * terminator still fails). A null key is left to {@code @NotBlank}; a blank one fails both.
     */
    @AssertTrue(message = "STRIPE_API_KEY must be a Stripe test key")
    public boolean isSandboxKey() {
        return apiKey == null || SANDBOX_KEY.matcher(apiKey).matches();
    }

    @Override
    public String toString() {
        return "StripeCheckoutRuntimeProperties[apiKey="
                + mask(apiKey)
                + ", webhookSecret="
                + mask(webhookSecret)
                + "]";
    }

    private static String mask(String secret) {
        return secret == null || secret.isBlank() ? "<unset>" : "<redacted>";
    }
}
