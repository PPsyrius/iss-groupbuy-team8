package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.exception.CatalogErrorCode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductDisplay;
import com.blocbuy.catalog.model.ProductStatus;
import com.blocbuy.catalog.model.ProductTeamTerms;
import com.blocbuy.catalog.model.TeamTermsChange;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-backed {@link ProductRepository}. Browse reads are two queries per page (page rows +
 * count); no per-item queries.
 */
@Repository
@RequiredArgsConstructor
public class ProductRepositoryImpl implements ProductRepository {

    private final BizProductMapper bizProductMapper;

    /** Database uniqueness settles simultaneous attempts to create the same product number. */
    @Override
    public void insertDraft(Product product, UUID actorId) {
        if (bizProductMapper.insertDraft(ProductConverter.toRow(product), actorId) != 1) {
            throw new BizException(CatalogErrorCode.PRODUCT_NUMBER_CONFLICT);
        }
    }

    /** Version preconditions prevent last-write-wins updates. */
    @Override
    public void updateListing(Product product, int expectedVersion, UUID actorId) {
        requireChanged(
                bizProductMapper.updateListing(
                        ProductConverter.toRow(product), expectedVersion, actorId));
    }

    /** Version precondition and row lock make the terms write compare-and-set, like the listing. */
    @Override
    public void updateTeamTerms(Product product, int expectedVersion, UUID actorId) {
        requireChanged(
                bizProductMapper.updateTeamTerms(
                        ProductConverter.toRow(product), expectedVersion, actorId));
    }

    /** An audit row that cannot be stored fails the whole write, never silently drops. */
    @Override
    public void appendTeamTermsLog(TeamTermsChange change) {
        if (bizProductMapper.insertTeamTermsLog(ProductConverter.toLogRow(change)) != 1) {
            throw new IllegalStateException("team terms audit row was not stored");
        }
    }

    /** Preserve the row and every historical reference. */
    @Override
    public void deleteDraft(UUID id, int expectedVersion, UUID actorId) {
        requireChanged(bizProductMapper.deleteDraft(id, expectedVersion, actorId));
    }

    /** Two bounded queries per page, independent of item count. */
    @Override
    public PageResult<Product> pageListings(PageQuery page, ProductStatus status) {
        String statusCode = status == null ? null : status.code();
        var rows = bizProductMapper.selectListingPage(statusCode, page.size(), page.offset());
        return PageResult.of(
                rows.stream().map(ProductConverter::toDomain).toList(),
                bizProductMapper.countListings(statusCode),
                page);
    }

    /** Conditional mutations must affect exactly one row. */
    private static void requireChanged(int count) {
        if (count != 1) {
            throw new BizException(CatalogErrorCode.PRODUCT_VERSION_CONFLICT);
        }
    }

    @Override
    public Optional<Product> lockById(UUID id) {
        return Optional.ofNullable(
                ProductConverter.toDomain(bizProductMapper.selectActiveByIdForUpdate(id)));
    }

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(
                ProductConverter.toDomain(bizProductMapper.selectActiveById(id)));
    }

    /** One batch query; the empty guard keeps an empty {@code IN ()} from reaching SQL. */
    @Override
    public List<ProductDisplay> findDisplaysByIds(Collection<UUID> ids) {
        if (ids == null || ids.isEmpty()) {
            return List.of();
        }
        return bizProductMapper.selectDisplaysByIds(ids).stream()
                .map(
                        row ->
                                new ProductDisplay(
                                        row.getProductId(), row.getTitle(), row.getCoverImage()))
                .toList();
    }

    @Override
    public Optional<ProductTeamTerms> findTeamTerms(UUID id) {
        BizProductTeamTermsDO row = bizProductMapper.selectTeamTerms(id);
        return row == null
                ? Optional.empty()
                : Optional.of(
                        new ProductTeamTerms(
                                row.getProductId(),
                                row.getTeamTargetSize(),
                                row.getTeamLifetimeSeconds()));
    }

    @Override
    public boolean consumeOneUnit(UUID id) {
        return bizProductMapper.consumeOneUnit(id) == 1;
    }

    @Override
    public PageResult<Product> pageOnSale(PageQuery page, String category) {
        String normalizedCategory = category == null || category.isBlank() ? null : category;
        var rows =
                bizProductMapper.selectOnSalePage(normalizedCategory, page.size(), page.offset());
        long total = bizProductMapper.countOnSale(normalizedCategory);
        return PageResult.of(rows.stream().map(ProductConverter::toDomain).toList(), total, page);
    }

    /** Runs one bounded search query plus its matching count query. */
    @Override
    public PageResult<Product> searchOnSale(
            PageQuery page, String category, List<String> keywordTokens) {
        String normalizedCategory = category == null || category.isBlank() ? null : category;
        List<String> patterns =
                keywordTokens == null || keywordTokens.isEmpty()
                        ? null
                        : keywordTokens.stream().map(ProductRepositoryImpl::toLikePattern).toList();
        var rows =
                bizProductMapper.selectVisibleSearchPage(
                        normalizedCategory, patterns, page.size(), page.offset());
        long total = bizProductMapper.countVisibleSearch(normalizedCategory, patterns);
        return PageResult.of(rows.stream().map(ProductConverter::toDomain).toList(), total, page);
    }

    /** Escape LIKE metacharacters so user tokens are always matched as data. */
    private static String toLikePattern(String token) {
        String escaped = token.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
        return "%" + escaped + "%";
    }
}
