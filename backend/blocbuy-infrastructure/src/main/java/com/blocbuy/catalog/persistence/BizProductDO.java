package com.blocbuy.catalog.persistence;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;
import lombok.Data;

/**
 * Persistence object for biz_product — table shape only, no business rules. Never crosses the
 * infrastructure boundary; the converter maps it to the {@code Product} aggregate. {@code images}
 * stays raw jsonb text here; the converter parses it.
 */
@Data
public class BizProductDO {

    private UUID id;
    private String productNo;
    private String title;
    private String subtitle;
    private String description;
    private String coverImage;
    private String images;
    private String category;
    private BigDecimal originalPrice;
    private String currency;
    private Integer stock;
    private Integer soldCount;
    private String status;
    private String pricingMode;
    private Instant createTime;
    private UUID createBy;
    private Instant updateTime;
    private UUID updateBy;
    private Boolean deleted;
    private Integer version;
}
