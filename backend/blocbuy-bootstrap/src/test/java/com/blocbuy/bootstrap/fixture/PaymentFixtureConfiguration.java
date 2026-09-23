package com.blocbuy.bootstrap.fixture;

import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.payment.service.PaymentBrowserService;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.TeamExpiryRefundService;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

/** Explicit import in the integration test only: no HTTP endpoints or scheduled jobs. */
@TestConfiguration(proxyBeanMethods = false)
public class PaymentFixtureConfiguration {
    @Bean
    PaymentLifecycleFixture paymentLifecycleFixture(
            JdbcTemplate jdbc,
            PaymentBrowserService browser,
            GroupDealSettlementSupport teams,
            StripeCheckoutService payments,
            TeamExpiryRefundService refunds,
            PlatformTransactionManager transactions) {
        return new PaymentLifecycleFixture(jdbc, browser, teams, payments, refunds, transactions);
    }
}
