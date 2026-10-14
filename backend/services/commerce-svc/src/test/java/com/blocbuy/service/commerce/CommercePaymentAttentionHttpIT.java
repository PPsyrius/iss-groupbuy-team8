package com.blocbuy.service.commerce;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.SessionRbac;
import com.blocbuy.common.util.UuidV7;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.client.WireMock;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** #469: real commerce HTTP/SQL/RBAC with restricted grants and socket-based identity answers. */
@SpringBootTest(
        classes = CommerceServiceApplication.class,
        properties = {"blocbuy.events.enabled=false", "blocbuy.scheduling.enabled=false"})
@ActiveProfiles({"local", "commerce"})
@AutoConfigureMockMvc
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CommercePaymentAttentionHttpIT {
    private static final String APP_USER = "blocbuy_commerce_app";
    private static final String APP_PASSWORD = "commerce-test-only";
    private static final String PATH = "/api/v1/admin/payments/attention";
    private static final String BATCH = "/internal/users/public-nicknames";
    private static final WireMockServer IDENTITY = identityServer();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    private static WireMockServer identityServer() {
        var server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @AfterAll
    static void stopIdentity() {
        IDENTITY.stop();
    }

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
        var owner =
                new JdbcTemplate(
                        new DriverManagerDataSource(
                                POSTGRES.getJdbcUrl(),
                                POSTGRES.getUsername(),
                                POSTGRES.getPassword()));
        owner.execute("CREATE ROLE " + APP_USER + " LOGIN PASSWORD '" + APP_PASSWORD + "'");
        owner.execute("CREATE SCHEMA domain");
        owner.execute("GRANT USAGE ON SCHEMA domain TO " + APP_USER);
        owner.execute(
                "ALTER DEFAULT PRIVILEGES IN SCHEMA domain "
                        + "GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO "
                        + APP_USER);
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", () -> APP_USER);
        properties.add("spring.datasource.password", () -> APP_PASSWORD);
        properties.add("spring.flyway.user", POSTGRES::getUsername);
        properties.add("spring.flyway.password", POSTGRES::getPassword);
        properties.add("spring.flyway.placeholders.approle", () -> APP_USER);
        properties.add("spring.data.redis.host", REDIS::getHost);
        properties.add("spring.data.redis.port", () -> REDIS.getMappedPort(6379));
        properties.add("blocbuy.identity.base-url", IDENTITY::baseUrl);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;

    private UUID buyer;
    private UUID masked;
    private String active;
    private String token;
    private String snapshotId;

    @BeforeEach
    void fixture() {
        IDENTITY.resetAll();
        jdbc.update("DELETE FROM biz_group_participation_receipt");
        jdbc.update("DELETE FROM biz_refund");
        jdbc.update("DELETE FROM biz_payment");
        UUID admin = UuidV7.next();
        buyer = UuidV7.next();
        masked = UuidV7.next();
        Instant at = Instant.parse("2026-10-14T00:00:00Z");
        payment(buyer, at);
        payment(masked, at.plusSeconds(1));
        active = "/internal/users/" + admin + "/active";
        IDENTITY.stubFor(
                WireMock.get(urlEqualTo(active))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"code\":\"COMMON_200_000\",\"message\":\"ok\"}")));
        IDENTITY.stubFor(
                post(urlEqualTo(BATCH))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"code\":\"COMMON_200_000\",\"message\":\"ok\","
                                                        + "\"data\":{\"nicknames\":{\""
                                                        + buyer
                                                        + "\":\"Tea buyer\"}}}")));
        token = StpUtil.getStpLogic().createLoginSession(admin.toString());
        snapshotId = SessionRbac.sessionId(StpUtil.getTokenName(), StpUtil.getLoginType(), admin);
        var session = new SaSession(snapshotId);
        session.getDataMap()
                .put(
                        SessionRbac.SNAPSHOT,
                        Map.of(
                                SessionRbac.ROLES,
                                List.of("admin"),
                                SessionRbac.PERMISSIONS,
                                List.of("payment:read")));
        SaManager.getSaTokenDao()
                .setSession(session, StpUtil.getStpLogic().getSessionTimeoutByLoginId(admin));
    }

    @AfterEach
    void clearSession() {
        if (token != null) {
            StpUtil.logoutByTokenValue(token);
        }
        if (snapshotId != null) {
            SaManager.getSaTokenDao().deleteSession(snapshotId);
        }
    }

    @Test
    void anonymousAndMissingSnapshotRequestsAreDeniedBeforeIdentityCalls() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        SaManager.getSaTokenDao().deleteSession(snapshotId);
        mvc.perform(get(PATH).header("blocbuy-token", token)).andExpect(status().isForbidden());
        assertThat(IDENTITY.getAllServeEvents()).isEmpty();
    }

    @Test
    void attentionUsesOneRemoteBatchWithoutIdentityGrants() throws Exception {
        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_USER);
        assertThatThrownBy(() -> jdbc.queryForObject("SELECT count(*) FROM sys_user", Long.class))
                .isInstanceOf(DataAccessException.class)
                .hasStackTraceContaining("permission denied");
        String body =
                mvc.perform(get(PATH).param("size", "20").header("blocbuy-token", token))
                        .andExpect(status().isOk())
                        .andExpect(header().string("Cache-Control", "no-store"))
                        .andExpect(jsonPath("$.data.total").value(2))
                        .andExpect(jsonPath("$.data.items.length()").value(2))
                        .andExpect(jsonPath("$.data.items[0].buyerNickname").value("Unknown buyer"))
                        .andExpect(jsonPath("$.data.items[1].buyerNickname").value("Tea buyer"))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        assertThat(body).doesNotContain(buyer.toString(), masked.toString(), "buyerId", "email");
        IDENTITY.verify(1, getRequestedFor(urlEqualTo(active)));
        IDENTITY.verify(
                1,
                postRequestedFor(urlEqualTo(BATCH))
                        .withRequestBody(
                                equalToJson(
                                        "{\"userIds\":[\"" + buyer + "\",\"" + masked + "\"]}",
                                        true,
                                        false)));
    }

    @Test
    void anEmptyPageMakesNoNicknameRequest() throws Exception {
        mvc.perform(get(PATH).param("page", "2").header("blocbuy-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items").isEmpty());
        IDENTITY.verify(1, getRequestedFor(urlEqualTo(active)));
        IDENTITY.verify(0, postRequestedFor(urlEqualTo(BATCH)));
    }

    @Test
    void nicknameOutageFailsClosedWithoutChangingAnyFinancialFields() throws Exception {
        var before = financialState();
        assertThat(before.get("payments")).hasSize(2);
        assertThat(before.get("refunds")).hasSize(2);
        assertThat(before.get("receipts")).hasSize(2);
        IDENTITY.stubFor(post(urlEqualTo(BATCH)).willReturn(aResponse().withStatus(503)));
        mvc.perform(get(PATH).header("blocbuy-token", token))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.data").doesNotExist());
        IDENTITY.verify(1, postRequestedFor(urlEqualTo(BATCH)));
        assertThat(financialState()).isEqualTo(before);
    }

    private Map<String, List<Map<String, Object>>> financialState() {
        return Map.of(
                "payments", jdbc.queryForList("SELECT * FROM biz_payment ORDER BY id"),
                "refunds", jdbc.queryForList("SELECT * FROM biz_refund ORDER BY id"),
                "receipts",
                        jdbc.queryForList(
                                "SELECT * FROM biz_group_participation_receipt ORDER BY id"));
    }

    private UUID payment(UUID buyer, Instant at) {
        UUID id = UuidV7.next();
        UUID deal = UuidV7.next();
        UUID intent = UuidV7.next();
        jdbc.update(
                "INSERT INTO biz_payment(id,payment_no,user_id,provider,amount,currency,status,"
                        + "intent_id,operation,business_id) VALUES (?,?,?,'stripe',12,'sgd','succeeded',?,'JOIN',?)",
                id,
                id.toString(),
                buyer,
                intent,
                deal);
        jdbc.update(
                "INSERT INTO biz_group_participation_receipt(payment_id,request_fingerprint,deal_id,"
                        + "outcome,reason_code,create_time,create_by,update_by) VALUES (?,'fixture',?,"
                        + "'compensation_required','GROUPBUY_409_002',?,?,?)",
                id,
                deal,
                java.sql.Timestamp.from(at),
                buyer,
                buyer);
        jdbc.update(
                "INSERT INTO biz_refund(payment_id,intent_id,deal_id,amount,currency,reason,"
                        + "source_event,status) VALUES (?,?,?,12,'sgd','GROUPBUY_409_002','fixture','pending')",
                id,
                intent,
                deal);
        return id;
    }
}
