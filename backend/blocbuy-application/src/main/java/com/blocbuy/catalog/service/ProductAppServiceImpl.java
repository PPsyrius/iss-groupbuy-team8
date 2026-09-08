package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.pricing.GroupDealOffer;
import com.blocbuy.catalog.pricing.ListingPrice;
import com.blocbuy.catalog.query.ActiveDealView;
import com.blocbuy.catalog.query.ListingDetailView;
import com.blocbuy.catalog.query.ListingView;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.service.GroupDealQueryService;
import com.blocbuy.groupbuy.service.GroupDealQueryService.OpenDealOffer;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Default {@link ProductAppService}. Read-only browse: one page query plus one batch deal query,
 * then per-item price resolution through the strategy selected by {@code pricing_mode} — no
 * per-item queries (N+1 forbidden).
 */
@Service
@RequiredArgsConstructor
public class ProductAppServiceImpl implements ProductAppService {

    private final ProductRepository productRepository;
    private final GroupDealQueryService groupDealQueryService;
    private final PricingStrategyResolver pricingStrategyResolver;

    @Override
    @Transactional(readOnly = true)
    public PageResult<ListingView> pageListings(Integer page, Integer size, String category) {
        PageQuery query = PageQuery.of(page, size);
        PageResult<Product> products = productRepository.pageOnSale(query, category);
        Map<UUID, OpenDealOffer> bestDeals = bestDealsFor(products.items());
        List<ListingView> items =
                products.items().stream()
                        .map(product -> toListingView(product, bestDeals.get(product.id())))
                        .toList();
        return PageResult.of(items, products.total(), query);
    }

    @Override
    @Transactional(readOnly = true)
    public ListingDetailView getListingDetail(UUID productId) {
        Product product =
                productRepository
                        .findById(productId)
                        .filter(Product::isOnSale)
                        .orElseThrow(() -> new BizException(CatalogErrorCode.PRODUCT_NOT_FOUND));
        OpenDealOffer deal =
                groupDealQueryService
                        .findBestOpenDealsByProductIds(List.of(productId))
                        .get(productId);
        ListingPrice price = resolvePrice(product, deal);
        return new ListingDetailView(
                product.id(),
                product.title(),
                product.subtitle(),
                product.description(),
                product.coverImage(),
                product.images(),
                product.category(),
                price.individualPrice(),
                price.groupPrice(),
                price.currency(),
                product.stock(),
                product.soldCount(),
                deal == null
                        ? null
                        : new ActiveDealView(
                                deal.dealId(),
                                deal.targetSize(),
                                deal.currentSize(),
                                deal.expireTime()));
    }

    /** One batch deal lookup for a page of products. */
    private Map<UUID, OpenDealOffer> bestDealsFor(Collection<Product> products) {
        List<UUID> productIds = products.stream().map(Product::id).distinct().toList();
        return groupDealQueryService.findBestOpenDealsByProductIds(productIds);
    }

    private ListingView toListingView(Product product, OpenDealOffer deal) {
        ListingPrice price = resolvePrice(product, deal);
        return new ListingView(
                product.id(),
                product.title(),
                product.subtitle(),
                product.coverImage(),
                product.category(),
                price.individualPrice(),
                price.groupPrice(),
                price.currency());
    }

    /** Strategy selection is column-driven: the product's pricing mode picks the strategy. */
    private ListingPrice resolvePrice(Product product, OpenDealOffer deal) {
        return pricingStrategyResolver
                .strategyFor(product.pricingMode())
                .resolve(product, toOffer(deal));
    }

    private static GroupDealOffer toOffer(OpenDealOffer deal) {
        return deal == null
                ? null
                : new GroupDealOffer(
                        deal.dealId(),
                        deal.dealPrice(),
                        deal.currency(),
                        deal.targetSize(),
                        deal.currentSize(),
                        deal.expireTime());
    }
}
