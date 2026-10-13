package com.blocbuy.common.config;

import com.blocbuy.common.constant.Topology;
import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis wiring. Mapper interfaces live only in {@code com.blocbuy.**.persistence} packages (by
 * convention + ArchUnit), are annotated {@link Mapper}, and never leave the infrastructure layer.
 * SQL parameters use {@code #{}} binding only; {@code ${}} is restricted to server-side enum
 * whitelists for identifiers.
 *
 * <p>Monolith only: each service assembly declares its own {@code @MapperScan} for the contexts it
 * owns (backend ADR 0008), so this global scan must not run there.
 */
@Configuration
@ConditionalOnProperty(
        name = Topology.PROPERTY,
        havingValue = Topology.MONOLITH,
        matchIfMissing = true)
@MapperScan(basePackages = "com.blocbuy", annotationClass = Mapper.class)
public class MyBatisConfig {}
