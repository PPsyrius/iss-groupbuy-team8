package com.blocbuy.groupbuy.service;

/**
 * Use-case service for GroupDeal — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 * TODO(TEAM8-groupbuy): add one public method per use case.
 */
public interface GroupDealAppService {}
