/**
 * Notification's inbound messaging adapter (backend ADR 0007, #446): decoding of the commerce
 * integration envelope into {@code NotificationEvent}. Notification-owned; it never depends on the
 * commerce outbox in {@code com.blocbuy.integration}.
 */
package com.blocbuy.notification.messaging;
