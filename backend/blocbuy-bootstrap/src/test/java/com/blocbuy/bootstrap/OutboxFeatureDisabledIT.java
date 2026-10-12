package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.blocbuy.bootstrap.fixture.PaymentFixtureConfiguration;
import com.blocbuy.bootstrap.fixture.PaymentLifecycleFixture;
import com.blocbuy.bootstrap.scheduling.OutboxRetentionJob;
import com.blocbuy.integration.outbox.IntegrationOutboxWriter;
import com.blocbuy.payment.gateway.CheckoutGateway;
import com.blocbuy.payment.model.CheckoutPayment;
import com.blocbuy.payment.repository.CheckoutPaymentRepository;
import com.blocbuy.payment.service.StripeCheckoutService;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

/** Verifies the opt-in default against a migrated PostgreSQL database. */
@SpringBootTest(
        properties = {
            "spring.flyway.locations=classpath:db/migration,classpath:db/stripe-fixture",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1",
            "blocbuy.payment.stripe.checkout-enabled=true",
            "blocbuy.payment.stripe.api-key=sk_test_fixture_placeholder",
            "blocbuy.payment.stripe.webhook-secret=whsec_fixture_placeholder",
            "blocbuy.payment.checkout.success-url=https://shop.example/payments/return",
            "blocbuy.payment.checkout.cancel-url=https://shop.example/payments/return"
        })
@ActiveProfiles("local")
@Import(PaymentFixtureConfiguration.class)
@Testcontainers
@DirtiesContext
class OutboxFeatureDisabledIT {

    @Container
    @SuppressWarnings("resource")
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

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

    @Autowired private ApplicationContext context;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PaymentLifecycleFixture coordinator;
    @Autowired private StripeCheckoutService payments;
    @Autowired private CheckoutPaymentRepository repository;
    @MockitoBean private CheckoutGateway gateway;
    private final Map<String, CheckoutGateway.Session> sessions = new ConcurrentHashMap<>();
    private UUID product;

    @BeforeEach
    void setUp() {
        jdbc.execute(
                "TRUNCATE sys_outbox,biz_payment,log_payment_webhook_event,biz_purchase_intent,"
                        + "biz_order,biz_group_member,biz_group_deal,biz_group_participation_receipt,"
                        + "biz_team_price_policy,biz_product,sys_user");
        product = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO biz_product(id,product_no,title,original_price,currency,stock,status,"
                        + "team_target_size,team_lifetime_seconds)"
                        + " VALUES(?,?,'Outbox disabled test product',20,'sgd',100,'on_sale',2,7200)",
                product,
                product.toString());
        UUID admin = buyer();
        jdbc.update(
                "INSERT INTO biz_team_price_policy(product_id,amount,currency,valid_from,valid_until,"
                        + "create_by,update_by,version)"
                        + " VALUES(?,10,'sgd',clock_timestamp()-interval '1 hour',"
                        + "clock_timestamp()+interval '1 day',?,?,1)",
                product,
                admin,
                admin);
        when(gateway.createCheckoutSession(any()))
                .thenAnswer(
                        call -> {
                            CheckoutPayment payment = call.getArgument(0);
                            var session = session(payment, "open", "unpaid");
                            sessions.put(session.id(), session);
                            return session;
                        });
        when(gateway.retrieveCheckoutSession(any()))
                .thenAnswer(call -> sessions.get(call.getArgument(0)));
    }

    @Test
    void unsetFeatureSwitchAllowsCommittedTeamSuccessWithoutOutboxRows() {
        assertThat(context.getBeansOfType(IntegrationOutboxWriter.class)).isEmpty();
        assertThat(context.getBeansOfType(OutboxRetentionJob.class)).isEmpty();
        UUID deal = createSuccessfulTeam();

        UUID joiner = buyer();
        UUID intent = coordinator.intent(joiner, "JOIN", deal);
        var prepared = payments.prepare(joiner, intent, UUID.randomUUID().toString());
        CheckoutPayment payment = repository.findById(prepared.paymentId()).orElseThrow();
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));
        payments.recover(payment.id(), "test-operator", true);

        assertThat(
                        jdbc.queryForObject(
                                "SELECT status FROM biz_group_deal WHERE id=?", String.class, deal))
                .isEqualTo("succeeded");
        assertThat(
                        jdbc.queryForObject(
                                "SELECT count(*) FROM sys_outbox WHERE aggregate_id=?",
                                Long.class,
                                deal))
                .isZero();
    }

    private UUID createSuccessfulTeam() {
        UUID leader = buyer();
        UUID intent = coordinator.intent(leader, "START", product);
        var prepared = payments.prepare(leader, intent, UUID.randomUUID().toString());
        CheckoutPayment payment = repository.findById(prepared.paymentId()).orElseThrow();
        sessions.put(payment.sessionId(), session(payment, "complete", "paid"));
        payments.recover(payment.id(), "test-operator", true);
        return jdbc.queryForObject(
                "SELECT deal_id FROM biz_group_member WHERE payment_id=?",
                UUID.class,
                payment.id());
    }

    private UUID buyer() {
        UUID id = UUID.randomUUID();
        jdbc.update(
                "INSERT INTO sys_user(id,register_source,status) VALUES(?,'local','active')", id);
        return id;
    }

    private CheckoutGateway.Session session(CheckoutPayment payment, String status, String paid) {
        return new CheckoutGateway.Session(
                "cs_test_" + payment.id(),
                payment.id().toString(),
                payment.quote().intentId().toString(),
                "paid".equals(paid) ? "pi_" + payment.id() : null,
                payment.quote().amount().movePointRight(2).longValueExact(),
                "sgd",
                status,
                paid,
                false,
                "https://checkout.stripe.com/c/pay/cs_test_" + payment.id(),
                payment.sessionExpiresAt());
    }
}
