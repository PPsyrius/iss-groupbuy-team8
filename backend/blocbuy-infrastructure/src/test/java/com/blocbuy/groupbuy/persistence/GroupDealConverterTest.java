package com.blocbuy.groupbuy.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.groupbuy.model.GroupDeal;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Converter null-tolerance: absent rows and nullable size columns map to safe defaults. */
class GroupDealConverterTest {

    @Test
    void nullRowMapsToNull() {
        assertThat(GroupDealConverter.toDomain(null)).isNull();
    }

    /**
     * Null size columns fall back to zero in the converter, which the aggregate then rejects — such
     * rows cannot exist under the NOT NULL schema, so fail-fast is the correct outcome.
     */
    @Test
    void nullSizesFailFastInReconstitution() {
        BizGroupDealDO row = row();
        row.setTargetSize(null);
        row.setCurrentSize(null);

        assertThatThrownBy(() -> GroupDealConverter.toDomain(row))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void populatedRowRoundTrips() {
        GroupDeal deal = GroupDealConverter.toDomain(row());

        assertThat(deal.dealPrice()).isEqualByComparingTo("10.00");
        assertThat(deal.currency()).isEqualTo("sgd");
        assertThat(deal.targetSize()).isEqualTo(3);
        assertThat(deal.currentSize()).isEqualTo(1);
    }

    private static BizGroupDealDO row() {
        BizGroupDealDO row = new BizGroupDealDO();
        row.setId(UUID.randomUUID());
        row.setDealNo("TEST-1");
        row.setProductId(UUID.randomUUID());
        row.setLeaderUserId(UUID.randomUUID());
        row.setDealPrice(new BigDecimal("10.00"));
        row.setCurrency("sgd");
        row.setTargetSize(3);
        row.setCurrentSize(1);
        row.setStatus("open");
        row.setStartTime(Instant.parse("2026-09-01T00:00:00Z"));
        row.setExpireTime(Instant.parse("2027-12-31T23:59:59Z"));
        return row;
    }
}
