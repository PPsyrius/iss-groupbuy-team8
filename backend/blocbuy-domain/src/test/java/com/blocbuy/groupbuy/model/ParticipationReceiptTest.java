package com.blocbuy.groupbuy.model;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Locked idempotency record: fingerprint plus an optional frozen terminal result. */
class ParticipationReceiptTest {

    @Test
    void pendingReceiptCarriesFingerprintWithoutResult() {
        ParticipationReceipt receipt = new ParticipationReceipt("fp-1", null);

        assertThat(receipt.fingerprint()).isEqualTo("fp-1");
        assertThat(receipt.result()).isNull();
    }

    @Test
    void completedReceiptCarriesFrozenResult() {
        ParticipationResult result =
                new ParticipationResult("applied", UUID.randomUUID(), "open", 1, null);
        ParticipationReceipt receipt = new ParticipationReceipt("fp-1", result);

        assertThat(receipt.fingerprint()).isEqualTo("fp-1");
        assertThat(receipt.result()).isEqualTo(result);
    }
}
