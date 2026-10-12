package com.blocbuy.common.constant;

/**
 * Deployment topology switch (backend ADR 0008). Compile-time constants so every module can use
 * them in {@code @ConditionalOnProperty}: the monolith is the default everywhere; only the ECS
 * service assemblies set {@code blocbuy.topology=distributed}.
 */
public final class Topology {

    /** Property that selects the topology. */
    public static final String PROPERTY = "blocbuy.topology";

    /** One process with every context (EC2 {@code dev}, local development, tests). */
    public static final String MONOLITH = "monolith";

    /** One of the three service assemblies on ECS. */
    public static final String DISTRIBUTED = "distributed";

    private Topology() {}
}
