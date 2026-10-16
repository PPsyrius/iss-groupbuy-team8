package com.blocbuy.notification.repository;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.notification.model.Notification;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Immutable persistence port: no update/version operation until mutable behavior is agreed. */
public interface NotificationRepository {
    /** One SQL batch; existing event-recipient effects are retained unchanged on replay. */
    int insertIfAbsent(List<Notification> notifications);

    /** Internal lookup; not exposed as an unscoped HTTP endpoint. */
    Optional<Notification> findById(UUID id);

    /** Active rows for this recipient only, newest first with ID as the tie-breaker. */
    PageResult<Notification> pageByUserId(PageQuery query, UUID userId);
}
