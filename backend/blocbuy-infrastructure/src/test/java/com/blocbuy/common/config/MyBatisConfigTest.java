package com.blocbuy.common.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.apache.ibatis.datasource.unpooled.UnpooledDataSource;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

/**
 * The global mapper scan belongs to the monolith only (backend ADR 0008). An empty in-memory {@link
 * SqlSessionFactory} lets the scanned mappers initialise without a database. {@link TopologyCheck}
 * turns an unknown topology into a startup failure instead of a silently skipped scan.
 */
class MyBatisConfigTest {

    private static final String IDENTITY_MAPPER = "sysUserMapper";
    private static final String COMMERCE_MAPPER = "bizProductMapper";

    private final ApplicationContextRunner runner =
            new ApplicationContextRunner()
                    .withBean(SqlSessionFactory.class, MyBatisConfigTest::unconnectedSessionFactory)
                    .withUserConfiguration(TopologyCheck.class, MyBatisConfig.class);

    @Test
    void scansEveryMapperWhenTheTopologyIsUnset() {
        runner.run(
                context -> {
                    assertThat(context).hasSingleBean(MyBatisConfig.class);
                    assertThat(context.containsBeanDefinition(IDENTITY_MAPPER)).isTrue();
                    assertThat(context.containsBeanDefinition(COMMERCE_MAPPER)).isTrue();
                });
    }

    @Test
    void scansEveryMapperInTheMonolith() {
        runner.withPropertyValues("blocbuy.topology=monolith")
                .run(context -> assertThat(context).hasSingleBean(MyBatisConfig.class));
    }

    @Test
    void leavesMapperScanningToTheServiceAssemblies() {
        runner.withPropertyValues("blocbuy.topology=distributed")
                .run(
                        context -> {
                            assertThat(context).doesNotHaveBean(MyBatisConfig.class);
                            assertThat(context.containsBeanDefinition(IDENTITY_MAPPER)).isFalse();
                        });
    }

    @Test
    void refusesToStartWithAMisspelledTopology() {
        runner.withPropertyValues("blocbuy.topology=monolit")
                .run(
                        context ->
                                assertThat(context)
                                        .getFailure()
                                        .isInstanceOf(IllegalStateException.class)
                                        .hasMessage(
                                                "blocbuy.topology must be monolith or distributed,"
                                                        + " not 'monolit'"));
    }

    @Test
    void refusesToStartWithAnEmptyTopology() {
        runner.withPropertyValues("blocbuy.topology=")
                .run(context -> assertThat(context).hasFailed());
    }

    @Test
    void acceptsTheTopologyInAnyCase() {
        runner.withPropertyValues("blocbuy.topology=Monolith")
                .run(context -> assertThat(context).hasSingleBean(MyBatisConfig.class));
    }

    /** Never opens a connection: the data source is only consulted when a statement runs. */
    private static SqlSessionFactory unconnectedSessionFactory() {
        Environment environment =
                new Environment("test", new JdbcTransactionFactory(), new UnpooledDataSource());
        return new SqlSessionFactoryBuilder().build(new Configuration(environment));
    }
}
