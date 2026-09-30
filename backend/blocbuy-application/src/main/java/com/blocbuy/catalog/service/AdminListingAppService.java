package com.blocbuy.catalog.service;

import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.pricing.TeamPricePolicy;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.util.UUID;

/** UC9 use cases; callers authenticate the administrator and pass only a trusted actor ID. */
public interface AdminListingAppService {
    /** Non-idempotent draft creation; the live business number is unique. */
    ManagedListing create(String productNo, ListingDetails details, UUID actorId);

    /** Read one non-deleted listing, regardless of publication state. */
    ManagedListing get(UUID id, UUID actorId);

    /** Paginated administrative read; null status includes all non-deleted states. */
    PageResult<ManagedListing> page(PageQuery page, ProductStatus status, UUID actorId);

    /** Catalog-owned dashboard view composed through the public groupbuy query port. */
    ListingCounts counts(UUID actorId);

    /** Counts share one database snapshot; open deals span all products. */
    record ListingCounts(
            long draft, long onSale, long offSale, long openDeals, long withActivePolicy) {}

    /** Catalog-owned administrative view; policy is null when no non-deleted revision exists. */
    record ManagedListing(Product product, TeamPricePolicy latestPolicy) {}

    /** Conditional content replacement; stale writes return 412 without mutation. */
    ManagedListing revise(UUID id, ListingDetails details, int expectedVersion, UUID actorId);

    /** Conditional publication state change; draft is never a legal target. */
    ManagedListing changeStatus(UUID id, ProductStatus target, int expectedVersion, UUID actorId);

    /** Soft-delete an unreferenced never-published draft; repeat reads/deletes return 404. */
    void delete(UUID id, int expectedVersion, UUID actorId);
}
