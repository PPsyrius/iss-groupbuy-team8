package com.blocbuy.catalog.service;

import com.blocbuy.catalog.query.ListingDetailView;
import com.blocbuy.catalog.query.ListingQuery;
import com.blocbuy.catalog.query.ListingView;
import com.blocbuy.common.model.PageResult;
import java.util.UUID;

/**
 * Use-case service for Product — transaction boundary sits on public command methods
 * ({@code @Transactional}, queries {@code readOnly = true}). Orchestrates aggregates, domain
 * services, repository ports, and gateways; carries no rules that belong to a single aggregate.
 *
 * <p>Listing browse resolves the displayed price pair per view through the pricing strategy
 * selected by the product's pricing mode; deal data arrives via the groupbuy query port, never from
 * groupbuy mappers/DOs.
 */
public interface ProductAppService {

    /**
     * Buyer listing browse: one page of on-sale products, each with its resolved dual price and the
     * open deal's progress (null when none). {@code category} null/blank lists all categories.
     */
    PageResult<ListingView> pageListings(Integer page, Integer size, String category);

    /**
     * Buyer listing search: keyword plus category, paginated. A null/blank keyword behaves like
     * {@link #pageListings(Integer, Integer, String)}. PostgreSQL owns deterministic matching;
     * callers may request best-effort semantic ordering of that bounded result page.
     */
    PageResult<ListingView> search(ListingQuery query);

    /**
     * Listing detail with the resolved dual price and the active deal's progress.
     *
     * @throws com.blocbuy.common.exception.BizException {@code PRODUCT_NOT_FOUND} when the product
     *     is missing, deleted, or not on sale (no existence leak for off-sale products)
     */
    ListingDetailView getListingDetail(UUID productId);
}
