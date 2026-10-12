package com.blocbuy.notification.persistence;

import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationType;

/** Handwritten mapping; restored rows pass the same invariants as new notifications. */
final class NotificationConverter {
    private NotificationConverter() {}

    static BizNotificationDO toRow(Notification notification) {
        var row = new BizNotificationDO();
        row.setId(notification.id());
        row.setEventId(notification.eventId());
        row.setUserId(notification.userId());
        row.setNotificationType(notification.type().code());
        row.setReferenceId(notification.referenceId());
        row.setTitle(notification.title());
        row.setContent(notification.content());
        row.setCreateTime(notification.createTime());
        return row;
    }

    static Notification toDomain(BizNotificationDO row) {
        if (row == null) {
            return null;
        }
        return new Notification(
                row.getId(),
                row.getEventId(),
                row.getUserId(),
                NotificationType.fromCode(row.getNotificationType()),
                row.getReferenceId(),
                row.getTitle(),
                row.getContent(),
                row.getCreateTime());
    }
}
