package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** One client per configuration; built lazily so startup tolerates missing secrets. */
class StripeClientProviderTest {
    @Test
    void reusesOneClientAcrossCalls() {
        StripeClientProvider provider =
                new StripeClientProvider(new StripeProperties("test-placeholder", null));
        var first = provider.client();
        assertThat(first).isNotNull().isSameAs(provider.client());
    }
}
