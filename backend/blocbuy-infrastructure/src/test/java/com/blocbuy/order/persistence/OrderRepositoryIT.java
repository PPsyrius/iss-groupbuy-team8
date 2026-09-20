package com.blocbuy.order.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.order.exception.OrderErrorCode;
import com.blocbuy.order.model.Order;
import com.blocbuy.order.model.OrderStatus;
import com.blocbuy.order.repository.OrderRepository;
import java.io.InputStream;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.Instant;
import java.util.UUID;
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
 * Real PostgreSQL 18 order guarantees: unique keys (US19), version compare-and-swap (US20), and
 * status-guarded idempotent deal sweeps. biz_order has no cross-table references, so V9 alone
 * migrates this isolated mapper test; the bootstrap chain exercises the full V1–V11 set.
 */
@Testcontainers
class OrderRepositoryIT {
    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

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
        new ResourceDatabasePopulator(new FileSystemResource(migrations.resolve("V9__order.sql")))
                .execute(source);
        var config =
                new Configuration(new Environment("test", new JdbcTransactionFactory(), source));
        config.setDefaultStatementTimeout(5);
        config.getTypeHandlerRegistry().register(UuidTypeHandler.class);
        load(config, "mapper/order/BizOrderMapper.xml");
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    /** Verifies the save/read round-trip through both key lookups. */
    @Test
    void savePersistsSnapshotReadableByIdAndPayment() {
        Order order = Order.create(terms("12.00"), paidAt());
        save(order);

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            Order byId = repo.findById(order.id()).orElseThrow();
            Order byPayment = repo.findByPaymentId(order.paymentId()).orElseThrow();
            assertThat(byId.id()).isEqualTo(order.id());
            assertThat(byId.orderNo()).isEqualTo(order.orderNo());
            assertThat(byId.dealId()).isEqualTo(order.dealId());
            assertThat(byId.userId()).isEqualTo(order.userId());
            assertThat(byId.productTitle()).isEqualTo(order.productTitle());
            assertThat(byId.quantity()).isEqualTo(order.quantity());
            assertThat(byId.totalAmount()).isEqualByComparingTo(order.totalAmount());
            assertThat(byId.status()).isEqualTo(order.status());
            assertThat(byId.version()).isEqualTo(order.version());
            assertThat(byPayment.id()).isEqualTo(order.id());
        }
    }

    /** US19 core: a replayed payment confirmation collides on uq_biz_order_payment_id. */
    @Test
    void duplicatePaymentIsRejectedAtTheDatabase() {
        Order.NewOrderTerms terms = terms("12.00");
        save(Order.create(terms, paidAt()));
        Order replay = Order.create(terms, paidAt());

        assertThatThrownBy(() -> save(replay)).isInstanceOf(PersistenceException.class);
    }

    /** Mirrors uq_biz_group_member_deal_user: no second active order per user per deal. */
    @Test
    void duplicateDealUserIsRejectedAtTheDatabase() {
        Order.NewOrderTerms first = terms("12.00");
        Order.NewOrderTerms second =
                new Order.NewOrderTerms(
                        "ORDER-" + UUID.randomUUID(),
                        UUID.randomUUID(),
                        first.dealId(),
                        first.userId(),
                        UUID.randomUUID(),
                        "Mechanical keyboard",
                        null,
                        1,
                        new BigDecimal("12.00"),
                        "sgd");
        save(Order.create(first, paidAt()));
        Order duplicate = Order.create(second, paidAt());

        assertThatThrownBy(() -> save(duplicate)).isInstanceOf(PersistenceException.class);
    }

    /** Business order numbers are unique among active rows. */
    @Test
    void duplicateOrderNoIsRejectedAtTheDatabase() {
        Order.NewOrderTerms first = terms("12.00");
        Order.NewOrderTerms second =
                new Order.NewOrderTerms(
                        first.orderNo(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        first.userId(),
                        UUID.randomUUID(),
                        "Mechanical keyboard",
                        null,
                        1,
                        new BigDecimal("15.00"),
                        "sgd");
        save(Order.create(first, paidAt()));
        Order duplicate = Order.create(second, paidAt());

        assertThatThrownBy(() -> save(duplicate)).isInstanceOf(PersistenceException.class);
    }

    /** US20: stale compare-and-swap never overwrites; the winner's write is visible. */
    @Test
    void saveIfVersionRejectsStaleWriteButAcceptsFreshOne() {
        Order order = Order.create(terms("12.00"), paidAt());
        save(order);
        Order confirmed;
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            confirmed = repo.saveIfVersion(repo.findById(order.id()).orElseThrow().confirm());
            session.commit();
        }
        assertThat(confirmed.status().code()).isEqualTo("confirmed");
        assertThat(confirmed.version()).isEqualTo(1);

        Order staleSnapshotAtVersionZero =
                Order.reconstitute(
                        new Order.NewOrderTerms(
                                order.orderNo(),
                                order.paymentId(),
                                order.dealId(),
                                order.userId(),
                                order.productId(),
                                order.productTitle(),
                                order.productImage(),
                                order.quantity(),
                                order.unitPrice(),
                                order.currency()),
                        order.id(),
                        order.totalAmount(),
                        new Order.Lifecycle(
                                OrderStatus.PENDING,
                                order.paidTime(),
                                null,
                                order.createTime(),
                                0));
        Order staleCancel = staleSnapshotAtVersionZero.cancel(paidAt());
        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThatThrownBy(() -> repo.saveIfVersion(staleCancel))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining(OrderErrorCode.ORDER_VERSION_CONFLICT.code());
            session.rollback();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM biz_order WHERE id = ?",
                                String.class,
                                order.id()))
                .isEqualTo("confirmed");
    }

    /** A vanished row is a missing order, not a conflict. */
    @Test
    void saveIfVersionOnMissingRowIsOrderNotFound() {
        Order ghost = Order.create(terms("12.00"), paidAt());
        Order ghostCancel = ghost.cancel(paidAt());

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThatThrownBy(() -> repo.saveIfVersion(ghostCancel))
                    .isInstanceOf(BizException.class)
                    .hasMessageContaining(OrderErrorCode.ORDER_NOT_FOUND.code());
            session.rollback();
        }
    }

    /** TeamSucceeded sweep: both members' orders confirm once; a replay is a zero-row no-op. */
    @Test
    void confirmByDealIdIsStatusGuardedAndIdempotent() {
        Order.NewOrderTerms first = terms("12.00");
        Order.NewOrderTerms second =
                new Order.NewOrderTerms(
                        "ORDER-" + UUID.randomUUID(),
                        UUID.randomUUID(),
                        first.dealId(),
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        "Mechanical keyboard",
                        null,
                        1,
                        new BigDecimal("12.00"),
                        "sgd");
        save(Order.create(first, paidAt()));
        save(Order.create(second, paidAt()));

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThat(repo.confirmByDealId(first.dealId())).isEqualTo(2);
            assertThat(repo.confirmByDealId(first.dealId())).isZero();
            assertThat(repo.confirmByDealId(UUID.randomUUID())).isZero();
            session.commit();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM biz_order WHERE deal_id = ? AND status = 'confirmed'",
                                Integer.class,
                                first.dealId()))
                .isEqualTo(2);
    }

    /** TeamExpired sweep: pending orders cancel once with the durable instant, replays no-op. */
    @Test
    void cancelByDealIdStampsInstantAndIsIdempotent() {
        Order order = Order.create(terms("12.00"), paidAt());
        save(order);
        Instant expiredAt = paidAt().plusSeconds(3600);

        try (SqlSession session = sessions.openSession()) {
            var repo = repo(session);
            assertThat(repo.cancelByDealId(order.dealId(), expiredAt)).isEqualTo(1);
            assertThat(repo.cancelByDealId(order.dealId(), expiredAt)).isZero();
            session.commit();
        }
        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM biz_order WHERE id = ?",
                                String.class,
                                order.id()))
                .isEqualTo("cancelled");
        assertThat(
                        jdbc.queryForObject(
                                        "SELECT cancel_time FROM biz_order WHERE id = ?",
                                        java.sql.Timestamp.class,
                                        order.id())
                                .toInstant())
                .isEqualTo(expiredAt);
    }

    /** A cancelled order can never be confirmed afterwards — the guard is on status, not luck. */
    @Test
    void confirmByDealIdSkipsCancelledOrders() {
        Order order = Order.create(terms("12.00"), paidAt());
        save(order);
        try (SqlSession session = sessions.openSession()) {
            repo(session).cancelByDealId(order.dealId(), paidAt().plusSeconds(60));
            session.commit();
        }

        try (SqlSession session = sessions.openSession()) {
            assertThat(repo(session).confirmByDealId(order.dealId())).isZero();
            session.commit();
        }
    }

    /** My Orders is owner-scoped, newest first, with an accurate total. */
    @Test
    void pageByUserIdScopesOwnerAndSortsNewestFirst() {
        UUID buyer = UUID.randomUUID();
        Order older = Order.create(termsFor(buyer, "12.00"), paidAt());
        Order newer = Order.create(termsFor(buyer, "15.00"), paidAt().plusSeconds(300));
        Order otherBuyer = Order.create(terms("20.00"), paidAt());
        save(older);
        save(newer);
        save(otherBuyer);

        PageResult<Order> page;
        try (SqlSession session = sessions.openSession()) {
            page = repo(session).pageByUserId(PageQuery.of(1, 10), buyer);
            session.commit();
        }
        assertThat(page.total()).isEqualTo(2);
        assertThat(page.items()).extracting(Order::id).containsExactly(newer.id(), older.id());

        PageResult<Order> firstOnly;
        try (SqlSession session = sessions.openSession()) {
            firstOnly = repo(session).pageByUserId(PageQuery.of(2, 1), buyer);
            session.commit();
        }
        assertThat(firstOnly.total()).isEqualTo(2);
        assertThat(firstOnly.items()).extracting(Order::id).containsExactly(older.id());
    }

    /** Commits one order inside its own SQL session, mirroring the participation transaction. */
    private static void save(Order order) {
        try (SqlSession session = sessions.openSession()) {
            repo(session).save(order);
            session.commit();
        }
    }

    /** Binds the repository to the caller's SQL session so writes share that transaction. */
    private static OrderRepository repo(SqlSession session) {
        return new OrderRepositoryImpl(session.getMapper(BizOrderMapper.class));
    }

    /** Supplies fresh deal/user/product/payment references per call. */
    private static Order.NewOrderTerms terms(String unitPrice) {
        return termsFor(UUID.randomUUID(), unitPrice);
    }

    private static Order.NewOrderTerms termsFor(UUID buyer, String unitPrice) {
        return new Order.NewOrderTerms(
                "ORDER-" + UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                buyer,
                UUID.randomUUID(),
                "Mechanical keyboard",
                "https://img.example/keyboard.png",
                1,
                new BigDecimal(unitPrice),
                "sgd");
    }

    /** Deterministic placement instants keep newest-first assertions meaningful. */
    private static Instant paidAt() {
        return Instant.parse("2026-09-20T10:00:00Z");
    }

    /** Loads the mapper XML into the isolated MyBatis configuration. */
    private static void load(Configuration config, String resource) throws Exception {
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
    }
}
