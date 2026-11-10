package com.blocbuy.service.commerce;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathMatching;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.gateway.StripeCheckoutGateway;
import com.blocbuy.payment.gateway.StripeClientProvider;
import com.blocbuy.payment.gateway.StripeProperties;
import com.blocbuy.payment.model.CheckoutPayment;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.stripe.Stripe;
import java.net.ServerSocket;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.json.JsonMapper;

/**
 * Local-only #459 rehearsal: real commerce HTTP/SQL/Redis, local identity and Stripe substitutes.
 */
@SpringBootTest(
        classes = CommerceServiceApplication.class,
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "blocbuy.events.enabled=false",
            "blocbuy.scheduling.enabled=false",
            "blocbuy.payment.stripe.checkout-enabled=true",
            "blocbuy.payment.stripe.api-key=sk_test_local_rehearsal_fixture",
            "blocbuy.payment.stripe.webhook-secret=local-rehearsal-not-a-provider-credential",
            "blocbuy.payment.checkout.success-url=http://localhost:5173/payments/return",
            "blocbuy.payment.checkout.cancel-url=http://localhost:5173/payments/return"
        })
@ActiveProfiles({"local", "commerce"})
@Testcontainers
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class LocalIdentityOutageRehearsalIT {
    private static final String SIGNING = "local-rehearsal-not-a-provider-credential";
    private static final WireMockServer IDENTITY = identityServer();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(2)).build();

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    @Container
    @SuppressWarnings("resource")
    static final GenericContainer<?> REDIS =
            new GenericContainer<>("redis:8.10").withExposedPorts(6379);

    @DynamicPropertySource
    static void infrastructure(DynamicPropertyRegistry properties) {
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
        properties.add("blocbuy.identity.base-url", IDENTITY::baseUrl);
    }

    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private CheckoutGateway gateway;
    private final Map<String, CheckoutGateway.Session> sessions = new ConcurrentHashMap<>();
    private final List<Map<String, Object>> observations = new ArrayList<>();

    @AfterAll
    static void stopIdentity() {
        IDENTITY.stop();
    }

    @Test
    void browseSurvivesWhileStartAndWebhookFailClosedThenOneRedeliverySettles() throws Exception {
        UUID buyer = UUID.randomUUID();
        UUID product = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status,"
                        + "team_target_size,team_lifetime_seconds)"
                        + " VALUES(?,?,'Local outage rehearsal',20,'sgd',100,'on_sale',2,7200)",
                product,
                product.toString());
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,"
                        + "valid_until,create_by,update_by,version) VALUES(?,10,'sgd',"
                        + "clock_timestamp()-interval '1 hour',clock_timestamp()+interval '1 day',?,?,1)",
                product,
                buyer,
                buyer);
        activeIdentity();
        providerSubstitute();
        String token = StpUtil.getStpLogic().createLoginSession(buyer.toString());
        try {
            String intentBody =
                    JSON.writeValueAsString(Map.of("operation", "START", "businessId", product));
            HttpResponse<String> intent =
                    call("POST", "/api/v1/payments/intents", intentBody, token, null);
            observed("healthy_start", intent, 200);
            String intentId = JSON.readTree(intent.body()).get("data").get("intentId").asText();
            HttpResponse<String> checkout =
                    call(
                            "POST",
                            "/api/v1/payments/checkout-sessions",
                            JSON.writeValueAsString(Map.of("intentId", intentId)),
                            token,
                            null);
            observed("healthy_checkout", checkout, 200);
            UUID paymentId =
                    UUID.fromString(
                            JSON.readTree(checkout.body()).get("data").get("paymentId").asText());
            CheckoutGateway.Session open = sessions.values().iterator().next();
            sessions.put(
                    open.id(),
                    new CheckoutGateway.Session(
                            open.id(),
                            open.paymentId(),
                            open.intentId(),
                            "pi_local_" + paymentId,
                            open.amountMinor(),
                            open.currency(),
                            "complete",
                            "paid",
                            false,
                            open.url(),
                            open.expiresAt(),
                            "succeeded"));
            final String event = signedEvent(open.id());
            final int intentsBefore = count("biz_purchase_intent");

            IDENTITY.stop();
            observed(
                    "identity_down_browse",
                    call("GET", "/api/v1/products?page=1&size=20", null, null, null),
                    200);
            observed(
                    "identity_down_start",
                    call("POST", "/api/v1/payments/intents", intentBody, token, null),
                    500);
            assertThat(count("biz_purchase_intent")).isEqualTo(intentsBefore);
            observed(
                    "identity_down_signed_webhook",
                    call("POST", "/api/v1/webhooks/stripe", event, null, sign(event)),
                    500);
            assertThat(paymentState(paymentId)).isEqualTo("created");
            assertThat(count("log_payment_webhook_event")).isZero();
            assertThat(count("biz_group_participation_receipt")).isZero();
            assertThat(count("biz_order")).isZero();
            assertThat(count("biz_group_member")).isZero();
            assertThat(count("biz_refund")).isZero();
            observations.add(
                    Map.of(
                            "stage",
                            "outage_database",
                            "paymentState",
                            paymentState(paymentId),
                            "events",
                            0,
                            "orders",
                            0,
                            "refunds",
                            0));

            IDENTITY.start();
            activeIdentity();
            observed(
                    "identity_restored_webhook_redelivery",
                    call("POST", "/api/v1/webhooks/stripe", event, null, sign(event)),
                    200);
            observed(
                    "duplicate_webhook_redelivery",
                    call("POST", "/api/v1/webhooks/stripe", event, null, sign(event)),
                    200);
            assertThat(paymentState(paymentId)).isEqualTo("succeeded");
            assertThat(count("log_payment_webhook_event")).isEqualTo(1);
            assertThat(count("biz_group_participation_receipt")).isEqualTo(1);
            assertThat(count("biz_order")).isEqualTo(1);
            assertThat(count("biz_group_member")).isEqualTo(1);
            assertThat(count("biz_refund")).isZero();
            observations.add(
                    Map.of(
                            "stage",
                            "recovered_database",
                            "paymentState",
                            paymentState(paymentId),
                            "events",
                            1,
                            "orders",
                            1,
                            "members",
                            1,
                            "refunds",
                            0));
            var result = new LinkedHashMap<String, Object>();
            result.put("issue", 459);
            result.put("recordedAt", Instant.now().toString());
            result.put("baseline", System.getProperty("rehearsal.baseline", "not-recorded"));
            result.put("level", "local real commerce HTTP, PostgreSQL 18 and Redis 8.10");
            result.put("substitutes", List.of("WireMock identity HTTP", "Stripe provider API"));
            result.put("observations", observations);
            result.put("result", "PASS");
            Path evidence =
                    Path.of(System.getProperty("rehearsal.evidence", "target/rehearsal-evidence"));
            Files.createDirectories(evidence);
            Files.writeString(
                    evidence.resolve("identity-outage.json"),
                    JSON.writerWithDefaultPrettyPrinter().writeValueAsString(result));
        } finally {
            if (!IDENTITY.isRunning()) {
                IDENTITY.start();
            }
            StpUtil.logoutByTokenValue(token);
        }
    }

    private void providerSubstitute() {
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(call -> {
                    CheckoutPayment payment = call.getArgument(0);
                    var session =
                            new CheckoutGateway.Session(
                                    "cs_test_local_" + payment.id(),
                                    payment.id().toString(),
                                    payment.quote().intentId().toString(),
                                    null,
                                    payment.quote().amount().movePointRight(2).longValueExact(),
                                    payment.quote().currency(),
                                    "open",
                                    "unpaid",
                                    false,
                                    "https://checkout.stripe.com/c/pay/local-rehearsal-fixture",
                                    payment.sessionExpiresAt());
                    sessions.put(session.id(), session);
                    return session;
                });
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(call -> sessions.get(call.getArgument(0)));
        var properties = new StripeProperties("", SIGNING);
        var verifier = new StripeCheckoutGateway(properties, new StripeClientProvider(properties));
        when(gateway.verifyCheckoutWebhook(any(), any()))
                .thenAnswer(call ->
                        verifier.verifyCheckoutWebhook(call.getArgument(0), call.getArgument(1)));
    }

    private HttpResponse<String> call(
            String method, String path, String body, String token, String signature)
            throws Exception {
        var request =
                HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path))
                        .timeout(Duration.ofSeconds(10))
                        .header("Content-Type", "application/json")
                        .header("X-BlocBuy-Request", "checkout")
                        .header("Idempotency-Key", "local-459-outage-rehearsal");
        if (token != null) {
            request.header("blocbuy-token", token);
        }
        if (signature != null) {
            request.header("Stripe-Signature", signature);
        }
        request.method(
                method,
                body == null
                        ? HttpRequest.BodyPublishers.noBody()
                        : HttpRequest.BodyPublishers.ofString(body));
        return HTTP.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    private void observed(String stage, HttpResponse<String> response, int expected) {
        assertThat(response.statusCode()).as(stage + ": " + response.body()).isEqualTo(expected);
        observations.add(Map.of("stage", stage, "httpStatus", response.statusCode()));
    }

    private int count(String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Integer.class);
    }

    private String paymentState(UUID id) {
        return jdbc.queryForObject("SELECT status FROM biz_payment WHERE id=?", String.class, id);
    }

    private static WireMockServer identityServer() {
        int port;
        try (ServerSocket free = new ServerSocket(0)) {
            port = free.getLocalPort();
        } catch (java.io.IOException exception) {
            throw new java.io.UncheckedIOException(exception);
        }
        var server = new WireMockServer(wireMockConfig().port(port));
        server.start();
        return server;
    }

    private static void activeIdentity() {
        IDENTITY.resetAll();
        IDENTITY.stubFor(
                get(urlPathMatching("/internal/users/.*/active"))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                "{\"code\":\"COMMON_200_000\",\"message\":\"ok\"}")));
    }

    private static String signedEvent(String sessionId) {
        return "{\"id\":\"evt_local_459_identity_outage\",\"object\":\"event\",\"api_version\":\""
                + Stripe.API_VERSION
                + "\",\"livemode\":false,\"type\":\"checkout.session.completed\","
                + "\"data\":{\"object\":{\"id\":\""
                + sessionId
                + "\",\"object\":\"checkout.session\"}}}";
    }

    private static String sign(String body) throws Exception {
        long timestamp = Instant.now().getEpochSecond();
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(SIGNING.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "t="
                + timestamp
                + ",v1="
                + HexFormat.of()
                        .formatHex(
                                mac.doFinal(
                                        (timestamp + "." + body).getBytes(StandardCharsets.UTF_8)));
    }
}
