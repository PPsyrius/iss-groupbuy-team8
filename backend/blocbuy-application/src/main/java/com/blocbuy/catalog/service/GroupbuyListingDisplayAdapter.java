package com.blocbuy.catalog.service;

import com.blocbuy.catalog.model.ProductDisplay;
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
 * only title and cover image cross the boundary — never the Product aggregate or its row. The read
 * itself is a narrow projection, so a product row that would not rebuild as an aggregate still
 * displays.
 */
@Service
@RequiredArgsConstructor
public class GroupbuyListingDisplayAdapter implements ListingDisplayGateway {

    private final ProductRepository productRepository;

    /** One batch projection read, then an in-memory map by product id. */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, ListingDisplay> findByProductIds(Collection<UUID> productIds) {
        if (productIds == null || productIds.isEmpty()) {
            return Map.of();
        }
        return productRepository.findDisplaysByIds(productIds).stream()
                .map(GroupbuyListingDisplayAdapter::toDisplay)
                .collect(Collectors.toMap(ListingDisplay::productId, Function.identity()));
    }

    private static ListingDisplay toDisplay(ProductDisplay product) {
        return new ListingDisplay(product.productId(), product.title(), product.coverImage());
    }
}
