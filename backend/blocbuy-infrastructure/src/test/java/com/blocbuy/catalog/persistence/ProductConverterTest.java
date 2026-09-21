package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Product conversion: null tolerance, counter defaults, corrupt payloads fail fast. */
class ProductConverterTest {

    private static final UUID PRODUCT_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Test
    void toDomainNullMapsToNull() {
        assertThat(ProductConverter.toDomain(null)).isNull();
    }

    @Test
    void nullCountsDefaultToZero() {
        BizProductDO row = row();
        row.setStock(null);
        row.setSoldCount(null);
        row.setVersion(null);

        Product product = ProductConverter.toDomain(row);

        assertThat(product.stock()).isZero();
        assertThat(product.soldCount()).isZero();
        assertThat(product.version()).isZero();
        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.pricingMode()).isEqualTo(PricingMode.DUAL);
    }

    @Test
    void toDomainMapsEveryPersistedField() {
        Product product = ProductConverter.toDomain(row());

        assertThat(product.id()).isEqualTo(PRODUCT_ID);
        assertThat(product.productNo()).isEqualTo("SKU-1");
        assertThat(product.title()).isEqualTo("Tea");
        assertThat(product.subtitle()).isEqualTo("Fresh leaves");
        assertThat(product.description()).isEqualTo("Green tea");
        assertThat(product.coverImage()).isEqualTo("cover.jpg");
        assertThat(product.images()).containsExactly("one.jpg", "two.jpg");
        assertThat(product.category()).isEqualTo("grocery");
        assertThat(product.originalPrice()).isEqualByComparingTo("12.00");
        assertThat(product.currency()).isEqualTo("sgd");
        assertThat(product.stock()).isEqualTo(5);
        assertThat(product.soldCount()).isEqualTo(2);
        assertThat(product.status()).isEqualTo(ProductStatus.DRAFT);
        assertThat(product.pricingMode()).isEqualTo(PricingMode.DUAL);
        assertThat(product.version()).isEqualTo(3);
    }

    @Test
    void toRowMapsEveryEditableField() {
        Product product = ProductConverter.toDomain(row());

        BizProductDO result = ProductConverter.toRow(product);

        assertThat(result.getId()).isEqualTo(PRODUCT_ID);
        assertThat(result.getProductNo()).isEqualTo("SKU-1");
        assertThat(result.getTitle()).isEqualTo("Tea");
        assertThat(result.getSubtitle()).isEqualTo("Fresh leaves");
        assertThat(result.getDescription()).isEqualTo("Green tea");
        assertThat(result.getCoverImage()).isEqualTo("cover.jpg");
        assertThat(result.getImages()).isEqualTo("[\"one.jpg\",\"two.jpg\"]");
        assertThat(result.getCategory()).isEqualTo("grocery");
        assertThat(result.getOriginalPrice()).isEqualByComparingTo("12.00");
        assertThat(result.getCurrency()).isEqualTo("sgd");
        assertThat(result.getStock()).isEqualTo(5);
        assertThat(result.getSoldCount()).isEqualTo(2);
        assertThat(result.getStatus()).isEqualTo("draft");
        assertThat(result.getPricingMode()).isEqualTo("dual");
        assertThat(result.getVersion()).isEqualTo(3);
    }

    @Test
    void nullAndBlankImagesMapToEmptyList() {
        BizProductDO row = row();
        row.setImages(null);
        assertThat(ProductConverter.toDomain(row).images()).isEmpty();

        row.setImages("  ");
        assertThat(ProductConverter.toDomain(row).images()).isEmpty();
    }

    @Test
    void corruptImagesFailFast() {
        BizProductDO row = row();
        row.setImages("{not-json");

        assertThatThrownBy(() -> ProductConverter.toDomain(row))
                .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void unknownStatusFailsFast() {
        BizProductDO row = row();
        row.setStatus("unknown");

        assertThatThrownBy(() -> ProductConverter.toDomain(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown product status");
    }

    @Test
    void unknownPricingModeFailsFast() {
        BizProductDO row = row();
        row.setPricingMode("unknown");

        assertThatThrownBy(() -> ProductConverter.toDomain(row))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("unknown pricing mode");
    }

    private static BizProductDO row() {
        BizProductDO row = new BizProductDO();
        row.setId(PRODUCT_ID);
        row.setProductNo("SKU-1");
        row.setTitle("Tea");
        row.setSubtitle("Fresh leaves");
        row.setDescription("Green tea");
        row.setCoverImage("cover.jpg");
        row.setImages("[\"one.jpg\",\"two.jpg\"]");
        row.setCategory("grocery");
        row.setOriginalPrice(new BigDecimal("12.00"));
        row.setCurrency("sgd");
        row.setStock(5);
        row.setSoldCount(2);
        row.setStatus("draft");
        row.setPricingMode("dual");
        row.setVersion(3);
        return row;
    }
}
