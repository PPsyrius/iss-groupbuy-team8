package com.blocbuy.identity.service;

import java.util.UUID;

/**
 * Use-case service for User — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 */
public interface UserAppService {
    /** Revalidates an existing session's account against identity-owned current state. */
    void requireActive(UUID userId);
}
