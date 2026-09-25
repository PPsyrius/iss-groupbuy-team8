package com.blocbuy.catalog.persistence;

import java.util.UUID;
import lombok.Data;

/** Projection of the display columns of biz_product; never crosses the infrastructure boundary. */
@Data
public class BizProductDisplayDO {

    private UUID productId;
    private String title;
    private String coverImage;
}
