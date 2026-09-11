package com.blocbuy.catalog.persistence;

import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import java.util.List;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

/**
 * Hand-written BizProductDO ↔ Product converter (no MapStruct — locked decision). Enum↔varchar and
 * jsonb↔List mapping lives here so the aggregate never sees storage codes or raw JSON.
 */
final class ProductConverter {

    private static final ObjectMapper JSON = new ObjectMapper();
    private static final TypeReference<List<String>> IMAGE_LIST = new TypeReference<>() {};

    private ProductConverter() {}

    static Product toDomain(BizProductDO data) {
        if (data == null) {
            return null;
        }
        return Product.reconstitute(
                data.getId(),
                data.getProductNo(),
                data.getTitle(),
                data.getSubtitle(),
                data.getDescription(),
                data.getCoverImage(),
                parseImages(data.getImages()),
                data.getCategory(),
                data.getOriginalPrice(),
                data.getCurrency(),
                data.getStock() == null ? 0 : data.getStock(),
                data.getSoldCount() == null ? 0 : data.getSoldCount(),
                ProductStatus.fromCode(data.getStatus()),
                PricingMode.fromCode(data.getPricingMode()),
                data.getVersion() == null ? 0 : data.getVersion());
    }

    /** Map editable aggregate state only; audit timestamps/actor remain infrastructure-managed. */
    static BizProductDO toRow(Product product) {
        BizProductDO row = new BizProductDO();
        row.setId(product.id());
        row.setProductNo(product.productNo());
        row.setTitle(product.title());
        row.setSubtitle(product.subtitle());
        row.setDescription(product.description());
        row.setCoverImage(product.coverImage());
        row.setImages(JSON.writeValueAsString(product.images()));
        row.setCategory(product.category());
        row.setOriginalPrice(product.originalPrice());
        row.setCurrency(product.currency());
        row.setStock(product.stock());
        row.setSoldCount(product.soldCount());
        row.setStatus(product.status().code());
        row.setPricingMode(product.pricingMode().code());
        row.setVersion(product.version());
        return row;
    }

    /** images jsonb → ordered URL list; corrupt payloads fail fast, never silently empty. */
    private static List<String> parseImages(String images) {
        if (images == null || images.isBlank()) {
            return List.of();
        }
        try {
            return JSON.readValue(images, IMAGE_LIST);
        } catch (Exception e) {
            throw new IllegalStateException("corrupt biz_product.images payload", e);
        }
    }
}
