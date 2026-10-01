package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.service.DisabledStripePaymentAppService;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import com.blocbuy.payment.service.StripePaymentAppService;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Keep the Boot-specific property gate at the assembly boundary, not in the application module. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(StripeCheckoutFeatureProperties.class)
public class StripePaymentFacadeConfiguration {
    @Bean
    StripeFeatureSwitch stripeFeatureSwitch(StripeCheckoutFeatureProperties properties) {
        return new StripeFeatureSwitch(properties.checkoutEnabled());
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "blocbuy.payment.stripe",
            name = "checkout-enabled",
            havingValue = "true")
    StripePaymentAppService stripePaymentAppService(
            ObjectProvider<StripeCheckoutService> checkout) {
        return new StripePaymentAppService(checkout);
    }

    @Bean
    @ConditionalOnProperty(
            prefix = "blocbuy.payment.stripe",
            name = "checkout-enabled",
            havingValue = "false",
            matchIfMissing = true)
    DisabledStripePaymentAppService disabledStripePaymentAppService(CheckoutGateway gateway) {
        return new DisabledStripePaymentAppService(gateway);
    }
}
