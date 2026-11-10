package com.blocbuy.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import cn.dev33.satoken.dao.SaTokenDao;
import com.blocbuy.identity.repository.RoleRepository;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class RbacAutoConfigurationTest {
    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(SaTokenDao.class, () -> mock(SaTokenDao.class))
                    .withConfiguration(AutoConfigurations.of(RbacAutoConfiguration.class));

    @Test
    void monolithUsesIdentityRepository() {
        runner.withBean(RoleRepository.class, () -> mock(RoleRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(StpInterfaceImpl.class);
                    assertThat(context).doesNotHaveBean(SessionStpInterface.class);
                });
    }

    @Test
    void distributedIdentityStillUsesItsRepository() {
        runner.withPropertyValues("blocbuy.topology=distributed")
                .withBean(RoleRepository.class, () -> mock(RoleRepository.class))
                .run(context -> {
                    assertThat(context).hasSingleBean(StpInterfaceImpl.class);
                    assertThat(context).doesNotHaveBean(SessionStpInterface.class);
                });
    }

    @Test
    void distributedServiceWithoutIdentityUsesSessionOnly() {
        runner.withPropertyValues("blocbuy.topology=distributed")
                .run(context -> {
                    assertThat(context).hasSingleBean(SessionStpInterface.class);
                    assertThat(context).doesNotHaveBean(StpInterfaceImpl.class);
                });
    }

    @Test
    void monolithNeverFallsBackToSessionRbac() {
        runner.run(context -> assertThat(context).doesNotHaveBean(SessionStpInterface.class));
    }
}
