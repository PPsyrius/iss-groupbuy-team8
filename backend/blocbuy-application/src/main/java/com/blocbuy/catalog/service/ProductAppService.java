package com.blocbuy.catalog.service;

/**
 * Use-case service for Product — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 * TODO(TEAM8-catalog): add one public method per use case.
 */
public interface ProductAppService {}
