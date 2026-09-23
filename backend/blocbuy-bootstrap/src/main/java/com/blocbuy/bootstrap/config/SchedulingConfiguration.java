package com.blocbuy.bootstrap.config;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.SchedulingProperties;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs, opt-in. Only the {@code ec2} and {@code docker} profiles set {@code
 * blocbuy.scheduling.enabled}; everywhere else — tests included — no scheduler thread exists, so no
 * job can race a test that drives the same use case by hand.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
@ConditionalOnBooleanProperty("blocbuy.scheduling.enabled")
public class SchedulingConfiguration {

    @Bean
    LeasedJobRunner leasedJobRunner(SchedulerLeaseRepository leases, MeterRegistry meters) {
        return new LeasedJobRunner(
                leases, meters, LeasedJobRunner.ownerId(System.getenv("HOSTNAME")));
    }

    @Bean
    TeamExpirySweepJob teamExpirySweepJob(
            LeasedJobRunner runner,
            GroupDealExpirySweeper sweeper,
            SchedulingProperties properties,
            MeterRegistry meters) {
        return new TeamExpirySweepJob(runner, sweeper, properties.teamExpiry(), meters);
    }
}
