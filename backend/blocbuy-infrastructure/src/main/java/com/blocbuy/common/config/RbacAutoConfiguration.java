package com.blocbuy.common.config;

import cn.dev33.satoken.dao.SaTokenDao;
import com.blocbuy.common.constant.Topology;
import com.blocbuy.identity.repository.RoleRepository;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;

/** Selects RBAC after the assembly's repositories have been registered (backend ADR 0008). */
@AutoConfiguration
public class RbacAutoConfiguration {

    /** Identity and the monolith retain their database-backed authorization. */
    @Bean
    @ConditionalOnBean(RoleRepository.class)
    StpInterfaceImpl databaseStpInterface(RoleRepository roles) {
        return new StpInterfaceImpl(roles);
    }

    /** Distributed assemblies without identity read the shared login snapshot only. */
    @Bean
    @ConditionalOnMissingBean(RoleRepository.class)
    @ConditionalOnProperty(name = Topology.PROPERTY, havingValue = Topology.DISTRIBUTED)
    SessionStpInterface sessionStpInterface(SaTokenDao sessions) {
        return new SessionStpInterface(sessions);
    }
}
