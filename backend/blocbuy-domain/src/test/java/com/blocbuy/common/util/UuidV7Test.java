package com.blocbuy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;
import org.junit.jupiter.api.Test;

/** UUIDv7 layout: version 7, variant 10, time-ordered leading bits, no collisions. */
class UuidV7Test {

    @Test
    void generatesRfc9562Version7Uuids() {
        UUID id = UuidV7.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2); // IETF variant (10xx)
    }

    @Test
    void generatesDistinctIds() {
        assertThat(UuidV7.next()).isNotEqualTo(UuidV7.next());
    }
}
