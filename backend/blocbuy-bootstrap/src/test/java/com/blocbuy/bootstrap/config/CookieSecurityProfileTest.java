package com.blocbuy.bootstrap.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.boot.test.context.ConfigDataApplicationContextInitializer;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * Both auth cookies carry {@code Secure} exactly on the HTTPS profiles (#96): {@code ec2} sits
 * behind CloudFront's redirect-to-https, {@code local} and {@code docker} are plain HTTP. The
 * Sa-Token session cookie and the OAuth state-binding cookie must never disagree.
 */
class CookieSecurityProfileTest {

    @ParameterizedTest
    @CsvSource({"ec2, true", "local, false", "docker, false"})
    void cookieFlagsFollowTheProfileTransport(String profile, boolean expected) {
        new ApplicationContextRunner()
                .withInitializer(new ConfigDataApplicationContextInitializer())
                .withPropertyValues("spring.profiles.active=" + profile)
                .run(context -> {
                    var environment = context.getEnvironment();
                    assertThat(environment.getProperty("sa-token.cookie.http-only", Boolean.class))
                            .isTrue();
                    assertThat(environment.getProperty("sa-token.cookie.same-site"))
                            .isEqualTo("Lax");
                    assertThat(environment.getProperty("sa-token.cookie.secure", Boolean.class))
                            .isEqualTo(expected);
                    assertThat(
                                    environment.getProperty(
                                            "blocbuy.frontend.state-cookie-secure", Boolean.class))
                            .isEqualTo(expected);
                });
    }
}
