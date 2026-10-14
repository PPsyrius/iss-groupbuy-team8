package com.blocbuy.identity.service;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Use-case service for User — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 */
public interface UserAppService {
    /** Revalidates an existing session's account against identity-owned current state. */
    void requireActive(UUID userId);

    /**
     * Session-scoped profile view for rendering the logged-in account. Read-only; callers never
     * receive the identity aggregate.
     */
    CurrentUser currentUser(UUID userId);

    /**
     * The nickname any visitor may see for this user, for example on a public invite landing page.
     * Read-only and never throws for an unknown user.
     *
     * @return empty when the user is unknown or not active, or has no nickname to show
     */
    Optional<String> publicNickname(UUID userId);

    /**
     * Showable public nicknames by user id; ids without one are absent. Never throws for unknown or
     * inactive users. The same rule as {@link #publicNickname(UUID)}, answered in one batch, for
     * example once per admin page.
     */
    Map<UUID, String> publicNicknames(Set<UUID> userIds);

    /**
     * Display facts of the current session's account; never carries credentials or provider data.
     *
     * @param userId local user id (UUIDv7) — the Sa-Token login id
     * @param nickname display nickname, may be null
     * @param avatarUrl avatar URL, may be null
     */
    record CurrentUser(UUID userId, String nickname, String avatarUrl) {}
}
