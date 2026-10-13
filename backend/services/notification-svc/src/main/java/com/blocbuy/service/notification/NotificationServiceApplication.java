package com.blocbuy.service.notification;

import com.blocbuy.bootstrap.config.TraceIdFilter;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Import;

/**
 * notification-svc entry point (backend ADR 0008). Scans only the contexts this service owns plus
 * the shared {@code com.blocbuy.common} packages, never {@code com.blocbuy.bootstrap} itself, whose
 * monolith entry class would pull in every context. {@code TraceIdFilter} is imported on its own
 * because the rest of {@code com.blocbuy.bootstrap.config} is commerce configuration.
 */
@SpringBootApplication(scanBasePackages = {"com.blocbuy.notification", "com.blocbuy.common"})
@MapperScan(
        basePackages = {"com.blocbuy.notification.persistence", "com.blocbuy.common.persistence"},
        annotationClass = Mapper.class)
@Import(TraceIdFilter.class)
public class NotificationServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(NotificationServiceApplication.class, args);
    }
}
