package com.blocbuy.platform;

import com.blocbuy.common.constant.Topology;
import java.util.Locale;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.restclient.RestClientCustomizer;
import org.springframework.context.annotation.Bean;

/**
 * Support every service assembly gets by depending on this module (backend ADR 0008). The monolith
 * never has it on its classpath.
 */
@AutoConfiguration
@EnableConfigurationProperties(TopologyProperties.class)
public class PlatformAutoConfiguration {

    /**
     * Fails fast unless the assembly runs distributed: as {@code monolith} it would keep the global
     * mapper scan and the database-backed role lookup that the service does not own.
     */
    @Bean
    InitializingBean distributedTopologyGuard(TopologyProperties properties) {
        return () -> {
            if (properties.topology() != TopologyProperties.Mode.DISTRIBUTED) {
                throw new IllegalStateException(
                        "A service assembly must run with "
                                + Topology.PROPERTY
                                + "="
                                + Topology.DISTRIBUTED
                                + ", not "
                                + properties.topology().name().toLowerCase(Locale.ROOT));
            }
        };
    }

    /** Applies trace propagation to every {@code RestClient.Builder} the context hands out. */
    @Bean
    RestClientCustomizer traceIdPropagation() {
        return builder -> builder.requestInterceptor(new TraceIdPropagationInterceptor());
    }
}
