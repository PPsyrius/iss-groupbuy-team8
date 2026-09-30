package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.bootstrap.scheduling.SchedulingProperties;
import io.micrometer.core.instrument.MeterRegistry;
import java.io.OutputStreamWriter;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfSystemProperty;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Manual US23 measurement: a real HTTP server, PostgreSQL 18, Redis 8 and the production session
 * DAO. Nothing in this fixture is packaged into the application or exposed as a login endpoint.
 * Normal verify skips this class; -Dperf.enabled=true explicitly starts the bounded load run.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "spring.flyway.locations=classpath:db/migration",
            "blocbuy.payment.checkout.success-url=https://shop.example/payments/return",
            "blocbuy.payment.checkout.cancel-url=https://shop.example/payments/return",
            "blocbuy.scheduling.enabled=true",
            "blocbuy.scheduling.team-expiry.interval=PT1M",
            "blocbuy.scheduling.team-expiry.initial-delay=PT30S",
            "blocbuy.scheduling.team-expiry.batch-size=100",
            "blocbuy.scheduling.team-expiry.lease=PT2M",
            "blocbuy.scheduling.refund-worker.initial-delay=PT1H",
            "logging.level.com.blocbuy=INFO"
        })
@ActiveProfiles({"local", "stripe-sprint2"})
@EnabledIfSystemProperty(named = "perf.enabled", matches = "true")
@DirtiesContext
class PerformanceBaselineIT {
    private static final int VUS = 100;
    private static final int EXPIRY_TEAMS = 30;
    private static final PostgreSQLContainer POSTGRES =
            new PostgreSQLContainer("postgres:18.6").withDatabaseName("blocbuy_perf");
    private static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10.2").withExposedPorts(6379);

    @DynamicPropertySource
    static void databases(DynamicPropertyRegistry properties) {
        POSTGRES.start();
        REDIS.start();
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("spring.data.redis.password", () -> "");
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private MeterRegistry meters;
    @Autowired private SchedulingProperties scheduling;

    @Test
    void recordOneHundredUserBaselineAndExpiryLag() throws Exception {
        Path output = Path.of("target", "perf");
        Files.createDirectories(output);
        assertThat(scheduling.teamExpiry().interval().getSeconds()).isEqualTo(60);
        assertThat(scheduling.teamExpiry().initialDelay().getSeconds()).isEqualTo(30);
        assertThat(scheduling.teamExpiry().batchSize()).isEqualTo(100);
        assertThat(scheduling.teamExpiry().lease().getSeconds()).isEqualTo(120);
        new ResourceDatabasePopulator(new ClassPathResource("perf/seed.sql"))
                .execute(jdbc.getDataSource());
        List<Map<String, Object>> users = new ArrayList<>();
        var buyers =
                jdbc.queryForList(
                        "SELECT u.id::text,string_agg(o.order_no,',') AS order_nos"
                                + " FROM sys_user u JOIN biz_order o ON o.user_id=u.id"
                                + " WHERE u.nickname <> 'Synthetic buyer 0' GROUP BY u.id"
                                + " ORDER BY u.id");
        assertThat(buyers).hasSize(VUS);
        for (var buyer : buyers) {
            users.add(
                    Map.of(
                            "token",
                            StpUtil.getStpLogic().createLoginSession(buyer.get("id")),
                            "orderNos",
                            List.of(buyer.get("order_nos").toString().split(","))));
        }
        String product =
                jdbc.queryForObject(
                        "SELECT id::text FROM biz_product WHERE product_no='PERF-1'", String.class);
        String deal =
                jdbc.queryForObject(
                        "SELECT id::text FROM biz_group_deal WHERE deal_no='PERF-OPEN-1'",
                        String.class);
        var json = JsonMapper.builder().build();
        // Expiry cohorts cross their deadlines in each measured scenario; default 60-second timer.
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=clock_timestamp()"
                        + "+make_interval(secs => 20+((n-1)/10)*70)"
                        + " FROM generate_series(1,?) n WHERE deal_no='PERF-EXPIRY-'||n",
                EXPIRY_TEAMS);
        Process workload =
                new ProcessBuilder(
                                System.getProperty("perf.node", "node"),
                                Path.of("..", "perf", "workload.mjs").toString())
                        .redirectOutput(output.resolve("workload.json").toFile())
                        .redirectError(output.resolve("progress.log").toFile())
                        .start();
        try {
            try (var writer =
                    new OutputStreamWriter(workload.getOutputStream(), StandardCharsets.UTF_8)) {
                writer.write(
                        json.writeValueAsString(
                                Map.of(
                                        "baseUrl", "http://127.0.0.1:" + port,
                                        "users", users,
                                        "productId", product,
                                        "dealId", deal,
                                        "vus", VUS)));
            }
            Map<String, Map<String, Object>> observed = new LinkedHashMap<>();
            long deadline = System.nanoTime() + TimeUnit.MINUTES.toNanos(5);
            List<Map<String, Object>> rows;
            do {
                workload.waitFor(1, TimeUnit.SECONDS);
                rows =
                        jdbc.queryForList(
                                "SELECT deal_no,status,expire_time::text AS deadline,"
                                        + "update_time::text AS resolved_at,clock_timestamp()::text AS observed_at,"
                                        + "extract(epoch FROM update_time-expire_time)*1000 AS transition_lag_ms,"
                                        + "extract(epoch FROM clock_timestamp()-expire_time)*1000 AS observed_lag_ms"
                                        + " FROM biz_group_deal WHERE deal_no LIKE 'PERF-EXPIRY-%' ORDER BY deal_no");
                for (var row : rows) {
                    if ("expired".equals(row.get("status"))) {
                        observed.putIfAbsent(row.get("deal_no").toString(), row);
                    }
                }
            } while (workload.isAlive() && System.nanoTime() < deadline);
            assertThat(workload.isAlive())
                    .as("bounded workload must finish within five minutes")
                    .isFalse();
            // First post-commit observation is a conservative upper bound; all times are DB-owned.
            var expiries =
                    rows.stream()
                            .map(row -> observed.getOrDefault(row.get("deal_no").toString(), row))
                            .toList();
            double expired =
                    meters.counter("blocbuy.team.expiry.teams", "result", "expired").count();
            Files.writeString(
                    output.resolve("expiry.json"),
                    json.writeValueAsString(
                            Map.of(
                                    "teams",
                                    expiries,
                                    "expiredMetric",
                                    expired,
                                    "resolutionTargetMs",
                                    60_000,
                                    "resolutionTargetMet",
                                    expiries.size() == EXPIRY_TEAMS
                                            && expiries.stream()
                                                    .allMatch(
                                                            row ->
                                                                    "expired"
                                                                                    .equals(
                                                                                            row.get(
                                                                                                    "status"))
                                                                            && ((Number)
                                                                                                    row
                                                                                                            .get(
                                                                                                                    "observed_lag_ms"))
                                                                                            .doubleValue()
                                                                                    <= 60_000),
                                    "failedMetric",
                                    meters.counter("blocbuy.team.expiry.teams", "result", "failed")
                                            .count(),
                                    "schedulerIntervalSeconds",
                                    scheduling.teamExpiry().interval().getSeconds(),
                                    "schedulerInitialDelaySeconds",
                                    scheduling.teamExpiry().initialDelay().getSeconds(),
                                    "schedulerBatchSize",
                                    scheduling.teamExpiry().batchSize(),
                                    "schedulerLeaseSeconds",
                                    scheduling.teamExpiry().lease().getSeconds(),
                                    "maxResolutionLagMs",
                                    expiries.stream()
                                            .filter(row -> "expired".equals(row.get("status")))
                                            .mapToDouble(
                                                    row ->
                                                            ((Number) row.get("observed_lag_ms"))
                                                                    .doubleValue())
                                            .max()
                                            .orElse(Double.NaN))));
            assertThat(workload.exitValue()).as("workload errors; see target/perf").isZero();
            assertThat(expiries).hasSize(EXPIRY_TEAMS);
            assertThat(expiries)
                    .allSatisfy(row -> assertThat(row).containsEntry("status", "expired"));
            assertThat(expired).isEqualTo(EXPIRY_TEAMS);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM biz_order o JOIN biz_group_deal d ON d.id=o.deal_id"
                                            + " WHERE d.deal_no LIKE 'PERF-EXPIRY-%' AND o.status='cancelled'",
                                    Integer.class))
                    .isEqualTo(EXPIRY_TEAMS);
            assertThat(
                            jdbc.queryForObject(
                                    "SELECT count(*) FROM biz_refund r JOIN biz_group_deal d ON d.id=r.deal_id"
                                            + " WHERE d.deal_no LIKE 'PERF-EXPIRY-%' AND r.status='pending'",
                                    Integer.class))
                    .isEqualTo(EXPIRY_TEAMS);
        } finally {
            if (workload.isAlive()) {
                workload.destroyForcibly();
                workload.waitFor();
            }
            users.forEach(user -> StpUtil.logoutByTokenValue(user.get("token").toString()));
        }
    }
}
