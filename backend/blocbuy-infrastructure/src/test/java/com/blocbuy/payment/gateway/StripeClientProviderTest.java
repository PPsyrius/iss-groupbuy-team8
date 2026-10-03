package com.blocbuy.payment.gateway;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import org.junit.jupiter.api.Test;

/** One client per configuration; built lazily so startup tolerates missing secrets. */
class StripeClientProviderTest {
    @Test
    void reusesOneClientAcrossCalls() {
        StripeClientProvider provider =
                new StripeClientProvider(new StripeProperties("sk_test_placeholder", null));
        var first = provider.client();
        assertThat(first).isNotNull().isSameAs(provider.client());
    }

    @Test
    void rejectsLiveAndUnconfiguredKeysBeforeConstructingClient() {
        for (String key : new String[] {"sk_live_example", "", " "}) {
            var provider = new StripeClientProvider(new StripeProperties(key, null));
            assertThatThrownBy(provider::client).isInstanceOf(BizException.class);
        }
        var provider = new StripeClientProvider(new StripeProperties(null, null));
        assertThatThrownBy(provider::client).isInstanceOf(BizException.class);
    }
}
