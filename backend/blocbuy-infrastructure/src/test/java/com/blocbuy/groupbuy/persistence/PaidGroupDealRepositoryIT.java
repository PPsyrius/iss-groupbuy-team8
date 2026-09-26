package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.model.ParticipationResult;
import com.blocbuy.groupbuy.model.StandardPaidGroupDealCreator;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.exceptions.PersistenceException;
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

/**
 * Real PostgreSQL 18 receipt uniqueness, immutable replay, rollback and wall-clock lock protocol.
 */
@Testcontainers
class PaidGroupDealRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    /**
     * Loads V5, V7, V17 and V18 for this isolated groupbuy mapper test with bounded SQL waits. V17
     * supplies retry state read by the due-team query and V18 the member's sharer column; unrelated
     * migrations are covered by the bootstrap integration tests using the full Flyway chain.
     */
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
                        new FileSystemResource(migrations.resolve("V17__scheduled_item_retry.sql")),
                        new FileSystemResource(migrations.resolve("V18__share_attribution.sql")))
                .execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        load(config, "mapper/groupbuy/BizGroupDealMapper.xml");
        load(config, "mapper/groupbuy/PaidGroupDealMapper.xml");
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    /**
     * Verifies soft deletion preserves receipt replay and the permanent unique member-payment
     * binding.
     */
    @Test
    void receiptAndMemberPaymentCannotBeReusedAfterDeletion() {
        var paid = paid();
        UUID dealId = start(paid);
        ParticipationResult result;
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            var receipt = repo.reserveAndLock(paid.paymentId(), "changed-request", paid.userId());
            assertThat(receipt.fingerprint()).isEqualTo("first-request");
            result = receipt.result();
            assertThat(result.currentSize()).isEqualTo(1);
            session.commit();
        }
        jdbc.update(
                "UPDATE biz_group_member SET deleted = true WHERE payment_id = ?",
                paid.paymentId());
        jdbc.update("UPDATE biz_group_deal SET deleted = true WHERE id = ?", dealId);
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThat(
                            repo.reserveAndLock(paid.paymentId(), "first-request", paid.userId())
                                    .result())
                    .isEqualTo(result);
            assertThat(repo.lockDeal(dealId)).isEmpty();

            PaidGroupDealMapper mapper = session.getMapper(PaidGroupDealMapper.class);
            UUID memberId = UuidV7.next();
            UUID paymentId = paid.paymentId();
            UUID userId = UuidV7.next();
            Instant now = repo.currentTime();

            assertThatThrownBy(
                            () ->
                                    mapper.insertMember(
                                            memberId, paymentId, userId, "member", null, now))
                    .isInstanceOf(PersistenceException.class);
            session.rollback();
        }
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT id FROM biz_group_participation_receipt WHERE payment_id = ?",
                                        UUID.class,
                                        paid.paymentId())
                                .version())
                .isEqualTo(7);
    }

    /** Verifies explicit rollback removes all three writes in the paid initiation protocol. */
    @Test
    void rollbackRemovesReceiptDealAndLeaderAtomically() {
        var paid = paid();
        UUID dealId;
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            repo.reserveAndLock(paid.paymentId(), "rollback", paid.userId());
            Instant now = repo.currentTime();
            var deal = new StandardPaidGroupDealCreator().initiate(terms(now), paid, now);
            dealId = deal.id();
            repo.insertStartedDeal(deal, paid, now);
            repo.complete(paid.paymentId(), ParticipationResult.applied(deal), paid.userId(), now);
            session.rollback();
        }
        assertThat(count("SELECT count(*) FROM biz_group_deal WHERE id = ?", dealId)).isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM biz_group_member WHERE payment_id = ?",
                                paid.paymentId()))
                .isZero();
        assertThat(
                        count(
                                "SELECT count(*) FROM biz_group_participation_receipt WHERE payment_id = ?",
                                paid.paymentId()))
                .isZero();
    }

    /**
     * Verifies a competing receipt reservation waits and then observes the original committed
     * outcome.
     */
    @Test
    void duplicateReservationWaitsForCommittedResult() throws Exception {
        var paid = paid();
        try (SqlSession writer = sessions.openSession();
                var executor = Executors.newSingleThreadExecutor()) {
            var repo = repo(writer);
            repo.reserveAndLock(paid.paymentId(), "first-request", paid.userId());
            Instant now = repo.currentTime();
            var deal = new StandardPaidGroupDealCreator().initiate(terms(now), paid, now);
            var result = ParticipationResult.applied(deal);
            var started = new CountDownLatch(1);
            var waiting =
                    executor.submit(
                            () -> {
                                try (SqlSession reader = sessions.openSession()) {
                                    started.countDown();
                                    return repo(reader)
                                            .reserveAndLock(
                                                    paid.paymentId(),
                                                    "other-content",
                                                    paid.userId());
                                }
                            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> waiting.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            repo.insertStartedDeal(deal, paid, now);
            repo.complete(paid.paymentId(), result, paid.userId(), now);
            writer.commit();
            var replay = waiting.get(5, TimeUnit.SECONDS);
            assertThat(replay.fingerprint()).isEqualTo("first-request");
            assertThat(replay.result()).isEqualTo(result);
        }
    }

    /** Advances expiry under a held lock to prove post-lock wall time rejects a stale admission. */
    @Test
    void joinUsesWallClockAfterWaitingForDealLock() throws Exception {
        UUID dealId = start(paid());
        var joiner = paid();
        try (SqlSession writer = sessions.openSession();
                var executor = Executors.newSingleThreadExecutor()) {
            repo(writer).lockDeal(dealId).orElseThrow();
            var started = new CountDownLatch(1);
            var waiting =
                    executor.submit(
                            () -> {
                                try (SqlSession reader = sessions.openSession()) {
                                    var repo = repo(reader);
                                    repo.reserveAndLock(
                                            joiner.paymentId(), "join", joiner.userId());
                                    Instant beforeLock = repo.currentTime();
                                    started.countDown();
                                    var deal = repo.lockDeal(dealId).orElseThrow();
                                    Instant afterLock = repo.currentTime();
                                    assertThat(beforeLock).isBefore(deal.expireTime());
                                    assertThat(afterLock).isAfterOrEqualTo(deal.expireTime());

                                    boolean hasActiveMember =
                                            repo.hasActiveMember(dealId, joiner.userId());

                                    assertThatThrownBy(
                                                    () ->
                                                            deal.joinPaid(
                                                                    joiner,
                                                                    hasActiveMember,
                                                                    afterLock))
                                            .isInstanceOf(BizException.class);
                                    reader.rollback();
                                    return afterLock;
                                }
                            });
            assertThat(started.await(5, TimeUnit.SECONDS)).isTrue();
            assertThatThrownBy(() -> waiting.get(200, TimeUnit.MILLISECONDS))
                    .isInstanceOf(TimeoutException.class);
            // Move the deadline while holding the lock: reader transaction started before this
            // timestamp, but post-lock clock_timestamp must reject joining after it.
            try (var statement =
                    writer.getConnection()
                            .prepareStatement(
                                    "UPDATE biz_group_deal SET expire_time = clock_timestamp() WHERE id = ?")) {
                statement.setObject(1, dealId);
                assertThat(statement.executeUpdate()).isEqualTo(1);
            }
            // Direct JDBC writes do not mark the MyBatis session dirty.
            writer.commit(true);
            assertThat(waiting.get(5, TimeUnit.SECONDS)).isNotNull();
        }
        assertThat(count("SELECT count(*) FROM biz_group_member WHERE deal_id = ?", dealId))
                .isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_size FROM biz_group_deal WHERE id = ?",
                                Integer.class,
                                dealId))
                .isEqualTo(1);
    }

    /**
     * The due-list query and the status-guarded expiry UPDATE against real SQL: the deadline
     * comparison is inclusive, resolved teams leave the queue, and a team another writer already
     * moved matches nothing.
     */
    @Test
    void dueListAndExpiryUpdateAgreeOnWhatIsStillOpen() {
        var paid = paid();
        UUID dealId = start(paid);
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time = clock_timestamp() - interval '1 minute'"
                        + " WHERE id = ?",
                dealId);

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            Instant now = repo.currentTime();
            assertThat(repo.dueForExpiry(now, 10)).contains(dealId);
            assertThat(repo.dueForExpiry(now.minusSeconds(3600), 10)).doesNotContain(dealId);

            var before = repo.lockDeal(dealId).orElseThrow();
            repo.expire(before, before.expire(now), now);
            session.commit();
        }

        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM biz_group_deal WHERE id = ?",
                                String.class,
                                dealId))
                .isEqualTo("expired");
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            Instant now = repo.currentTime();
            assertThat(repo.dueForExpiry(now, 10)).doesNotContain(dealId);
            session.rollback();
        }
    }

    /** A failed oldest team is deferred without hiding newer due teams, then retried when due. */
    @Test
    void retryBackoffExcludesOnlyTheDeferredTeamUntilItsDeadline() {
        UUID deferred = start(paid());
        UUID next = start(paid());
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time = clock_timestamp() - interval '2 minutes'"
                        + " WHERE id = ?",
                deferred);
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time = clock_timestamp() - interval '1 minute'"
                        + " WHERE id = ?",
                next);

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThat(repo.dueForExpiry(repo.currentTime(), 1)).containsExactly(deferred);
            repo.deferExpiryRetry(deferred, Duration.ofMinutes(5));
            session.commit();
        }

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThat(repo.dueForExpiry(repo.currentTime(), 1)).containsExactly(next);
            session.rollback();
        }

        jdbc.update(
                "UPDATE sys_scheduled_item_retry SET retry_after = clock_timestamp()"
                        + " - interval '1 second' WHERE item_id = ?",
                deferred);
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThat(repo.dueForExpiry(repo.currentTime(), 1)).containsExactly(deferred);
            session.rollback();
        }
    }

    /** A team that is already closed matches the status guard nowhere, so the write is refused. */
    @Test
    void expiryRefusesATeamThatIsNoLongerOpen() {
        var paid = paid();
        UUID dealId = start(paid);
        jdbc.update(
                "UPDATE biz_group_deal SET expire_time = clock_timestamp() - interval '1 minute'"
                        + " WHERE id = ?",
                dealId);
        com.blocbuy.groupbuy.model.GroupDeal before;
        Instant now;
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            before = repo.lockDeal(dealId).orElseThrow();
            now = repo.currentTime();
            session.commit();
        }
        // The webhook that filled this team wins the race while the sweeper is between its read
        // and its write; no lock is held here, so the two writers really do overlap.
        jdbc.update("UPDATE biz_group_deal SET status = 'succeeded' WHERE id = ?", dealId);

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            var after = before.expire(now);

            assertThatThrownBy(() -> repo.expire(before, after, now))
                    .isInstanceOf(BizException.class);
            session.rollback();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM biz_group_deal WHERE id = ?",
                                String.class,
                                dealId))
                .isEqualTo("succeeded");
    }

    /** Commits a complete paid-leader fixture with its receipt in one SQL session. */
    private UUID start(PaidParticipation paid) {
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            repo.reserveAndLock(paid.paymentId(), "first-request", paid.userId());
            Instant now = repo.currentTime();
            var deal = new StandardPaidGroupDealCreator().initiate(terms(now), paid, now);
            repo.insertStartedDeal(deal, paid, now);
            repo.complete(paid.paymentId(), ParticipationResult.applied(deal), paid.userId(), now);
            session.commit();
            return deal.id();
        }
    }

    /** Supplies unique two-person SGD terms using the caller's database time for expiry. */
    private static NewDealTerms terms(Instant now) {
        UUID id = UuidV7.next();
        return new NewDealTerms(
                id,
                "GROUP-" + id,
                UuidV7.next(),
                new BigDecimal("12.00"),
                "sgd",
                2,
                now.plusSeconds(60));
    }

    /** Supplies distinct UUIDv7 buyer and payment facts without invoking any payment provider. */
    private static PaidParticipation paid() {
        return new PaidParticipation(UuidV7.next(), UuidV7.next(), new BigDecimal("12.00"), "sgd");
    }

    /** Binds the repository to the caller's SQL session so its writes share that transaction. */
    private static PaidGroupDealRepositoryImpl repo(SqlSession session) {
        return new PaidGroupDealRepositoryImpl(session.getMapper(PaidGroupDealMapper.class));
    }

    /** Counts fixture rows by UUID to verify rollback independently of repository mapping. */
    private static int count(String query, UUID id) {
        return jdbc.queryForObject(query, Integer.class, id);
    }

    /** Loads mapper XML and shared SQL fragments into the isolated MyBatis configuration. */
    private static void load(Configuration config, String resource) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
    }
}
