package com.blocbuy.bootstrap.config;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.PurchaseIntentExpiryJob;
import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.SchedulingProperties;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import com.blocbuy.order.service.PurchaseIntentExpirySweeper;
import com.blocbuy.payment.service.RefundService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs, deployment-opt-in. Every profile defaults {@code blocbuy.scheduling.enabled} to
 * false, so no scheduler thread exists until an operator completes the backlog check and enables it
 * explicitly.
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

    @Bean
    RefundWorkerJob refundWorkerJob(
            LeasedJobRunner runner,
            RefundService refunds,
            SchedulingProperties properties,
            MeterRegistry meters) {
        return new RefundWorkerJob(runner, refunds, properties.refundWorker(), meters);
    }

    @Bean
    PurchaseIntentExpiryJob purchaseIntentExpiryJob(
            LeasedJobRunner runner,
            PurchaseIntentExpirySweeper sweeper,
            SchedulingProperties properties,
            MeterRegistry meters) {
        return new PurchaseIntentExpiryJob(
                runner, sweeper, properties.purchaseIntentExpiry(), meters);
    }
}
