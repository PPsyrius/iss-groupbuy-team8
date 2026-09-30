package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.ListingDetails;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.catalog.repository.TeamPriceRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.util.UuidV7;
import com.blocbuy.groupbuy.service.GroupDealQueryService;
import com.blocbuy.identity.service.UserAppService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Product-first short transactions share the quotation lock protocol; no payment or stock workflow.
 */
@Service
@RequiredArgsConstructor
public class AdminListingAppServiceImpl implements AdminListingAppService {
    private final ProductRepository products;
    private final TeamPriceRepository prices;
    private final GroupDealQueryService deals;
    private final UserAppService users;

    /** Create server-owned identity, lifecycle and counters after checking account state. */
    @Override
    @Transactional(timeout = 10)
    public Product create(String productNo, ListingDetails details, UUID actorId) {
        users.requireActive(actorId);
        Product product =
                Product.draft(UuidV7.next(), products.resolveProductNumber(productNo), details);
        products.insertDraft(product, actorId);
        return product;
    }

    /** Administrative reads cannot be performed by a disabled account with an old session. */
    @Override
    @Transactional(readOnly = true)
    public Product get(UUID id, UUID actorId) {
        users.requireActive(actorId);
        return products.findById(id)
                .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
    }

    /** Keep count and data filters aligned and never query once per listing. */
    @Override
    @Transactional(readOnly = true)
    public PageResult<Product> page(PageQuery page, ProductStatus status, UUID actorId) {
        users.requireActive(actorId);
        return products.pageListings(page, status);
    }

    /** Domain revision preserves identity, sold counters and pricing strategy selection. */
    @Override
    @Transactional(timeout = 10)
    public Product revise(UUID id, ListingDetails details, int expectedVersion, UUID actorId) {
        Product current = lockForChange(id, expectedVersion, actorId);
        Product revised = current.revise(details);
        products.updateListing(revised, expectedVersion, actorId);
        return revised;
    }

    /** Route intent into state-specific aggregate behavior, never a public status setter. */
    @Override
    @Transactional(timeout = 10)
    public Product changeStatus(UUID id, ProductStatus target, int expectedVersion, UUID actorId) {
        Product current = lockForChange(id, expectedVersion, actorId);
        Product changed =
                switch (target) {
                    case ON_SALE -> current.publish();
                    case OFF_SALE -> current.delist();
                    case DRAFT -> throw new BizException(CatalogErrorCode.PRODUCT_STATE_CONFLICT);
                };
        products.updateListing(changed, expectedVersion, actorId);
        return changed;
    }

    /** All historical references veto deletion, including expired or logically deleted history. */
    @Override
    @Transactional(timeout = 10)
    public void delete(UUID id, int expectedVersion, UUID actorId) {
        Product current = lockForChange(id, expectedVersion, actorId);
        current.requireDeletable();
        if (prices.hasProductReferences(id) || deals.hasProductReferences(id)) {
            throw new BizException(CatalogErrorCode.PRODUCT_REFERENCED);
        }
        products.deleteDraft(id, expectedVersion, actorId);
    }

    /**
     * Product row first, then account/precondition checks, matching the existing quotation writer.
     */
    private Product lockForChange(UUID id, int expectedVersion, UUID actorId) {
        Product product =
                products.lockById(id)
                        .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        users.requireActive(actorId);
        product.requireVersion(expectedVersion);
        return product;
    }
}
