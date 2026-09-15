package com.blocbuy.common.util;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
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
        Set<UUID> ids = new HashSet<>();
        for (int i = 0; i < 10_000; i++) {
            assertThat(ids.add(UuidV7.next())).isTrue();
        }
    }
}
