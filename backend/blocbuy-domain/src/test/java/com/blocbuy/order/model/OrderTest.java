package com.blocbuy.order.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.order.exception.OrderErrorCode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

/** Order placement invariants and the PENDING → CONFIRMED | CANCELLED state machine. */
class OrderTest {

    private static final Instant PAID_AT = Instant.parse("2026-09-20T10:00:00Z");
    private static final Instant CANCELED_AT = Instant.parse("2026-09-21T09:30:00Z");

    @Test
    void createBuildsPendingOrderWithComputedTotal() {
        Order order = Order.create(terms("keyboard", "12.00", 2), PAID_AT);

        assertThat(order.id()).isNotNull();
        assertThat(order.status()).isEqualTo(OrderStatus.PENDING);
        assertThat(order.version()).isZero();
        assertThat(order.paidTime()).isEqualTo(PAID_AT);
        assertThat(order.createTime()).isEqualTo(PAID_AT);
        assertThat(order.cancelTime()).isNull();
        assertThat(order.totalAmount()).isEqualByComparingTo(new BigDecimal("24.00"));
        assertThat(order.productImage()).isEqualTo("https://img.example/keyboard.png");
    }

    @Test
    void createAcceptsNullProductImage() {
        Order order = Order.create(terms("keyboard", "12.00", 1, null), PAID_AT);

        assertThat(order.productImage()).isNull();
    }

    @Test
    void createNormalizesExactlyRepresentableMoneyToDatabaseScale() {
        Order order = Order.create(terms("keyboard", "12.000", 2), PAID_AT);

        assertThat(order.unitPrice().toPlainString()).isEqualTo("12.00");
        assertThat(order.totalAmount().toPlainString()).isEqualTo("24.00");
    }

    @Test
    void createRejectsUnitPriceThatRequiresDatabaseRounding() {
        Order.NewOrderTerms terms = terms("keyboard", "12.345", 2);

        assertThatThrownBy(() -> Order.create(terms, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unitPrice must be exactly representable as numeric(18,2)");
    }

    @Test
    void createRejectsUnitPriceThatExceedsDatabasePrecision() {
        Order.NewOrderTerms terms = terms("keyboard", "10000000000000000", 1);

        assertThatThrownBy(() -> Order.create(terms, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unitPrice must be exactly representable as numeric(18,2)");
    }

    @Test
    void createRejectsComputedTotalThatExceedsDatabasePrecision() {
        Order.NewOrderTerms terms = terms("keyboard", "9999999999999999.99", 2);

        assertThatThrownBy(() -> Order.create(terms, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalAmount must be exactly representable as numeric(18,2)");
    }

    @ParameterizedTest
    @ValueSource(ints = {0, -1})
    void createRejectsNonPositiveQuantity(int quantity) {
        Order.NewOrderTerms terms = terms("keyboard", "12.00", quantity);

        assertThatThrownBy(() -> Order.create(terms, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("quantity must be >= 1, got " + quantity);
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-0.01"})
    void createRejectsNonPositiveUnitPrice(String unitPrice) {
        Order.NewOrderTerms terms = terms("keyboard", unitPrice, 1);

        assertThatThrownBy(() -> Order.create(terms, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unitPrice must be positive");
    }

    /** Placement is validated before the total is computed — never a NullPointerException. */
    @Test
    void createRejectsNullUnitPriceBeforeComputingTotal() {
        Order.NewOrderTerms terms = terms("keyboard", "12.00", 1);
        Order.NewOrderTerms nullPrice =
                new Order.NewOrderTerms(
                        terms.orderNo(),
                        terms.paymentId(),
                        terms.dealId(),
                        terms.userId(),
                        terms.productId(),
                        terms.productTitle(),
                        terms.productImage(),
                        terms.quantity(),
                        null,
                        terms.currency());

        assertThatThrownBy(() -> Order.create(nullPrice, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("unitPrice must be positive");
    }

    @Test
    void createRejectsNullTermsAndNullPaidAt() {
        Order.NewOrderTerms terms = terms("keyboard", "12.00", 1);

        assertThatThrownBy(() -> Order.create(null, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms must not be null");
        assertThatThrownBy(() -> Order.create(terms, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("paidAt must not be null");
    }

    @ParameterizedTest
    @MethodSource("blankAndNullSuppliers")
    void createRejectsBlankOrderNoAndCurrency(String orderNo, String currency, String message) {
        Order.NewOrderTerms terms =
                new Order.NewOrderTerms(
                        orderNo,
                        UuidV7.next(),
                        UuidV7.next(),
                        UuidV7.next(),
                        UuidV7.next(),
                        "Mechanical keyboard",
                        null,
                        1,
                        new BigDecimal("12.00"),
                        currency);

        assertThatThrownBy(() -> Order.create(terms, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    static java.util.stream.Stream<Arguments> blankAndNullSuppliers() {
        return java.util.stream.Stream.of(
                Arguments.of(" ", "sgd", "orderNo must not be blank"),
                Arguments.of(null, "sgd", "orderNo must not be blank"),
                Arguments.of("ORDER-1", " ", "currency must not be blank"),
                Arguments.of("ORDER-1", null, "currency must not be blank"));
    }

    /**
     * Each reference is checked individually; the blank-title arm rounds out the placement guard.
     */
    @Test
    void createRejectsMissingReferences() {
        Order.NewOrderTerms base = terms("keyboard", "12.00", 1);
        Order.NewOrderTerms missingPayment =
                new Order.NewOrderTerms(
                        "ORDER-1",
                        null,
                        base.dealId(),
                        base.userId(),
                        base.productId(),
                        base.productTitle(),
                        null,
                        1,
                        base.unitPrice(),
                        base.currency());
        Order.NewOrderTerms missingDeal =
                new Order.NewOrderTerms(
                        "ORDER-1",
                        base.paymentId(),
                        null,
                        base.userId(),
                        base.productId(),
                        base.productTitle(),
                        null,
                        1,
                        base.unitPrice(),
                        base.currency());
        Order.NewOrderTerms missingUser =
                new Order.NewOrderTerms(
                        "ORDER-1",
                        base.paymentId(),
                        base.dealId(),
                        null,
                        base.productId(),
                        base.productTitle(),
                        null,
                        1,
                        base.unitPrice(),
                        base.currency());
        Order.NewOrderTerms missingProduct =
                new Order.NewOrderTerms(
                        "ORDER-1",
                        base.paymentId(),
                        base.dealId(),
                        base.userId(),
                        null,
                        base.productTitle(),
                        null,
                        1,
                        base.unitPrice(),
                        base.currency());
        Order.NewOrderTerms blankTitle =
                new Order.NewOrderTerms(
                        "ORDER-1",
                        base.paymentId(),
                        base.dealId(),
                        base.userId(),
                        base.productId(),
                        " ",
                        null,
                        1,
                        base.unitPrice(),
                        base.currency());

        assertThatThrownBy(() -> Order.create(missingPayment, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("paymentId must not be null");
        assertThatThrownBy(() -> Order.create(missingDeal, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("dealId must not be null");
        assertThatThrownBy(() -> Order.create(missingUser, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("userId must not be null");
        assertThatThrownBy(() -> Order.create(missingProduct, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productId must not be null");
        assertThatThrownBy(() -> Order.create(blankTitle, PAID_AT))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("productTitle must not be blank");
    }

    @Test
    void confirmMovesPendingToConfirmed() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT).confirm();

        assertThat(order.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(order.cancelTime()).isNull();
        assertThat(order.version()).isZero();
    }

    @Test
    void cancelMovesPendingToCancelledWithInstant() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT).cancel(CANCELED_AT);

        assertThat(order.status()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.cancelTime()).isEqualTo(CANCELED_AT);
    }

    @Test
    void cancelRejectsNullInstant() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT);

        assertThatThrownBy(() -> order.cancel(null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("canceledAt must not be null");
    }

    @ParameterizedTest
    @MethodSource("terminalOrders")
    void confirmOnTerminalStateIsRejected(Order terminal) {
        assertThatThrownBy(terminal::confirm)
                .isInstanceOf(BizException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_STATE_INVALID.code());
    }

    @ParameterizedTest
    @MethodSource("terminalOrders")
    void cancelOnTerminalStateIsRejected(Order terminal) {
        assertThatThrownBy(() -> terminal.cancel(CANCELED_AT))
                .isInstanceOf(BizException.class)
                .hasMessageContaining(OrderErrorCode.ORDER_STATE_INVALID.code());
    }

    static java.util.stream.Stream<Arguments> terminalOrders() {
        return java.util.stream.Stream.of(
                Arguments.of(Order.create(terms("keyboard", "12.00", 1), PAID_AT).confirm()),
                Arguments.of(
                        Order.create(terms("keyboard", "12.00", 1), PAID_AT).cancel(CANCELED_AT)));
    }

    @Test
    void reconstituteRejectsNullPlacement() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT);
        UUID id = order.id();
        Order.Lifecycle lifecycle = lifecycleOf(order);
        BigDecimal total = order.totalAmount();

        assertThatThrownBy(() -> Order.reconstitute(null, id, total, lifecycle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("terms must not be null");
    }

    @Test
    void reconstituteRejectsCorruptedTotal() {
        Order order = Order.create(terms("keyboard", "12.00", 2), PAID_AT);
        Order.NewOrderTerms placement = placementOf(order);
        UUID id = order.id();
        Order.Lifecycle lifecycle = lifecycleOf(order);
        BigDecimal corruptedTotal = new BigDecimal("99.00");

        assertThatThrownBy(() -> Order.reconstitute(placement, id, corruptedTotal, lifecycle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("totalAmount must equal quantity * unitPrice");
    }

    @Test
    void reconstituteNormalizesExactlyRepresentableMoneyToDatabaseScale() {
        Order original = Order.create(terms("keyboard", "12.00", 2), PAID_AT);

        Order restored =
                Order.reconstitute(
                        terms("keyboard", "12.000", 2),
                        original.id(),
                        new BigDecimal("24.000"),
                        lifecycleOf(original));

        assertThat(restored.unitPrice().toPlainString()).isEqualTo("12.00");
        assertThat(restored.totalAmount().toPlainString()).isEqualTo("24.00");
    }

    @Test
    void reconstituteRejectsMissingLifecycle() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT);
        Order.NewOrderTerms placement = placementOf(order);
        UUID id = order.id();
        BigDecimal totalAmount = order.totalAmount();

        assertThatThrownBy(() -> Order.reconstitute(placement, id, totalAmount, null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("lifecycle must not be null");
    }

    @ParameterizedTest
    @MethodSource("inconsistentCancellationLifecycles")
    void reconstituteRejectsInconsistentCancellationLifecycle(
            OrderStatus status, Instant cancelTime, String message) {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT);
        Order.NewOrderTerms placement = placementOf(order);
        UUID id = order.id();
        BigDecimal totalAmount = order.totalAmount();
        Order.Lifecycle lifecycle = new Order.Lifecycle(status, PAID_AT, cancelTime, PAID_AT, 0);

        assertThatThrownBy(() -> Order.reconstitute(placement, id, totalAmount, lifecycle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    static java.util.stream.Stream<Arguments> inconsistentCancellationLifecycles() {
        return java.util.stream.Stream.of(
                Arguments.of(
                        OrderStatus.CANCELLED,
                        null,
                        "cancelTime must be present for a cancelled order"),
                Arguments.of(
                        OrderStatus.PENDING,
                        CANCELED_AT,
                        "cancelTime must be null unless the order is cancelled"),
                Arguments.of(
                        OrderStatus.CONFIRMED,
                        CANCELED_AT,
                        "cancelTime must be null unless the order is cancelled"));
    }

    /** Every structural null the write path never produces must still fail fast on read. */
    @ParameterizedTest
    @MethodSource("corruptedRows")
    void reconstituteRejectsNullStructuralFields(
            UUID id,
            String productTitle,
            BigDecimal unitPrice,
            BigDecimal totalAmount,
            OrderStatus status,
            Instant paidTime,
            Instant createTime,
            String message) {
        Order.NewOrderTerms placement =
                new Order.NewOrderTerms(
                        "ORDER-1",
                        UuidV7.next(),
                        UuidV7.next(),
                        UuidV7.next(),
                        UuidV7.next(),
                        productTitle,
                        null,
                        1,
                        unitPrice,
                        "sgd");
        Order.Lifecycle lifecycle = new Order.Lifecycle(status, paidTime, null, createTime, 0);

        assertThatThrownBy(() -> Order.reconstitute(placement, id, totalAmount, lifecycle))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage(message);
    }

    static java.util.stream.Stream<Arguments> corruptedRows() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT);
        return java.util.stream.Stream.of(
                Arguments.of(
                        null,
                        "Keyboard",
                        order.unitPrice(),
                        order.totalAmount(),
                        OrderStatus.PENDING,
                        PAID_AT,
                        PAID_AT,
                        "id must not be null"),
                Arguments.of(
                        order.id(),
                        null,
                        order.unitPrice(),
                        order.totalAmount(),
                        OrderStatus.PENDING,
                        PAID_AT,
                        PAID_AT,
                        "productTitle must not be blank"),
                Arguments.of(
                        order.id(),
                        "Keyboard",
                        null,
                        order.totalAmount(),
                        OrderStatus.PENDING,
                        PAID_AT,
                        PAID_AT,
                        "unitPrice must be positive"),
                Arguments.of(
                        order.id(),
                        "Keyboard",
                        order.unitPrice(),
                        null,
                        OrderStatus.PENDING,
                        PAID_AT,
                        PAID_AT,
                        "totalAmount must equal quantity * unitPrice"),
                Arguments.of(
                        order.id(),
                        "Keyboard",
                        order.unitPrice(),
                        order.totalAmount(),
                        null,
                        PAID_AT,
                        PAID_AT,
                        "status must not be null"),
                Arguments.of(
                        order.id(),
                        "Keyboard",
                        order.unitPrice(),
                        order.totalAmount(),
                        OrderStatus.PENDING,
                        null,
                        PAID_AT,
                        "paidTime must not be null"),
                Arguments.of(
                        order.id(),
                        "Keyboard",
                        order.unitPrice(),
                        order.totalAmount(),
                        OrderStatus.PENDING,
                        PAID_AT,
                        null,
                        "createTime must not be null"));
    }

    @Test
    void withNextVersionCopiesEverythingButVersion() {
        Order order = Order.create(terms("keyboard", "12.00", 1), PAID_AT).confirm();
        Order bumped = order.withNextVersion();

        assertThat(bumped.version()).isEqualTo(order.version() + 1);
        assertThat(bumped.id()).isEqualTo(order.id());
        assertThat(bumped.status()).isEqualTo(order.status());
        assertThat(bumped.totalAmount()).isEqualByComparingTo(order.totalAmount());
    }

    /** Rebuilds the placement value from the aggregate's own snapshot accessors. */
    private static Order.NewOrderTerms placementOf(Order order) {
        return new Order.NewOrderTerms(
                order.orderNo(),
                order.paymentId(),
                order.dealId(),
                order.userId(),
                order.productId(),
                order.productTitle(),
                order.productImage(),
                order.quantity(),
                order.unitPrice(),
                order.currency());
    }

    private static Order.Lifecycle lifecycleOf(Order order) {
        return new Order.Lifecycle(
                order.status(),
                order.paidTime(),
                order.cancelTime(),
                order.createTime(),
                order.version());
    }

    /** Placement fixture with a distinct business number and fresh references per call. */
    private static Order.NewOrderTerms terms(String keyword, String unitPrice, int quantity) {
        return terms(keyword, unitPrice, quantity, "https://img.example/" + keyword + ".png");
    }

    private static Order.NewOrderTerms terms(
            String keyword, String unitPrice, int quantity, String image) {
        return new Order.NewOrderTerms(
                "ORDER-" + UUID.randomUUID(),
                UuidV7.next(),
                UuidV7.next(),
                UuidV7.next(),
                UuidV7.next(),
                "Mechanical " + keyword,
                image,
                quantity,
                new BigDecimal(unitPrice),
                "sgd");
    }
}
