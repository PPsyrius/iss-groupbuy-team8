package com.blocbuy.identity.model;

/**
 * Display facts of a user: what other screens show, never credentials or provider data. Both values
 * are optional.
 *
 * @param nickname display nickname, may be null
 * @param avatarUrl avatar URL, may be null
 */
public record UserProfile(String nickname, String avatarUrl) {

    /** A profile with neither a nickname nor an avatar. */
    public static final UserProfile EMPTY = new UserProfile(null, null);
}
