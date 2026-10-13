package com.blocbuy.service.commerce;

import com.blocbuy.service.commerce.identity.RemoteIdentityConfiguration;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * commerce-svc entry point (backend ADR 0008). Scans only the contexts this service owns plus the
 * shared {@code com.blocbuy.common} packages, never {@code com.blocbuy.bootstrap} itself, whose
 * monolith entry class would pull in every context. The bootstrap {@code config} and {@code
 * scheduling} packages carry the Stripe, payment-integration and scheduled-job wiring this service
 * owns. Identity is not scanned: {@link RemoteIdentityConfiguration} supplies the {@code
 * UserAppService} as an HTTP client of identity-svc (#468).
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.blocbuy.catalog",
            "com.blocbuy.groupbuy",
            "com.blocbuy.order",
            "com.blocbuy.payment",
            "com.blocbuy.integration.outbox",
            "com.blocbuy.common",
            "com.blocbuy.bootstrap.config",
            "com.blocbuy.bootstrap.scheduling"
        })
@MapperScan(
        basePackages = {
            "com.blocbuy.catalog.persistence",
            "com.blocbuy.groupbuy.persistence",
            "com.blocbuy.order.persistence",
            "com.blocbuy.payment.persistence",
            "com.blocbuy.integration.outbox",
            "com.blocbuy.common.persistence"
        },
        annotationClass = Mapper.class)
@Import(RemoteIdentityConfiguration.class)
public class CommerceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceServiceApplication.class, args);
    }
}
