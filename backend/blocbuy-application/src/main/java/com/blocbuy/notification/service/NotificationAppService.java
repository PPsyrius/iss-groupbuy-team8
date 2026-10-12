package com.blocbuy.notification.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.notification.exception.NotificationErrorCode;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.repository.NotificationRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

/** Notification use cases; #446 calls the batch write inside its consumed-message transaction. */
@Service
@RequiredArgsConstructor
public class NotificationAppService {
    public static final int MAX_BATCH_SIZE = 1000;
    private final NotificationRepository notifications;

    /** All-or-nothing batch of immutable effects; duplicate event-recipient keys are no-ops. */
    @Transactional(timeout = 3)
    public int recordBatch(List<Notification> batch) {
        if (batch == null
                || batch.isEmpty()
                || batch.size() > MAX_BATCH_SIZE
                || batch.stream().anyMatch(java.util.Objects::isNull)) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_INVALID);
        }
        return notifications.insertIfAbsent(List.copyOf(batch));
    }

    /** Page and count observe the same PostgreSQL snapshot during concurrent inserts. */
    @Transactional(readOnly = true, isolation = Isolation.REPEATABLE_READ, timeout = 3)
    public PageResult<Notification> pageMine(PageQuery query, UUID userId) {
        if (userId == null) {
            throw new BizException(NotificationErrorCode.NOTIFICATION_INVALID);
        }
        return notifications.pageByUserId(query, userId);
    }
}
