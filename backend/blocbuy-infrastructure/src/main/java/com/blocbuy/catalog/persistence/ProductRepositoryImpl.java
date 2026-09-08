package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.repository.ProductRepository;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
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

    @Override
    public Optional<Product> findById(UUID id) {
        return Optional.ofNullable(
                ProductConverter.toDomain(bizProductMapper.selectActiveById(id)));
    }

    @Override
    public PageResult<Product> pageOnSale(PageQuery page, String category) {
        String normalizedCategory = category == null || category.isBlank() ? null : category;
        var rows =
                bizProductMapper.selectOnSalePage(normalizedCategory, page.size(), page.offset());
        long total = bizProductMapper.countOnSale(normalizedCategory);
        return PageResult.of(rows.stream().map(ProductConverter::toDomain).toList(), total, page);
    }
}
