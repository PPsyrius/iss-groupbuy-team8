package com.blocbuy.catalog.persistence;

import java.util.UUID;
import lombok.Data;

/**
 * Persistence object for log_product_team_terms — table shape only. The id and creation time are
 * database-assigned; nothing here crosses the infrastructure boundary.
 */
@Data
public class LogProductTeamTermsDO {

    private UUID productId;
    private Integer productVersion;
    private Integer oldTargetSize;
    private Integer oldLifetimeSeconds;
    private Integer newTargetSize;
    private Integer newLifetimeSeconds;
    private Integer teamPriceVersion;
    private UUID createBy;
}
