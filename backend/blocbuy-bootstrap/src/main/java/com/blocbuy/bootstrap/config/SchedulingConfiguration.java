package com.blocbuy.bootstrap.config;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.OutboxRelayJob;
import com.blocbuy.bootstrap.scheduling.PurchaseIntentExpiryJob;
import com.blocbuy.bootstrap.scheduling.RefundWorkerJob;
import com.blocbuy.bootstrap.scheduling.SchedulingProperties;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.gateway.OutboxEventTransport;
import com.blocbuy.common.repository.OutboxRelayRepository;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.common.service.OutboxRelayService;
import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import com.blocbuy.order.service.PurchaseIntentExpirySweeper;
import com.blocbuy.payment.service.RefundService;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBooleanProperty;
import org.springframework.boot.autoconfigure.condition.ConditionalOnExpression;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Scheduled jobs are deployment-opt-in. With both scheduling and the event relay disabled, no
 * scheduled work is registered.
 */
@Configuration(proxyBeanMethods = false)
@EnableScheduling
@EnableConfigurationProperties(SchedulingProperties.class)
public class SchedulingConfiguration {

    @Bean
    @ConditionalOnExpression(
            "${blocbuy.scheduling.enabled:false} or ${blocbuy.events.relay.enabled:false}")
    LeasedJobRunner leasedJobRunner(SchedulerLeaseRepository leases, MeterRegistry meters) {
        return new LeasedJobRunner(
                leases, meters, LeasedJobRunner.ownerId(System.getenv("HOSTNAME")));
    }

    @Bean
    @ConditionalOnBooleanProperty("blocbuy.scheduling.enabled")
    TeamExpirySweepJob teamExpirySweepJob(
            LeasedJobRunner runner,
            GroupDealExpirySweeper sweeper,
            SchedulingProperties properties,
            MeterRegistry meters) {
        return new TeamExpirySweepJob(runner, sweeper, properties.teamExpiry(), meters);
    }

    @Bean
    @ConditionalOnBooleanProperty("blocbuy.scheduling.enabled")
    RefundWorkerJob refundWorkerJob(
            LeasedJobRunner runner,
            RefundService refunds,
            SchedulingProperties properties,
            MeterRegistry meters) {
        return new RefundWorkerJob(runner, refunds, properties.refundWorker(), meters);
    }

    @Bean
    @ConditionalOnBooleanProperty("blocbuy.scheduling.enabled")
    PurchaseIntentExpiryJob purchaseIntentExpiryJob(
            LeasedJobRunner runner,
            PurchaseIntentExpirySweeper sweeper,
            SchedulingProperties properties,
            MeterRegistry meters) {
        return new PurchaseIntentExpiryJob(
                runner, sweeper, properties.purchaseIntentExpiry(), meters);
    }

    @Bean
    @ConditionalOnExpression(
            "${blocbuy.events.enabled:false} and ${blocbuy.events.relay.enabled:false}")
    OutboxRelayService outboxRelayService(
            OutboxRelayRepository repository, OutboxEventTransport transport) {
        return new OutboxRelayService(repository, transport);
    }

    @Bean
    @ConditionalOnExpression(
            "${blocbuy.events.enabled:false} and ${blocbuy.events.relay.enabled:false}")
    OutboxRelayJob outboxRelayJob(LeasedJobRunner runner, OutboxRelayService relay) {
        return new OutboxRelayJob(runner, relay);
    }
}
