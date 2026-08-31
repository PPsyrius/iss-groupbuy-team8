package com.blocbuy.notification.persistence;

import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.repository.NotificationRepository;
import java.util.Optional;
import org.springframework.stereotype.Repository;

/**
 * Repository implementation for Notification. Placeholder until the NotificationMapper + DO land with the first
 * notification migration. Batch reads via {@code findByIds}; queries inside loops are forbidden.
 */
@Repository
public class NotificationRepositoryImpl implements NotificationRepository {

  @Override
  public Optional<Notification> findById(String id) {
    // TODO(TEAM8-notification): select via NotificationMapper once the table exists
    return Optional.empty();
  }
}
