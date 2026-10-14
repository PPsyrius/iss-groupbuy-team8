package com.blocbuy.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.payment.model.PaymentAttention;
import com.blocbuy.payment.repository.PaymentAttentionQuery;
import com.blocbuy.payment.service.AdminPaymentAppService.AttentionItem;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** #469: one identity batch per page enriches payment-owned rows without exposing buyer ids. */
class AdminPaymentAppServiceTest {
    private static final UUID ACTOR = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID TEA = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final UUID COFFEE = UUID.fromString("b0000000-0000-7000-8000-000000000002");
    private static final Instant WHEN = Instant.parse("2026-10-02T12:00:00Z");
    private static final PageQuery PAGE = new PageQuery(2, 3);

    private final PaymentAttentionQuery attention = mock(PaymentAttentionQuery.class);
    private final UserAppService users = mock(UserAppService.class);
    private final AdminPaymentAppService service = new AdminPaymentAppService(attention, users);

    @Test
    void onePageMakesExactlyOneBatchLookupForItsDistinctBuyers() {
        PaymentAttention first = row(TEA, WHEN.plusSeconds(2));
        PaymentAttention second = row(COFFEE, WHEN.plusSeconds(1));
        PaymentAttention third = row(TEA, WHEN);
        when(attention.page(PAGE))
                .thenReturn(PageResult.of(List.of(first, second, third), 7, PAGE));
        when(users.publicNicknames(Set.of(TEA, COFFEE))).thenReturn(Map.of(TEA, "Tea buyer"));

        PageResult<AttentionItem> result = service.pageAttention(PAGE, ACTOR);

        verify(users, times(1)).publicNicknames(any());
        verify(users).publicNicknames(Set.of(TEA, COFFEE));
        assertThat(result.items())
                .extracting(AttentionItem::paymentId, AttentionItem::buyerNickname)
                .containsExactly(
                        tuple(first.paymentId(), "Tea buyer"),
                        tuple(second.paymentId(), "Unknown buyer"),
                        tuple(third.paymentId(), "Tea buyer"));
        assertThat(result.total()).isEqualTo(7);
        assertThat(result.page()).isEqualTo(2);
        assertThat(result.size()).isEqualTo(3);
    }

    @Test
    void everyOtherColumnIsCopiedFromThePaymentRow() {
        PaymentAttention row = row(TEA, WHEN);
        when(attention.page(PAGE)).thenReturn(PageResult.of(List.of(row), 1, PAGE));
        when(users.publicNicknames(Set.of(TEA))).thenReturn(Map.of(TEA, "Tea buyer"));

        AttentionItem item = service.pageAttention(PAGE, ACTOR).items().getFirst();

        assertThat(item)
                .isEqualTo(
                        new AttentionItem(
                                row.paymentId(),
                                "Tea buyer",
                                row.dealId(),
                                row.dealNo(),
                                row.reasonCode(),
                                row.amount(),
                                row.currency(),
                                row.refundStatus(),
                                row.occurredAt()));
    }

    @Test
    void anEmptyPageStillMakesOneLookupWithAnEmptySet() {
        when(attention.page(PAGE)).thenReturn(PageResult.of(List.of(), 0, PAGE));
        when(users.publicNicknames(Set.of())).thenReturn(Map.of());

        assertThat(service.pageAttention(PAGE, ACTOR).items()).isEmpty();
        verify(users, times(1)).publicNicknames(Set.of());
    }

    @Test
    void anInactiveActorReadsNothing() {
        doThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED))
                .when(users)
                .requireActive(ACTOR);

        assertThatThrownBy(() -> service.pageAttention(PAGE, ACTOR))
                .isInstanceOf(BizException.class);
        verifyNoInteractions(attention);
        verify(users, never()).publicNicknames(any());
    }

    private static PaymentAttention row(UUID buyer, Instant at) {
        return new PaymentAttention(
                UUID.randomUUID(),
                buyer,
                UUID.randomUUID(),
                "BLOC-469",
                "GROUPBUY_409_002",
                new BigDecimal("12.00"),
                "sgd",
                "pending",
                at);
    }
}
