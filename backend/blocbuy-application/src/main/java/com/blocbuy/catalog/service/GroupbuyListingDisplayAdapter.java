package com.blocbuy.catalog.service;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.groupbuy.gateway.ListingDisplayGateway;
import java.util.Collection;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Catalog's implementation of the groupbuy-owned {@link ListingDisplayGateway}. Keeps the
 * dependency pointing catalog → groupbuy: groupbuy declares what it needs, catalog supplies it, and
 * only title and cover image cross the boundary — never the Product aggregate or its row.
 */
@Service
@RequiredArgsConstructor
public class GroupbuyListingDisplayAdapter implements ListingDisplayGateway {

    private final ProductRepository productRepository;

    /** One batch repository call, then an in-memory map by product id. */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ListingDisplay> findByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findByIds(productIds).stream()
                .map(GroupbuyListingDisplayAdapter::toDisplay)
                .collect(Collectors.toMap(ListingDisplay::productId, Function.identity()));
    }

    private static ListingDisplay toDisplay(Product product) {
        return new ListingDisplay(product.id(), product.title(), product.coverImage());
    }
}
