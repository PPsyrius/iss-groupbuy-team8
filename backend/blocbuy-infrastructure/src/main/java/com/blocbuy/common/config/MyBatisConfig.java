package com.blocbuy.common.config;

import org.apache.ibatis.annotations.Mapper;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.context.annotation.Configuration;

/**
 * MyBatis wiring. Mapper interfaces live only in {@code com.blocbuy.**.persistence} packages (by
 * convention + ArchUnit), are annotated {@link Mapper}, and never leave the infrastructure layer.
 * SQL parameters use {@code #{}} binding only; {@code ${}} is restricted to server-side enum
 * whitelists for identifiers.
 */
@Configuration
@MapperScan(basePackages = "com.blocbuy", annotationClass = Mapper.class)
public class MyBatisConfig {}
