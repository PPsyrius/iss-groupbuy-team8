package com.blocbuy.catalog.service;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.gateway.ListingSearchReranker;
import com.blocbuy.catalog.gateway.ListingSearchReranker.Candidate;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.pricing.GroupDealOffer;
import com.blocbuy.catalog.pricing.ListingPrice;
import com.blocbuy.catalog.query.ActiveDealView;
import com.blocbuy.catalog.query.ListingDetailView;
import com.blocbuy.catalog.query.ListingQuery;
import com.blocbuy.catalog.query.ListingView;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.query.OpenDealOffer;
import com.blocbuy.groupbuy.service.GroupDealQueryService;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Default {@link ProductAppService}. Read-only browse: one page query plus one batch deal query,
 * then per-item price resolution through the strategy selected by {@code pricing_mode} — no
 * per-item queries (N+1 forbidden).
 *
 * <p>Only the product reads run in a database transaction. It commits, and its connection returns
 * to the pool, before the best-offer lookup starts, so a slow Redis cannot pin a connection while
 * browse and detail requests wait on it. Each read statement sees its own READ COMMITTED snapshot
 * either way, and nothing here needs the product rows and the offers to come from one snapshot: the
 * offers are display data from a short-lived cache.
 */
@Service
@RequiredArgsConstructor
public class ProductAppServiceImpl implements ProductAppService {

    private final ProductRepository productRepository;
    private final GroupDealQueryService groupDealQueryService;
    private final PricingStrategyResolver pricingStrategyResolver;
    private final ListingSearchReranker listingSearchReranker;
    private final PlatformTransactionManager transactions;

    /**
     * Browses the deterministic on-sale catalog and assembles listing prices in one batch. The page
     * and count queries share one read-only transaction that ends before the deal lookup.
     */
    @Override
    public PageResult<ListingView> pageListings(Integer page, Integer size, String category) {
        PageQuery query = PageQuery.of(page, size);
        PageResult<Product> products =
                inReadOnlyTransaction(() -> productRepository.pageOnSale(query, category));
        return toPageResult(products, query);
    }

    /** Searches the deterministic catalog shortlist and optionally re-ranks its current page. */
    @Override
    public PageResult<ListingView> search(ListingQuery query) {
        PageResult<Product> products =
                productRepository.searchOnSale(
                        query.page(), query.category(), query.keywordTokens());
        List<Product> ordered = rerank(query, products.items());
        return toPageResult(PageResult.of(ordered, products.total(), query.page()), query.page());
    }

    /** Applies a fail-open semantic order while preserving every SQL candidate exactly once. */
    private List<Product> rerank(ListingQuery query, List<Product> products) {
        if (!query.aiRerank() || query.keyword() == null || products.size() < 2) {
            return products;
        }
        List<Candidate> candidates =
                products.stream()
                        .map(product ->
                                new Candidate(
                                        product.id(),
                                        product.title(),
                                        product.subtitle(),
                                        product.description(),
                                        product.category()))
                        .toList();
        List<UUID> rankedIds = listingSearchReranker.rerank(query.keyword(), candidates);
        if (rankedIds == null) {
            return products;
        }
        Map<UUID, Product> byId =
                products.stream().collect(java.util.stream.Collectors.toMap(Product::id, p -> p));
        LinkedHashSet<Product> ordered = new LinkedHashSet<>();
        rankedIds.stream().map(byId::get).filter(java.util.Objects::nonNull).forEach(ordered::add);
        ordered.addAll(products);
        return List.copyOf(ordered);
    }

    /** Shared browse/search assembly: one batch deal query, per-item price resolution. */
    private PageResult<ListingView> toPageResult(PageResult<Product> products, PageQuery query) {
        Map<UUID, OpenDealOffer> bestDeals = bestDealsFor(products.items());
        List<ListingView> items =
                products.items().stream()
                        .map(product -> toListingView(product, bestDeals.get(product.id())))
                        .toList();
        return PageResult.of(items, products.total(), query);
    }

    /**
     * Resolves one on-sale listing. The product read is the only database transaction; the
     * not-found decision and the deal lookup both happen after it has committed.
     */
    @Override
    public ListingDetailView getListingDetail(UUID productId) {
        Product product =
                inReadOnlyTransaction(() -> productRepository.findById(productId))
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
                toActiveDeal(deal));
    }

    /**
     * Runs a repository read in its own read-only transaction, which has committed and released its
     * connection by the time this returns. The caller must not start slow work inside {@code read}.
     */
    private <T> T inReadOnlyTransaction(Supplier<T> read) {
        TransactionTemplate tx = new TransactionTemplate(transactions);
        tx.setReadOnly(true);
        return tx.execute(status -> read.get());
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
                price.currency(),
                toActiveDeal(deal));
    }

    /**
     * Deal progress for both the grid and the detail page, present whenever an open deal exists —
     * even if the pricing strategy ignored it — so the two views never disagree.
     */
    private static ActiveDealView toActiveDeal(OpenDealOffer deal) {
        return deal == null
                ? null
                : new ActiveDealView(
                        deal.dealId(), deal.targetSize(), deal.currentSize(), deal.expireTime());
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
