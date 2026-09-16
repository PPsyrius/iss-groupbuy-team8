package com.blocbuy.catalog.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.blocbuy.catalog.model.PricingMode;
import com.blocbuy.catalog.model.Product;
import com.blocbuy.catalog.model.ProductStatus;
import java.math.BigDecimal;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Converter null-tolerance: absent rows, nullable counters and corrupt images payloads. */
class ProductConverterTest {

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
    void corruptImagesFailFast() {
        BizProductDO row = row();
        row.setImages("{not-json");

        assertThatThrownBy(() -> ProductConverter.toDomain(row))
                .isInstanceOf(IllegalStateException.class);
    }

    private static BizProductDO row() {
        BizProductDO row = new BizProductDO();
        row.setId(UUID.randomUUID());
        row.setProductNo("SKU-1");
        row.setTitle("Tea");
        row.setOriginalPrice(new BigDecimal("12.00"));
        row.setCurrency("sgd");
        row.setStock(5);
        row.setSoldCount(0);
        row.setStatus("draft");
        row.setPricingMode("dual");
        row.setVersion(0);
        return row;
    }
}
