package com.blocbuy.identity.model;

/** Stable role codes seeded by the identity migration. Referenced by code, never by row id. */
public final class RoleCode {

    /** Default role granted to every newly registered OAuth user. */
    public static final String BUYER = "buyer";

    /** Platform administrator. */
    public static final String ADMIN = "admin";

    private RoleCode() {}
}
