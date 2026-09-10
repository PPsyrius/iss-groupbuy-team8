package com.blocbuy.groupbuy.persistence;

import java.util.UUID;
import lombok.Data;

/** Infrastructure-only receipt row, including the frozen terminal outcome. */
@Data
public class ParticipationReceiptDO {
    private String requestFingerprint;
    private UUID dealId;
    private String outcome;
    private String dealStatus;
    private Integer currentSize;
    private String reasonCode;
}
