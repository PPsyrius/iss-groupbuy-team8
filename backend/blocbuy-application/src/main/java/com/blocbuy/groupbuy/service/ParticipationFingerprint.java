package com.blocbuy.groupbuy.service;

import com.blocbuy.groupbuy.model.NewDealTerms;
import com.blocbuy.groupbuy.model.PaidParticipation;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;

/** Canonical receipt identity; v1 field order, numeric scale and UTF-16 lengths are persisted. */
final class ParticipationFingerprint {
    private ParticipationFingerprint() {}

    /** Encodes the accepted initiation terms without changing historical start-v1 receipts. */
    static String start(NewDealTerms terms, PaidParticipation paid) {
        var canonical = payment("start-v1", paid);
        append(canonical, terms.id().toString());
        append(canonical, terms.dealNo());
        append(canonical, terms.productId().toString());
        append(canonical, terms.price().toPlainString());
        append(canonical, terms.currency());
        append(canonical, Integer.toString(terms.targetSize()));
        append(canonical, terms.expireTime().toString());
        return hash(canonical);
    }

    /** Attribution belongs to the immutable purchase intent, outside the join-v1 identity. */
    static String join(UUID dealId, PaidParticipation paid) {
        var canonical = payment("join-v1", paid);
        append(canonical, dealId.toString());
        return hash(canonical);
    }

    private static StringBuilder payment(String operation, PaidParticipation paid) {
        var canonical = new StringBuilder();
        append(canonical, operation);
        append(canonical, paid.paymentId().toString());
        append(canonical, paid.userId().toString());
        append(canonical, paid.amount().toPlainString());
        append(canonical, paid.currency());
        return canonical;
    }

    private static String hash(StringBuilder canonical) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest.getInstance("SHA-256")
                                    .digest(canonical.toString().getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException impossible) {
            throw new IllegalStateException("Required SHA-256 unavailable", impossible);
        }
    }

    private static void append(StringBuilder target, String value) {
        // String.length() counts UTF-16 units; code-point or UTF-8 byte lengths would break v1.
        target.append(value.length()).append(':').append(value);
    }
}
