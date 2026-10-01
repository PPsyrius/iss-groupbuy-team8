package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.blocbuy.bootstrap.config.StripeCheckoutConfiguration;
import com.blocbuy.bootstrap.config.StripePaymentFacadeConfiguration;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.PaymentBusinessPort;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.DisabledStripePaymentAppService;
import com.blocbuy.payment.service.PaymentAppService;
import com.blocbuy.payment.service.StripeCheckoutService;
import com.blocbuy.payment.service.StripeFeatureSwitch;
import com.blocbuy.payment.service.StripePaymentAppService;
import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.boot.validation.autoconfigure.ValidationAutoConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/** Rollout flags select one facade; invalid enabled deployments fail before accepting payments. */
class StripeCheckoutFlagConfigurationTest {
    private static final String[] VALID = {
        "blocbuy.payment.stripe.checkout-enabled=true",
        "blocbuy.payment.stripe.api-key=sk_test_local_fixture",
        "blocbuy.payment.stripe.webhook-secret=whsec_local_fixture",
        "blocbuy.payment.checkout.success-url=https://example.test/payments/return",
        "blocbuy.payment.checkout.cancel-url=https://example.test/payments/return"
    };

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withConfiguration(AutoConfigurations.of(ValidationAutoConfiguration.class))
                    .withUserConfiguration(
                            StripeCheckoutConfiguration.class,
                            StripePaymentFacadeConfiguration.class,
                            Collaborators.class);

    @Test
    void missingAndFalseFlagsSelectOnlyTheDisabledFacade() {
        runner.run(
                context -> {
                    assertThat(context).hasNotFailed();
                    assertThat(context.getBeansOfType(PaymentAppService.class)).hasSize(1);
                    assertThat(context.getBean(PaymentAppService.class))
                            .isInstanceOf(DisabledStripePaymentAppService.class);
                    assertThat(context.getBeansOfType(StripePaymentAppService.class)).isEmpty();
                    assertThat(context.getBeansOfType(StripeCheckoutService.class)).isEmpty();
                    assertThat(context.getBean(StripeFeatureSwitch.class).enabled()).isFalse();
                });
        runner.withPropertyValues("blocbuy.payment.stripe.checkout-enabled=false")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(PaymentAppService.class))
                                    .isInstanceOf(DisabledStripePaymentAppService.class);
                            assertThat(context.getBean(StripeFeatureSwitch.class).enabled())
                                    .isFalse();
                        });
    }

    @Test
    void enabledFlagSelectsOnlyCheckout() {
        runner.withPropertyValues(VALID)
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBeansOfType(PaymentAppService.class)).hasSize(1);
                            assertThat(context.getBean(PaymentAppService.class))
                                    .isInstanceOf(StripePaymentAppService.class);
                            assertThat(context.getBeansOfType(StripePaymentAppService.class))
                                    .hasSize(1);
                            assertThat(
                                            context.getBeansOfType(
                                                    DisabledStripePaymentAppService.class))
                                    .isEmpty();
                            assertThat(context.getBeansOfType(StripeCheckoutService.class))
                                    .hasSize(1);
                            assertThat(context.getBean(StripeFeatureSwitch.class).enabled())
                                    .isTrue();
                        });
    }

    @Test
    void enabledFlagRequiresEverySandboxSetting() {
        assertBadSetting("blocbuy.payment.stripe.api-key=", "STRIPE_API_KEY must be set");
        assertBadSetting(
                "blocbuy.payment.stripe.api-key=sk_live_not_allowed",
                "STRIPE_API_KEY must start with sk_test_");
        assertBadSetting(
                "blocbuy.payment.stripe.webhook-secret=", "STRIPE_WEBHOOK_SECRET must be set");
        assertBadSetting(
                "blocbuy.payment.checkout.success-url=",
                "BLOCBUY_PAYMENT_CHECKOUT_SUCCESS_URL must be set");
        assertBadSetting(
                "blocbuy.payment.checkout.cancel-url=",
                "BLOCBUY_PAYMENT_CHECKOUT_CANCEL_URL must be set");
    }

    @Test
    void legacyProfileEnablesTheSameCheckoutGate() {
        runner.withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues(Arrays.copyOfRange(VALID, 1, VALID.length))
                .withPropertyValues("spring.profiles.active=stripe-sprint2")
                .run(
                        context -> {
                            assertThat(context).hasNotFailed();
                            assertThat(context.getBean(StripeFeatureSwitch.class).enabled())
                                    .isTrue();
                        });
    }

    private void assertBadSetting(String override, String expected) {
        runner.withPropertyValues(VALID)
                .withPropertyValues(override)
                .run(
                        context -> {
                            assertThat(context).hasFailed();
                            assertThat(causeMessages(context.getStartupFailure()))
                                    .contains(expected);
                        });
    }

    private static String causeMessages(Throwable failure) {
        return Stream.iterate(failure, Throwable::getCause)
                .takeWhile(Objects::nonNull)
                .map(Throwable::getMessage)
                .filter(Objects::nonNull)
                .collect(Collectors.joining("\n"));
    }

    @Configuration(proxyBeanMethods = false)
    static class Collaborators {
        @Bean
        CheckoutPaymentRepository payments() {
            return mock(CheckoutPaymentRepository.class);
        }

        @Bean
        CheckoutGateway checkoutGateway() {
            return mock(CheckoutGateway.class);
        }

        @Bean
        PaymentBusinessPort business() {
            return mock(PaymentBusinessPort.class);
        }

        @Bean
        PlatformTransactionManager transactions() {
            return mock(PlatformTransactionManager.class);
        }
    }
}
