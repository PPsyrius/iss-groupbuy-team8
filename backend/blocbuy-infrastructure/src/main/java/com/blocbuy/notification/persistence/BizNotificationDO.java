package com.blocbuy.notification.persistence;

import java.time.Instant;
import java.util.UUID;
import lombok.Getter;
import lombok.Setter;

/** MyBatis row; never exposed to application callers or HTTP clients. */
@Getter
@Setter
public class BizNotificationDO {
    private UUID id;
    private UUID eventId;
    private UUID userId;
    private String notificationType;
    private UUID referenceId;
    private String title;
    private String content;
    private Instant createTime;
}
