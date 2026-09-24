package com.blocbuy.catalog.persistence;

import java.util.UUID;
import lombok.Data;

/**
 * Projection of the two group-buy columns of biz_product; never crosses the infrastructure
 * boundary. Both sizes are boxed because an unconfigured product leaves them null.
 */
@Data
public class BizProductTeamTermsDO {

    private UUID productId;
    private Integer teamTargetSize;
    private Integer teamLifetimeSeconds;
}
