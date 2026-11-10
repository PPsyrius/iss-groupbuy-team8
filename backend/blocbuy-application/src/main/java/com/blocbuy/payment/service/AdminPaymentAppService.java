package com.blocbuy.payment.service;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.payment.model.PaymentAttention;
import com.blocbuy.payment.repository.PaymentAttentionQuery;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Read-only visibility independent of the Stripe feature switch and refund execution. */
@Service
@RequiredArgsConstructor
public class AdminPaymentAppService {

    /** Shown when identity has no showable nickname: unknown, deleted, inactive or masked. */
    static final String UNKNOWN_BUYER = "Unknown buyer";

    private final PaymentAttentionQuery attention;
    private final UserAppService users;

    /**
     * Count, page and enrichment share a snapshot even while refunds finish concurrently. Buyer
     * nicknames come from exactly one {@link UserAppService#publicNicknames} call per page (#469).
     * In the monolith that read joins the same snapshot; in commerce-svc identity answers at call
     * time, which only matters for a display name edited mid-request.
     */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ)
    public PageResult<AttentionItem> pageAttention(PageQuery page, UUID actorId) {
        users.requireActive(actorId);
        PageResult<PaymentAttention> rows = attention.page(page);
        Set<UUID> buyers =
                rows.items().stream().map(PaymentAttention::buyerId).collect(Collectors.toSet());
        Map<UUID, String> nicknames = users.publicNicknames(buyers);
        List<AttentionItem> items =
                rows.items().stream()
                        .map(row ->
                                AttentionItem.of(
                                        row, nicknames.getOrDefault(row.buyerId(), UNKNOWN_BUYER)))
                        .toList();
        return new PageResult<>(items, rows.total(), rows.page(), rows.size(), rows.nextCursor());
    }

    /**
     * One attention row as administrators see it: the buyer's public nickname, never their id or
     * contact information.
     */
    public record AttentionItem(
            UUID paymentId,
            String buyerNickname,
            UUID dealId,
            String dealNo,
            String reasonCode,
            BigDecimal amount,
            String currency,
            String refundStatus,
            Instant occurredAt) {

        static AttentionItem of(PaymentAttention row, String buyerNickname) {
            return new AttentionItem(
                    row.paymentId(),
                    buyerNickname,
                    row.dealId(),
                    row.dealNo(),
                    row.reasonCode(),
                    row.amount(),
                    row.currency(),
                    row.refundStatus(),
                    row.occurredAt());
        }
    }
}
