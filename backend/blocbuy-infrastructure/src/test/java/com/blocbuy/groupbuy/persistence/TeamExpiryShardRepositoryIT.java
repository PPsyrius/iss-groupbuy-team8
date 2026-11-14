package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.common.persistence.UuidTypeHandler;
import java.io.InputStream;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Real PostgreSQL 18 unsigned UUID partitioning with the production mapper and existing indexes.
 */
@Testcontainers
class TeamExpiryShardRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final Instant NOW = Instant.parse("2026-11-14T00:00:00Z");
    private static JdbcTemplate jdbc;
    private static SqlSessionFactory sessions;

    @BeforeAll
    static void migrate() throws Exception {
        String url = POSTGRES.getJdbcUrl();
        var source =
                new DriverManagerDataSource(
                        url + (url.contains("?") ? "&" : "?") + "currentSchema=domain",
                        POSTGRES.getUsername(),
                        POSTGRES.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("CREATE SCHEMA domain");
        Path migrations =
                Path.of(
                        System.getProperty("basedir"),
                        "..",
                        "blocbuy-bootstrap",
                        "src",
                        "main",
                        "resources",
                        "db",
                        "migration");
        new ResourceDatabasePopulator(
                        new FileSystemResource(migrations.resolve("V5__groupbuy.sql")),
                        new FileSystemResource(migrations.resolve("V17__scheduled_item_retry.sql")))
                .execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        for (String resource :
                List.of(
                        "mapper/groupbuy/BizGroupDealMapper.xml",
                        "mapper/groupbuy/PaidGroupDealMapper.xml")) {
            try (InputStream in = new ClassPathResource(resource).getInputStream()) {
                new XMLMapperBuilder(in, config, resource, config.getSqlFragments()).parse();
            }
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @BeforeEach
    void clearIsolatedFixture() {
        jdbc.execute("TRUNCATE sys_scheduled_item_retry,biz_group_member,biz_group_deal");
    }

    @Test
    void everyUnsignedByteBelongsToExactlyOneShardForEveryAllowedCount() {
        seed(256);
        try (var session = sessions.openSession()) {
            var repository =
                    new PaidGroupDealRepositoryImpl(session.getMapper(PaidGroupDealMapper.class));
            List<UUID> legacy = repository.dueForExpiry(NOW, 500);
            assertThat(legacy).hasSize(256).contains(id(0), id(127), id(128), id(255));
            for (int count = 1; count <= 6; count++) {
                var union = new HashSet<UUID>();
                int selected = 0;
                for (int index = 0; index < count; index++) {
                    List<UUID> expected = new ArrayList<>();
                    for (UUID id : legacy) {
                        if ((id.getLeastSignificantBits() & 255) % count == index) {
                            expected.add(id);
                        }
                    }
                    List<UUID> partition = repository.dueForExpiry(NOW, 500, count, index);
                    assertThat(partition).containsExactlyElementsOf(expected);
                    selected += partition.size();
                    union.addAll(partition);
                }
                assertThat(selected).isEqualTo(256);
                assertThat(union).containsExactlyInAnyOrderElementsOf(legacy);
            }
            session.rollback();
        }
    }

    @Test
    void partitionPrecedesTheFiveHundredLimitAndRetainsEligibilityOrderingAndLogicalRetryKey() {
        seed(3600);
        jdbc.update("UPDATE biz_group_deal SET deleted=true WHERE id=?", id(6));
        jdbc.update("UPDATE biz_group_deal SET status='succeeded' WHERE id=?", id(12));
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=?::timestamptz + interval '1 second' WHERE id=?",
                NOW.toString(),
                id(18));
        jdbc.update(
                "INSERT INTO sys_scheduled_item_retry(job_key,item_id,retry_after) VALUES"
                        + " ('team-expiry-sweep',?,?::timestamptz + interval '1 second'),"
                        + " ('team-expiry-sweep',?,?::timestamptz - interval '1 second')",
                id(0),
                NOW.toString(),
                id(30),
                NOW.toString());
        jdbc.update(
                "INSERT INTO sys_scheduled_item_retry(job_key,item_id,retry_after,deleted)"
                        + " VALUES('team-expiry-sweep',?,?::timestamptz + interval '1 hour',true)",
                id(24),
                NOW.toString());
        // An older deadline must sort ahead of UUID order, even when its id is much larger.
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time=?::timestamptz - interval '1 second' WHERE id=?",
                NOW.toString(),
                id(3072));
        List<UUID> expected = new ArrayList<>();
        expected.add(id(3072));
        for (int i = 0; i < 3600 && expected.size() < 500; i++) {
            if ((i & 255) % 6 == 0 && i != 0 && i != 6 && i != 12 && i != 18 && i != 3072) {
                expected.add(id(i));
            }
        }
        try (var session = sessions.openSession()) {
            var repository =
                    new PaidGroupDealRepositoryImpl(session.getMapper(PaidGroupDealMapper.class));
            assertThat(repository.dueForExpiry(NOW, 500, 6, 0))
                    .hasSize(500)
                    .containsExactlyElementsOf(expected)
                    .contains(id(24), id(30));
            assertThat(repository.dueForExpiry(NOW, 500, 1, 0))
                    .containsExactlyElementsOf(repository.dueForExpiry(NOW, 500));
            session.rollback();
        }
    }

    private static void seed(int count) {
        jdbc.update(
                "INSERT INTO biz_group_deal(id,deal_no,product_id,leader_user_id,deal_price,currency,"
                        + "target_size,current_size,status,start_time,expire_time)"
                        + " SELECT ('00000000-0000-7000-8000-' || lpad(to_hex(i),12,'0'))::uuid,"
                        + " 'shard-'||i,uuidv7(),uuidv7(),10,'sgd',2,1,'open',"
                        + " ?::timestamptz - interval '1 hour',?::timestamptz"
                        + " FROM generate_series(0,? - 1) i",
                NOW.toString(),
                NOW.toString(),
                count);
    }

    private static UUID id(int value) {
        return UUID.fromString("00000000-0000-7000-8000-" + String.format("%012x", value));
    }
}
