package com.blocbuy.common.model;

/**
 * Stable role codes seeded by the identity migration. Referenced by code, never by row id.
 *
 * <p>Shared rather than identity-owned: every service checks roles from the Sa-Token session, so
 * commerce controllers must not import identity's model (backend ADR 0008).
 */
public final class RoleCode {

    /** Default role granted to every newly registered OAuth user. */
    public static final String BUYER = "buyer";

    /** Platform administrator. */
    public static final String ADMIN = "admin";

    private RoleCode() {}
}
