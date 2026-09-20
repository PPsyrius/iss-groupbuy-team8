package com.blocbuy.bootstrap.config;

import com.blocbuy.payment.service.StripeCheckoutService;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

/** Internal operator command runner. No public recovery endpoint or manual paid-state input. */
@Configuration
@Profile("stripe-ops")
public class StripeOperationsConfiguration {
    private static final String PAYMENT_ID = "payment-id";
    private static final String SESSION_ID = "session-id";
    private static final Logger LOG = LoggerFactory.getLogger(StripeOperationsConfiguration.class);

    @Bean
    ApplicationRunner stripeOperations(
            StripeCheckoutService service, ConfigurableApplicationContext context) {
        return args -> {
            String command = value(args, "payment-command");
            switch (command) {
                case "scan" -> {
                    var pending = service.scan();
                    pending.forEach(
                            payment ->
                                    LOG.warn(
                                            "CheckoutPayment needs inspection: id={}, status={}",
                                            payment.id(),
                                            payment.status()));
                    LOG.info(
                            "CheckoutPayment scan: {} candidate(s), limit 100; use cases for paginated review work items",
                            pending.size());
                }
                case "cases" -> logReviewCases(service, args);
                case "assign" ->
                        service.assignReview(
                                UUID.fromString(value(args, PAYMENT_ID)),
                                value(args, "operator"),
                                Instant.parse(value(args, "next-check-at")));
                case "inspect" -> {
                    var preview =
                            service.preview(
                                    UUID.fromString(value(args, PAYMENT_ID)),
                                    args.containsOption(SESSION_ID)
                                            ? value(args, SESSION_ID)
                                            : null);
                    LOG.info(
                            "Recovery preview (no writes): payment={} localStatus={} session={}"
                                    + " sessionStatus={} paymentStatus={} proposedAction={}",
                            preview.paymentId(),
                            preview.localStatus(),
                            preview.sessionId(),
                            preview.sessionStatus(),
                            preview.paymentStatus(),
                            preview.proposedAction());
                }
                case "recover" ->
                        service.recover(
                                UUID.fromString(value(args, PAYMENT_ID)),
                                value(args, "operator"),
                                "true".equals(value(args, "confirm-recovery")),
                                args.containsOption(SESSION_ID) ? value(args, SESSION_ID) : null);
                case "expire-open-sessions" -> {
                    if (!"true".equals(value(args, "confirm-expiry"))) {
                        throw new IllegalArgumentException("Explicit expiry confirmation required");
                    }
                    LOG.info("Expired/reconciled sessions: {}", service.expireDueSessions());
                }
                default ->
                        throw new IllegalArgumentException(
                                "Use scan, cases, assign, inspect, recover or expire-open-sessions");
            }
            SpringApplication.exit(context);
        };
    }

    private static void logReviewCases(
            StripeCheckoutService service, org.springframework.boot.ApplicationArguments args) {
        UUID after =
                args.containsOption("after-case-id")
                        ? UUID.fromString(value(args, "after-case-id"))
                        : null;
        var cases = service.reviewCases(after);
        cases.forEach(
                review -> {
                    boolean alert =
                            review.owner() == null
                                    || !review.nextCheckAt().isAfter(Instant.now())
                                    || "RECOVERY_FAILED".equals(review.reason());
                    LOG.warn(
                            "payment_review caseId={} paymentId={} owner={} reason={}"
                                    + " nextCheckAt={} alert={}",
                            review.caseId(),
                            review.paymentId(),
                            review.owner(),
                            review.reason(),
                            review.nextCheckAt(),
                            alert);
                });
        if (!cases.isEmpty()) {
            LOG.warn(
                    "Review work remains: count={}, nextPageAfter={};"
                            + " notify the project owner manually for alerts",
                    cases.size(),
                    cases.getLast().caseId());
        }
    }

    private static String value(org.springframework.boot.ApplicationArguments args, String name) {
        var values = args.getOptionValues(name);
        if (values == null || values.size() != 1 || values.getFirst().isBlank()) {
            throw new IllegalArgumentException("Missing or duplicate option: " + name);
        }
        return values.getFirst();
    }
}
