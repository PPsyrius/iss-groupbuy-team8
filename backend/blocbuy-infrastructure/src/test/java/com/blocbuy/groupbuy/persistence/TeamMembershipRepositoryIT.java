package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.persistence.UuidTypeHandler;
import com.blocbuy.groupbuy.model.DealStatus;
import com.blocbuy.groupbuy.model.MemberRole;
import com.blocbuy.groupbuy.model.TeamMembership;
import java.io.InputStream;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.Environment;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.SqlSession;
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
 * Real PostgreSQL coverage for the UC8 My Teams read: owner scoping, leader and member rows,
 * soft-delete exclusion on both tables, stable newest-first order, and a count that always matches
 * the page predicates.
 */
@Testcontainers
class TeamMembershipRepositoryIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    private static final UUID BUYER = UUID.fromString("c0000000-0000-7000-8000-000000000001");
    private static final UUID OTHER = UUID.fromString("c0000000-0000-7000-8000-000000000002");
    private static final UUID PRODUCT = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID LED_DEAL = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final UUID JOINED_DEAL = UUID.fromString("b0000000-0000-7000-8000-000000000002");
    private static final UUID TIE_DEAL = UUID.fromString("b0000000-0000-7000-8000-000000000003");
    private static final UUID DELETED_DEAL =
            UUID.fromString("b0000000-0000-7000-8000-000000000004");
    private static final UUID LEFT_DEAL = UUID.fromString("b0000000-0000-7000-8000-000000000005");
    private static final UUID TIE_LOW_MEMBER =
            UUID.fromString("d0000000-0000-7000-8000-000000000003");
    private static final UUID TIE_HIGH_MEMBER =
            UUID.fromString("d0000000-0000-7000-8000-000000000009");
    private static final UUID ORDER = UUID.fromString("e0000000-0000-7000-8000-000000000001");
    private static final Instant SAME_JOIN = Instant.parse("2026-09-20T08:00:00Z");

    private static SqlSessionFactory sessions;
    private static JdbcTemplate jdbc;

    /** Loads V5 and V7 for this isolated groupbuy mapper test with bounded SQL waits. */
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
        String resource = "mapper/groupbuy/BizGroupDealMapper.xml";
        try (InputStream input = new ClassPathResource(resource).getInputStream()) {
            new XMLMapperBuilder(input, config, resource, config.getSqlFragments()).parse();
        }
        sessions = new SqlSessionFactoryBuilder().build(config);
    }

    /**
     * The buyer leads one team, joined three (two sharing a join instant), left one (soft-deleted
     * membership), and joined one whose team was soft-deleted. Another buyer shares two teams.
     */
    @BeforeEach
    void fixtures() {
        jdbc.update("DELETE FROM biz_group_member");
        jdbc.update("DELETE FROM biz_group_deal");
        deal(LED_DEAL, "succeeded", 3, Instant.parse("2026-09-20T12:00:00Z"), false);
        deal(JOINED_DEAL, "open", 1, null, false);
        deal(TIE_DEAL, "expired", 2, null, false);
        deal(DELETED_DEAL, "open", 1, null, true);
        deal(LEFT_DEAL, "open", 1, null, false);
        member(
                UUID.fromString("d0000000-0000-7000-8000-000000000001"),
                LED_DEAL,
                BUYER,
                "leader",
                Instant.parse("2026-09-20T10:00:00Z"),
                ORDER,
                false);
        member(
                UUID.fromString("d0000000-0000-7000-8000-000000000002"),
                JOINED_DEAL,
                BUYER,
                "member",
                Instant.parse("2026-09-20T09:00:00Z"),
                null,
                false);
        member(TIE_LOW_MEMBER, TIE_DEAL, BUYER, "member", SAME_JOIN, null, false);
        member(TIE_HIGH_MEMBER, DELETED_DEAL, BUYER, "member", SAME_JOIN, null, false);
        member(
                UUID.fromString("d0000000-0000-7000-8000-000000000005"),
                LEFT_DEAL,
                BUYER,
                "member",
                Instant.parse("2026-09-20T11:00:00Z"),
                null,
                true);
        member(
                UUID.fromString("d0000000-0000-7000-8000-000000000006"),
                LED_DEAL,
                OTHER,
                "member",
                Instant.parse("2026-09-20T11:30:00Z"),
                null,
                false);
        member(
                UUID.fromString("d0000000-0000-7000-8000-000000000007"),
                TIE_DEAL,
                OTHER,
                "leader",
                Instant.parse("2026-09-20T07:00:00Z"),
                null,
                false);
    }

    @Test
    void returnsOnlyTheBuyersLiveTeamsNewestFirstWithCommittedStatus() {
        try (SqlSession session = sessions.openSession()) {
            PageResult<TeamMembership> page =
                    repository(session).pageMembershipsByUserId(new PageQuery(1, 20), BUYER);

            assertThat(page.total()).isEqualTo(3);
            assertThat(page.items())
                    .extracting(
                            TeamMembership::dealId,
                            TeamMembership::role,
                            TeamMembership::status,
                            TeamMembership::orderId)
                    .containsExactly(
                            tuple(LED_DEAL, MemberRole.LEADER, DealStatus.SUCCEEDED, ORDER),
                            tuple(JOINED_DEAL, MemberRole.MEMBER, DealStatus.OPEN, null),
                            tuple(TIE_DEAL, MemberRole.MEMBER, DealStatus.EXPIRED, null));
            TeamMembership led = page.items().getFirst();
            assertThat(led.productId()).isEqualTo(PRODUCT);
            assertThat(led.dealPrice()).isEqualByComparingTo("12.50");
            assertThat(led.currency()).isEqualTo("sgd");
            assertThat(led.targetSize()).isEqualTo(3);
            assertThat(led.currentSize()).isEqualTo(3);
            assertThat(led.expireTime()).isEqualTo(Instant.parse("2026-09-21T10:00:00Z"));
            assertThat(led.successTime()).isEqualTo(Instant.parse("2026-09-20T12:00:00Z"));
            assertThat(led.joinTime()).isEqualTo(Instant.parse("2026-09-20T10:00:00Z"));
        }
    }

    /** Equal join instants fall back to member id descending, so pages never overlap or skip. */
    @Test
    void equalJoinTimesUseDescendingMemberIdTieBreaker() {
        jdbc.update("UPDATE biz_group_deal SET deleted = false WHERE id = ?", DELETED_DEAL);
        try (SqlSession session = sessions.openSession()) {
            PageResult<TeamMembership> page =
                    repository(session).pageMembershipsByUserId(new PageQuery(1, 20), BUYER);

            assertThat(page.items())
                    .extracting(TeamMembership::dealId)
                    .containsExactly(LED_DEAL, JOINED_DEAL, DELETED_DEAL, TIE_DEAL);
        }
    }

    @Test
    void offsetPagesAgreeWithTheCount() {
        try (SqlSession session = sessions.openSession()) {
            GroupDealRepositoryImpl repository = repository(session);

            PageResult<TeamMembership> second =
                    repository.pageMembershipsByUserId(new PageQuery(2, 2), BUYER);
            PageResult<TeamMembership> beyond =
                    repository.pageMembershipsByUserId(new PageQuery(3, 2), BUYER);

            assertThat(second.total()).isEqualTo(3);
            assertThat(second.items()).extracting(TeamMembership::dealId).containsExactly(TIE_DEAL);
            assertThat(second.hasNext()).isFalse();
            assertThat(beyond.items()).isEmpty();
            assertThat(beyond.total()).isEqualTo(3);
        }
    }

    @Test
    void aBuyerWithoutTeamsGetsAnEmptyPage() {
        try (SqlSession session = sessions.openSession()) {
            PageResult<TeamMembership> page =
                    repository(session)
                            .pageMembershipsByUserId(
                                    new PageQuery(1, 20),
                                    UUID.fromString("c0000000-0000-7000-8000-000000000099"));

            assertThat(page.items()).isEmpty();
            assertThat(page.total()).isZero();
        }
    }

    private static GroupDealRepositoryImpl repository(SqlSession session) {
        return new GroupDealRepositoryImpl(session.getMapper(BizGroupDealMapper.class));
    }

    private static void deal(
            UUID id, String status, int currentSize, Instant successTime, boolean deleted) {
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id, deal_no, product_id, leader_user_id, deal_price, currency, target_size,
                     current_size, status, start_time, expire_time, success_time, deleted)
                VALUES (?, ?, ?, ?, 12.50, 'sgd', 3, ?, ?, ?, ?, ?, ?)
                """,
                id,
                "DEAL-" + id,
                PRODUCT,
                BUYER,
                currentSize,
                status,
                Timestamp.from(Instant.parse("2026-09-20T06:00:00Z")),
                Timestamp.from(Instant.parse("2026-09-21T10:00:00Z")),
                successTime == null ? null : Timestamp.from(successTime),
                deleted);
    }

    private static void member(
            UUID id,
            UUID dealId,
            UUID userId,
            String role,
            Instant joinTime,
            UUID orderId,
            boolean deleted) {
        jdbc.update(
                """
                INSERT INTO biz_group_member
                    (id, deal_id, order_id, user_id, member_role, status, join_time, deleted)
                VALUES (?, ?, ?, ?, ?, 'paid', ?, ?)
                """,
                id,
                dealId,
                orderId,
                userId,
                role,
                Timestamp.from(joinTime),
                deleted);
    }
}
