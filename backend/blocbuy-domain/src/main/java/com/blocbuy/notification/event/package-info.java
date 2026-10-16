/**
 * Event-facing ports of the notification domain. {@link
 * com.blocbuy.notification.event.NotificationEventHandler} is the inbound port through which the
 * SQS consumer (infrastructure) applies integration events (backend ADR 0007, #446).
 */
package com.blocbuy.notification.event;
