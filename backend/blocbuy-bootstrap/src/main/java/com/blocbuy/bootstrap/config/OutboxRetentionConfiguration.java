package com.blocbuy.bootstrap.config;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.OutboxRetentionJob;
import com.blocbuy.common.repository.OutboxRetentionRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Retention is installed only in an explicitly enabled event deployment with scheduling enabled.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnBooleanProperty("blocbuy.events.enabled")
public class OutboxRetentionConfiguration {

    @Bean
    @ConditionalOnBooleanProperty("blocbuy.scheduling.enabled")
    OutboxRetentionJob outboxRetentionJob(
            LeasedJobRunner runner, OutboxRetentionRepository outbox) {
        return new OutboxRetentionJob(runner, outbox);
    }
}
