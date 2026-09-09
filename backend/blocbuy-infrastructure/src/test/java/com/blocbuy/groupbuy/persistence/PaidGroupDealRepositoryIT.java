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
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * Real PostgreSQL 18 receipt uniqueness, immutable replay, rollback and wall-clock lock protocol.
 */
@Testcontainers
class PaidGroupDealRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:18");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

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
                                migrations.resolve("V7__groupbuy_paid_participation.sql")))
                .execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        load(config, "mapper/groupbuy/BizGroupDealMapper.xml");
        load(config, "mapper/groupbuy/PaidGroupDealMapper.xml");
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

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
            assertThatThrownBy(
                            () ->
                                    session.getMapper(PaidGroupDealMapper.class)
                                            .insertMember(
                                                    UuidV7.next(),
                                                    paid.paymentId(),
                                                    UuidV7.next(),
                                                    "member",
                                                    repo.currentTime()))
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
                                    assertThatThrownBy(
                                                    () ->
                                                            deal.joinPaid(
                                                                    joiner,
                                                                    repo.hasActiveMember(
                                                                            dealId,
                                                                            joiner.userId()),
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

    private static PaidParticipation paid() {
        return new PaidParticipation(UuidV7.next(), UuidV7.next(), new BigDecimal("12.00"), "sgd");
    }

    private static PaidGroupDealRepositoryImpl repo(SqlSession session) {
        return new PaidGroupDealRepositoryImpl(session.getMapper(PaidGroupDealMapper.class));
    }

    private static int count(String query, UUID id) {
        return jdbc.queryForObject(query, Integer.class, id);
    }

    private static void load(Configuration config, String resource) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
    }
}
