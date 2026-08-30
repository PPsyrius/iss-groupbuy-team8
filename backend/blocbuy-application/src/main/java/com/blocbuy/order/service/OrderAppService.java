package com.blocbuy.order.service;

/**
 * Use-case service for Order — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 * TODO(TEAM8-order): add one public method per use case.
 */
public interface OrderAppService {}
