package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidParticipation;
import com.blocbuy.groupbuy.service.PaidGroupDealService;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.postgresql.util.PSQLException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Real application proxy + MyBatis + Flyway. Payment facts are test fixtures, not Stripe proof. */
@SpringBootTest(
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "spring.flyway.locations=classpath:db/migration"
        })
@Testcontainers
@Import(TeamPriceControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class PaidGroupDealServiceIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    /**
     * Connects the Spring context and full Flyway migration chain to the container's domain schema.
     */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private PaidGroupDealService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactions;

    /**
     * Verifies real proxy transactions persist success while initiation replay retains its earlier
     * count.
     */
    @Test
    void realStartAndJoinPreserveReplaySnapshot() {
        var terms = terms();
        var leader = payment();
        var start = service.startConfirmed(terms, leader);
        assertThat(start.currentSize()).isEqualTo(1);
        assertThat(service.joinConfirmed(terms.id(), payment()).dealStatus())
                .isEqualTo("succeeded");
        assertThat(service.startConfirmed(terms, leader)).isEqualTo(start);
        assertThat(memberCount(terms.id())).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_size FROM biz_group_deal WHERE id = ?",
                                Integer.class,
                                terms.id()))
                .isEqualTo(2);
    }

    /** Verifies caller rollback removes receipt and membership too, allowing a genuine retry. */
    @Test
    void surroundingPaymentTransactionRollbackLeavesNoGroupOrMember() {
        var terms = terms();
        var payment = payment();
        var transaction = new TransactionTemplate(transactions);
        assertThatThrownBy(
                        () ->
                                transaction.executeWithoutResult(
                                        status -> {
                                            service.startConfirmed(terms, payment);
                                            throw new IllegalStateException(
                                                    "simulated caller failure after group persistence");
                                        }))
                .isInstanceOf(IllegalStateException.class);
        assertThat(memberCount(terms.id())).isZero();
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_deal WHERE id = ?",
                                Long.class,
                                terms.id()))
                .isZero();
        // The receipt must roll back too: retry applies a real group, not an orphaned success
        // receipt.
        assertThat(service.startConfirmed(terms, payment).outcome()).isEqualTo("applied");
        assertThat(memberCount(terms.id())).isEqualTo(1);
    }

    /**
     * Races distinct payments for one buyer or the last place; exactly one admission may commit.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void concurrentPaymentsNeverDuplicateBuyerOrExceedCapacity(boolean sameBuyer) throws Exception {
        var initial = terms();
        var terms =
                new NewDealTerms(
                        initial.id(),
                        initial.dealNo(),
                        initial.productId(),
                        initial.price(),
                        initial.currency(),
                        sameBuyer ? 3 : 2,
                        initial.expireTime());
        service.startConfirmed(terms, payment());
        var first = payment();
        var second =
                sameBuyer
                        ? new PaidParticipation(
                                UuidV7.next(), first.userId(), first.amount(), first.currency())
                        : payment();
        var ready = new CountDownLatch(2);
        var start = new CountDownLatch(1);
        try (var executor = Executors.newFixedThreadPool(2)) {
            var a =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                if (!start.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("start timed out");
                                }
                                return service.joinConfirmed(terms.id(), first);
                            });
            var b =
                    executor.submit(
                            () -> {
                                ready.countDown();
                                if (!start.await(10, TimeUnit.SECONDS)) {
                                    throw new IllegalStateException("start timed out");
                                }
                                return service.joinConfirmed(terms.id(), second);
                            });
            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            start.countDown();
            var resultA = a.get(20, TimeUnit.SECONDS);
            var resultB = b.get(20, TimeUnit.SECONDS);
            assertThat(java.util.List.of(resultA.outcome(), resultB.outcome()))
                    .containsExactlyInAnyOrder("applied", "compensation_required");
            assertThat(service.joinConfirmed(terms.id(), first)).isEqualTo(resultA);
            assertThat(service.joinConfirmed(terms.id(), second)).isEqualTo(resultB);
        } finally {
            start.countDown();
        }
        assertThat(memberCount(terms.id())).isEqualTo(2);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT current_size FROM biz_group_deal WHERE id = ?",
                                Integer.class,
                                terms.id()))
                .isEqualTo(2);
    }

    /**
     * Holds a receipt or deal lock to prove database cancellation, rollback and successful retry.
     */
    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void stalledLockTimesOutWithoutLeavingPartialParticipation(boolean receiptLock)
            throws Exception {
        var terms = terms();
        var leader = payment();
        var original = service.startConfirmed(terms, leader);
        var joiner = payment();
        try (var holder = jdbc.getDataSource().getConnection()) {
            holder.setAutoCommit(false);
            String sql =
                    receiptLock
                            ? "SELECT payment_id FROM biz_group_participation_receipt WHERE payment_id = ? FOR UPDATE"
                            : "SELECT id FROM biz_group_deal WHERE id = ? FOR UPDATE";
            try (var statement = holder.prepareStatement(sql)) {
                statement.setObject(1, receiptLock ? leader.paymentId() : terms.id());
                try (var rows = statement.executeQuery()) {
                    assertThat(rows.next()).isTrue();
                }
            }
            try (var executor = Executors.newSingleThreadExecutor()) {
                var waiting =
                        executor.submit(
                                () ->
                                        receiptLock
                                                ? service.startConfirmed(terms, leader)
                                                : service.joinConfirmed(terms.id(), joiner));
                try {
                    // Must be database cancellation, not this test's Future timeout.
                    assertThatThrownBy(() -> waiting.get(20, TimeUnit.SECONDS))
                            .isInstanceOf(ExecutionException.class)
                            .hasRootCauseInstanceOf(PSQLException.class)
                            .satisfies(
                                    error ->
                                            assertThat(
                                                            ((PSQLException)
                                                                            org.springframework.core
                                                                                    .NestedExceptionUtils
                                                                                    .getMostSpecificCause(
                                                                                            error))
                                                                    .getSQLState())
                                                    .isEqualTo("57014"));
                } finally {
                    // Release before ExecutorService.close(), including when the assertion fails.
                    holder.rollback();
                }
            }
        }
        assertThat(memberCount(terms.id())).isEqualTo(1);
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_group_participation_receipt WHERE payment_id = ?",
                                Long.class,
                                joiner.paymentId()))
                .isZero();
        assertThat(service.startConfirmed(terms, leader)).isEqualTo(original);
        assertThat(service.joinConfirmed(terms.id(), joiner).currentSize()).isEqualTo(2);
    }

    /**
     * Reads active membership directly to verify committed effects independently of service
     * results.
     */
    private long memberCount(UUID dealId) {
        return jdbc.queryForObject(
                "SELECT count(*) FROM biz_group_member WHERE deal_id = ? AND deleted = false",
                Long.class,
                dealId);
    }

    /**
     * Inserts an on-sale product fixture and supplies accepted two-person terms with ample
     * lifetime.
     */
    private NewDealTerms terms() {
        UUID id = UuidV7.next();
        UUID product = UuidV7.next();
        jdbc.update(
                "INSERT INTO biz_product (id, product_no, title, original_price, currency, stock, status) VALUES (?, ?, 'Paid deal test', 20, 'sgd', 10, 'on_sale')",
                product,
                "IT-" + product);
        return new NewDealTerms(
                id,
                "DEAL-" + id,
                product,
                new BigDecimal("10.00"),
                "sgd",
                2,
                Instant.now().plusSeconds(600));
    }

    /**
     * Inserts an active buyer and supplies payment facts as a fixture, without contacting Stripe.
     */
    private PaidParticipation payment() {
        UUID buyer = UuidV7.next();
        jdbc.update(
                "INSERT INTO sys_user (id, register_source, status) VALUES (?, 'google', 'active')",
                buyer);
        return new PaidParticipation(UuidV7.next(), buyer, new BigDecimal("10.00"), "sgd");
    }
}
