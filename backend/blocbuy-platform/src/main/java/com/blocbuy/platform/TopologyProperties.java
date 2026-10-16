package com.blocbuy.platform;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Binds {@code blocbuy.topology} (backend ADR 0008). Binding to an enum turns a typo into a startup
 * failure instead of silently matching neither topology's conditions.
 *
 * @param topology the deployment topology; {@code monolith} when unset
 */
@ConfigurationProperties("blocbuy")
public record TopologyProperties(@DefaultValue("monolith") Mode topology) {

    /** Values of {@code blocbuy.topology}; see {@link com.blocbuy.common.constant.Topology}. */
    public enum Mode {
        MONOLITH,
        DISTRIBUTED
    }
}
