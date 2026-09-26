package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.model.ShareCredit;
import com.blocbuy.groupbuy.model.ShareInvite;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
import org.apache.ibatis.session.SqlSessionFactory;
import org.apache.ibatis.session.SqlSessionFactoryBuilder;
import org.apache.ibatis.transaction.jdbc.JdbcTransactionFactory;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.FileSystemResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real PostgreSQL 18 idempotency of UC7 invites and credits (V18 partial unique indexes). */
@Testcontainers
class ShareAttributionRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final SecureRandom RANDOM = new SecureRandom();

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    /** Loads V5, V7 and V18 only; the full Flyway chain is covered by bootstrap tests. */
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
                        new FileSystemResource(
                                migrations.resolve("V7__groupbuy_paid_participation.sql")),
                        new FileSystemResource(migrations.resolve("V18__share_attribution.sql")))
                .execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        load(config, "mapper/groupbuy/BizShareInviteMapper.xml");
        load(config, "mapper/groupbuy/BizShareCreditMapper.xml");
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    @Test
    void anInviteIsFoundByItsReferenceAndByItsTeamAndSharer() {
        ShareInvite invite = ShareInvite.mint(UuidV7.next(), UuidV7.next(), RANDOM);
        try (SqlSession session = sessions.openSession()) {
            assertThat(invites(session).insertIfAbsent(invite)).isTrue();
            session.commit();
        }
        try (SqlSession session = sessions.openSession()) {
            var repo = invites(session);
            assertThat(repo.findByRef(invite.ref())).contains(invite);
            assertThat(repo.findByDealAndSharer(invite.dealId(), invite.sharerUserId()))
                    .contains(invite);
            assertThat(repo.findByRef("AAAAAAAAAAAAAAAAAAAAAA")).isEmpty();
        }
    }

    @Test
    void aSoftDeletedInviteNoLongerResolves() {
        ShareInvite invite = ShareInvite.mint(UuidV7.next(), UuidV7.next(), RANDOM);
        try (SqlSession session = sessions.openSession()) {
            invites(session).insertIfAbsent(invite);
            session.commit();
        }
        jdbc.update("UPDATE biz_share_invite SET deleted = true WHERE id = ?", invite.id());
        try (SqlSession session = sessions.openSession()) {
            assertThat(invites(session).findByRef(invite.ref())).isEmpty();
        }
    }

    /**
     * Two concurrent first mints by one sharer: the second insert waits for the first to commit,
     * then does nothing, and both read back the same single row.
     */
    @Test
    void concurrentMintsBySameSharerLeaveOneInvite() throws Exception {
        UUID deal = UuidV7.next();
        UUID sharer = UuidV7.next();
        ShareInvite first = ShareInvite.mint(deal, sharer, RANDOM);
        ShareInvite second = ShareInvite.mint(deal, sharer, RANDOM);
        var executor = Executors.newSingleThreadExecutor();
        try (SqlSession holder = sessions.openSession()) {
            assertThat(invites(holder).insertIfAbsent(first)).isTrue();
            var racer =
                    executor.submit(
                            () -> {
                                try (SqlSession session = sessions.openSession()) {
                                    boolean inserted = invites(session).insertIfAbsent(second);
                                    session.commit();
                                    return inserted;
                                }
                            });
            // Still blocked on the holder's uncommitted row, not finished early.
            assertThatThrownBy(() -> racer.get(300, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            holder.commit();
            assertThat(racer.get(5, TimeUnit.SECONDS)).isFalse();
        } finally {
            executor.shutdownNow();
        }
        try (SqlSession session = sessions.openSession()) {
            assertThat(invites(session).findByDealAndSharer(deal, sharer)).contains(first);
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_share_invite WHERE deal_id = ?",
                                Integer.class,
                                deal))
                .isEqualTo(1);
    }

    @Test
    void aReferenceCollisionIsANoOpRatherThanAFailure() {
        ShareInvite taken = ShareInvite.mint(UuidV7.next(), UuidV7.next(), RANDOM);
        ShareInvite clash =
                new ShareInvite(UuidV7.next(), taken.ref(), UuidV7.next(), UuidV7.next());
        try (SqlSession session = sessions.openSession()) {
            var repo = invites(session);
            assertThat(repo.insertIfAbsent(taken)).isTrue();
            assertThat(repo.insertIfAbsent(clash)).isFalse();
            session.commit();
        }
    }

    /** One credit per participation: a duplicate delivery writes nothing and fails nothing. */
    @Test
    void aParticipationIsCreditedOnce() {
        var credit =
                new ShareCredit(
                        UuidV7.next(),
                        UuidV7.next(),
                        UuidV7.next(),
                        UuidV7.next(),
                        Instant.parse("2026-09-25T10:00:00Z"));
        try (SqlSession session = sessions.openSession()) {
            var repo = new ShareCreditRepositoryImpl(session.getMapper(BizShareCreditMapper.class));
            assertThat(repo.recordIfAbsent(credit)).isTrue();
            assertThat(repo.recordIfAbsent(credit)).isFalse();
            session.commit();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT sharer_user_id FROM biz_share_credit WHERE payment_id = ?",
                                UUID.class,
                                credit.paymentId()))
                .isEqualTo(credit.sharerUserId());
    }

    private static ShareInviteRepositoryImpl invites(SqlSession session) {
        return new ShareInviteRepositoryImpl(session.getMapper(BizShareInviteMapper.class));
    }

    private static void load(Configuration config, String resource) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
    }
}
