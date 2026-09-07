package com.blocbuy.common.util;

import java.security.SecureRandom;
import java.time.Instant;
import java.util.UUID;

/**
 * Application-side UUIDv7 generator, matching the layout of PostgreSQL 18 {@code uuidv7()} (48-bit
 * epoch millis, version 7, variant 10). Entities carry their identity from birth, so inserts always
 * supply the id explicitly; the database default stays a backstop only.
 */
public final class UuidV7 {

    private static final SecureRandom RANDOM = new SecureRandom();

    private UuidV7() {}

    /** Time-ordered random UUID (RFC 9562 version 7). */
    public static UUID next() {
        long millis = Instant.now().toEpochMilli();
        long randA = RANDOM.nextLong() & 0x0FFFL;
        long randB = RANDOM.nextLong() & 0x3FFFFFFFFFFFFFFFL;
        long msb = (millis << 16) | 0x7000L | randA;
        long lsb = randB | 0x8000000000000000L;
        return new UUID(msb, lsb);
    }
}
