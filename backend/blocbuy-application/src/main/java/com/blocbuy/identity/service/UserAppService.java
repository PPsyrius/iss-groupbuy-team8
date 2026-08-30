package com.blocbuy.identity.service;

/**
 * Use-case service for User — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 * TODO(TEAM8-identity): add one public method per use case.
 */
public interface UserAppService {}
