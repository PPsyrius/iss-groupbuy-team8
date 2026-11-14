package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.bootstrap.scheduling.LeasedJobRunner;
import com.blocbuy.bootstrap.scheduling.SchedulingProperties;
import com.blocbuy.bootstrap.scheduling.TeamExpiryShardingProperties;
import com.blocbuy.bootstrap.scheduling.TeamExpirySweepJob;
import com.blocbuy.common.repository.SchedulerLeaseRepository;
import com.blocbuy.groupbuy.gateway.OpenDealOfferCache;
import com.blocbuy.groupbuy.service.GroupDealExpirySweeper;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.RefundGateway;
import com.sun.management.OperatingSystemMXBean;
import io.micrometer.core.instrument.MeterRegistry;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Opt-in, local-only source benchmark. Real PostgreSQL expiry/subscribers/outbox; no SNS, consumer,
 * provider, or cache network traffic. Normal verification does not run a throughput workload.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration",
            "spring.datasource.hikari.maximum-pool-size=5",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.events.enabled=true",
            "blocbuy.events.relay.enabled=false",
            "blocbuy.events.consumer.enabled=false",
            "blocbuy.scheduling.enabled=false",
            "logging.level.com.blocbuy=WARN",
            "logging.level.com.blocbuy.bootstrap.TeamExpiryShardingBenchmarkIT=INFO"
        })
@ActiveProfiles("local")
@EnabledIfSystemProperty(named = "blocbuy.expiry.benchmark", matches = "true")
@DirtiesContext
@Slf4j
class TeamExpiryShardingBenchmarkIT {
    private static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        POSTGRES.start();
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private JdbcTemplate jdbc;
    @Autowired private SqlSessionFactory sessions;
    @Autowired private GroupDealExpirySweeper sweeper;
    @Autowired private SchedulerLeaseRepository leases;
    @Autowired private MeterRegistry meters;
    @MockitoBean private OpenDealOfferCache cache;
    @MockitoBean private CheckoutGateway checkout;
    @MockitoBean private RefundGateway refunds;

    @Test
    void recordOriginalSingleFlightAndSixShardSourceThroughput() throws Exception {
        int teams = Integer.getInteger("blocbuy.expiry.benchmark.teams", 20000);
        assertThat(teams).isIn(2000, 20000, 40000, 80000);
        UUID product = UUID.randomUUID();
        UUID buyer = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status) VALUES(?,'local','active')",
                buyer);
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status,"
                        + "team_target_size,team_lifetime_seconds)"
                        + " VALUES(?,'EXPIRY-BENCH','Expiry benchmark',1,'sgd',100,'on_sale',3,3600)",
                product);
        measure(2000, 1, product, buyer);
        measure(2000, 6, product, buyer);
        List<Map<String, Object>> results = new ArrayList<>();
        for (int shards : List.of(1, 6, 6, 1)) {
            results.add(measure(teams, shards, product, buyer));
            Files.writeString(
                    Path.of("target", "expiry-sharding-benchmark-progress.json"),
                    JsonMapper.builder().build().writeValueAsString(results));
        }
        Map<String, Object> report = new LinkedHashMap<>();
        report.put("observedAt", Instant.now().toString());
        report.put("postgresVersion", jdbc.queryForObject("SELECT version()", String.class));
        report.put("teamsPerMeasuredRun", teams);
        report.put("commerceTaskSimulations", 6);
        report.put("sharedApplicationPoolMaximum", 5);
        report.put("fixedDelayMilliseconds", 1000);
        report.put("warmupTeamsPerMode", 2000);
        report.put("results", results);
        report.put(
                "limitations",
                List.of(
                        "One JVM/shared five-connection pool; not six ECS JVMs or thirty commerce connections",
                        "Unpaid one-recipient V4-shaped teams; paid refund correctness tested separately",
                        "Cache/provider mocks; no relay, SNS, consumers or notification-capacity claim",
                        "Separate Docker PostgreSQL/JVM CPU; no host attribution or CI timing threshold",
                        "Both-mode warmup and ABBA order do not eliminate JIT/cache/table-bloat effects"));
        Path output = Path.of("target", "expiry-sharding-benchmark.json");
        Files.writeString(output, JsonMapper.builder().build().writeValueAsString(report));
    }

    private Map<String, Object> measure(int teams, int shards, UUID product, UUID buyer)
            throws Exception {
        jdbc.execute(
                "TRUNCATE sys_outbox,sys_scheduled_item_retry,sys_scheduler_lock,biz_group_member,biz_group_deal");
        jdbc.update(
                "INSERT INTO biz_group_deal(id,deal_no,product_id,leader_user_id,deal_price,currency,"
                        + "target_size,current_size,status,start_time,expire_time)"
                        + " SELECT uuidv7(),'EXPIRY-BENCH-'||n,?,?,1,'sgd',"
                        + "3,0,'open',clock_timestamp()-interval '1 hour',clock_timestamp()-interval '1 minute'"
                        + " FROM generate_series(1,?) n",
                product,
                buyer,
                teams);
        jdbc.update(
                "INSERT INTO biz_group_member(deal_id,user_id,member_role,status,join_time)"
                        + " SELECT id,leader_user_id,'leader','joined',start_time FROM biz_group_deal");
        jdbc.execute("ANALYZE biz_group_deal");
        jdbc.execute("ANALYZE biz_group_member");
        String plan = scanPlan(shards);
        double before = expired();
        long pgCpuBefore = postgresCpuMicros();
        var os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
        long jvmCpuBefore = os.getProcessCpuTime();
        long began = System.nanoTime();
        var start = new CountDownLatch(1);
        List<Future<?>> workers = new ArrayList<>();
        try (var executor = Executors.newFixedThreadPool(6)) {
            for (int worker = 0; worker < 6; worker++) {
                var runner =
                        new LeasedJobRunner(
                                leases, meters, "expiry-benchmark/" + UUID.randomUUID());
                var job =
                        new TeamExpirySweepJob(
                                runner,
                                sweeper,
                                new SchedulingProperties.ExpirySweep(
                                        Duration.ofSeconds(1),
                                        Duration.ZERO,
                                        Duration.ofMinutes(2),
                                        500),
                                new TeamExpiryShardingProperties(shards),
                                meters);
                workers.add(
                        executor.submit(() -> {
                            if (!start.await(10, TimeUnit.SECONDS)) {
                                throw new AssertionError("benchmark start gate timed out");
                            }
                            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(10);
                            while (expired() - before < teams) {
                                if (System.nanoTime() >= deadline) {
                                    throw new AssertionError(
                                            "benchmark failed to drain within safety timeout");
                                }
                                job.run();
                                if (expired() - before < teams) {
                                    Thread.sleep(1000);
                                }
                            }
                            return null;
                        }));
            }
            start.countDown();
            for (Future<?> worker : workers) {
                worker.get(11, TimeUnit.MINUTES);
            }
        }
        double seconds = (System.nanoTime() - began) / 1_000_000_000.0;
        double jvmCpuSeconds = (os.getProcessCpuTime() - jvmCpuBefore) / 1_000_000_000.0;
        double pgCpuSeconds = (postgresCpuMicros() - pgCpuBefore) / 1_000_000.0;
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_deal WHERE status='expired'",
                                Integer.class))
                .isEqualTo(teams);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE event_type='TeamExpired'",
                                Integer.class))
                .isEqualTo(teams);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(DISTINCT aggregate_id) FROM sys_outbox WHERE event_type='TeamExpired'",
                                Integer.class))
                .isEqualTo(teams);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_scheduled_item_retry", Integer.class))
                .isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_refund", Integer.class)).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM biz_order", Integer.class)).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT stock FROM biz_product WHERE id=?", Integer.class, product))
                .isEqualTo(100);
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("shards", shards);
        result.put("seconds", seconds);
        result.put("createdEventsPerSecond", teams / seconds);
        result.put("jvmCpuSeconds", jvmCpuSeconds);
        result.put("postgresCpuSeconds", pgCpuSeconds);
        result.put("jvmAverageCpuCores", jvmCpuSeconds / seconds);
        result.put("postgresAverageCpuCores", pgCpuSeconds / seconds);
        result.put(
                "eventCreationSpanSeconds",
                jdbc.queryForObject(
                        "SELECT extract(epoch FROM max(created_at)-min(created_at)) FROM sys_outbox",
                        Double.class));
        result.put("candidateScanPlan", JsonMapper.builder().build().readTree(plan));
        log.info(
                "Local expiry benchmark: teams={} shards={} seconds={} eventsPerSecond={}"
                        + " jvmCpuSeconds={} postgresCpuSeconds={}",
                teams,
                shards,
                seconds,
                teams / seconds,
                jvmCpuSeconds,
                pgCpuSeconds);
        return result;
    }

    private double expired() {
        return meters.counter("blocbuy.team.expiry.teams", "result", "expired").count();
    }

    private long postgresCpuMicros() throws Exception {
        var cpu = POSTGRES.execInContainer("cat", "/sys/fs/cgroup/cpu.stat");
        assertThat(cpu.getExitCode()).isZero();
        for (String line : cpu.getStdout().split("\n")) {
            if (line.startsWith("usage_usec ")) {
                return Long.parseLong(line.substring("usage_usec ".length()).strip());
            }
        }
        throw new AssertionError("Docker cgroup v2 CPU accounting unavailable");
    }

    private String scanPlan(int shards) {
        String statement =
                "com.blocbuy.groupbuy.persistence.PaidGroupDealMapper."
                        + (shards == 1 ? "selectDueForExpiry" : "selectDueForExpiryShard");
        Map<String, Object> parameters =
                Map.of("now", Instant.now(), "limit", 500, "shardCount", shards, "shardIndex", 0);
        var bound =
                sessions.getConfiguration().getMappedStatement(statement).getBoundSql(parameters);
        return jdbc.execute(
                (ConnectionCallback<String>)
                        connection -> {
                            try (var explain =
                                    connection.prepareStatement(
                                            "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) "
                                                    + bound.getSql())) {
                                for (int i = 0; i < bound.getParameterMappings().size(); i++) {
                                    Object value =
                                            parameters.get(
                                                    bound.getParameterMappings()
                                                            .get(i)
                                                            .getProperty());
                                    explain.setObject(
                                            i + 1,
                                            value instanceof Instant instant
                                                    ? Timestamp.from(instant)
                                                    : value);
                                }
                                try (var rows = explain.executeQuery()) {
                                    if (!rows.next()) {
                                        throw new AssertionError("EXPLAIN returned no plan");
                                    }
                                    return rows.getString(1);
                                }
                            }
                        });
    }
}
