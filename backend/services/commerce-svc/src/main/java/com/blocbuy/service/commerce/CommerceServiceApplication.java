package com.blocbuy.service.commerce;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * commerce-svc entry point (backend ADR 0008). Scans only the contexts this service owns plus the
 * shared {@code com.blocbuy.common} packages, never {@code com.blocbuy.bootstrap} itself, whose
 * monolith entry class would pull in every context. The bootstrap {@code config} and {@code
 * scheduling} packages carry the Stripe, payment-integration and scheduled-job wiring this service
 * owns.
 */
@SpringBootApplication(
        scanBasePackages = {
            "com.blocbuy.catalog",
            "com.blocbuy.groupbuy",
            "com.blocbuy.order",
            "com.blocbuy.payment",
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
            "com.blocbuy.common.persistence"
        },
        annotationClass = Mapper.class)
public class CommerceServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CommerceServiceApplication.class, args);
    }
}
