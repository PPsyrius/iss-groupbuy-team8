package com.blocbuy.common.config;

import com.blocbuy.common.constant.Topology;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * Rejects any {@code blocbuy.topology} other than {@code monolith} or {@code distributed} before a
 * single bean is created (backend ADR 0008). Without it a typo such as {@code monolit} matches
 * neither topology's conditions: the monolith would skip {@link MyBatisConfig}'s mapper scan and
 * fail later on a missing mapper bean, far from the cause. Runs in every assembly; the service
 * assemblies additionally require {@code distributed}.
 */
@Configuration(proxyBeanMethods = false)
public class TopologyCheck {

    @Bean
    static BeanFactoryPostProcessor topologyValueCheck(Environment environment) {
        return beanFactory -> requireKnown(environment.getProperty(Topology.PROPERTY));
    }

    /** Unset means the monolith; values compare case-insensitively, as the conditions do. */
    static void requireKnown(String value) {
        if (value == null
                || Topology.MONOLITH.equalsIgnoreCase(value)
                || Topology.DISTRIBUTED.equalsIgnoreCase(value)) {
            return;
        }
        throw new IllegalStateException(
                Topology.PROPERTY
                        + " must be "
                        + Topology.MONOLITH
                        + " or "
                        + Topology.DISTRIBUTED
                        + ", not '"
                        + value
                        + "'");
    }
}
