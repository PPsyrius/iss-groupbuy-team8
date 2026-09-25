package com.blocbuy.bootstrap.config;

import com.blocbuy.catalog.service.CatalogPurchaseSupport;
import com.blocbuy.groupbuy.service.GroupDealPurchaseSupport;
import com.blocbuy.groupbuy.service.GroupDealSettlementSupport;
import com.blocbuy.groupbuy.service.PaidGroupDealService;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.order.repository.OrderRepository;
import com.blocbuy.order.repository.PurchaseIntentRepository;
import com.blocbuy.order.service.OrderFulfilmentAdapter;
import com.blocbuy.order.service.PurchaseIntentAppService;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.gateway.PaymentIntentPort;
import com.blocbuy.payment.gateway.RefundGateway;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.repository.RefundRepository;
import com.blocbuy.payment.service.RefundService;
import com.blocbuy.payment.service.TeamExpiryRefundService;
import com.blocbuy.payment.service.TeamExpiryRefundSubscriber;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Assembles the two adapters that connect the payment slice to order, groupbuy and catalog.
 *
 * <p>Deliberately carries no profile. These beans are the business half of a checkout and are
 * always correct to have; whether checkout is reachable at all is decided separately by {@code
 * StripeFeatureSwitch} and by the provider wiring in {@link StripeCheckoutConfiguration}. Declaring
 * them here rather than annotating the classes with {@code @Service} keeps the wiring — and the
 * fact that two order-module classes implement payment-owned ports — visible in one place.
 */
@Configuration(proxyBeanMethods = false)
public class PaymentIntegrationConfiguration {

    @Bean
    PaymentIntentPort purchaseIntentAppService(
            CatalogPurchaseSupport products,
            GroupDealPurchaseSupport deals,
            UserAppService users,
            PurchaseIntentRepository intents) {
        return new PurchaseIntentAppService(products, deals, users, intents);
    }

    @Bean
    PaymentBusinessPort orderFulfilmentAdapter(
            PurchaseIntentRepository intents,
            OrderRepository orders,
            GroupDealPurchaseSupport teams,
            GroupDealSettlementSupport settlement,
            PaidGroupDealService groups,
            CatalogPurchaseSupport products,
            UserAppService users) {
        return new OrderFulfilmentAdapter(
                intents, orders, teams, settlement, groups, products, users);
    }

    @Bean
    TeamExpiryRefundService teamExpiryRefundService(
            CheckoutPaymentRepository repository,
            PaymentBusinessPort business,
            PlatformTransactionManager transactions) {
        return new TeamExpiryRefundService(repository, business, transactions);
    }

    @Bean
    TeamExpiryRefundSubscriber teamExpiryRefundSubscriber(TeamExpiryRefundService refunds) {
        return new TeamExpiryRefundSubscriber(refunds);
    }

    /** The scheduled refund worker also runs under ec2 without activating checkout. */
    @Bean
    RefundService refundService(
            RefundRepository repository,
            RefundGateway gateway,
            PlatformTransactionManager transactions) {
        return new RefundService(repository, gateway, transactions);
    }
}
