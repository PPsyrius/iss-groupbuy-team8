package com.blocbuy.notification.repository;

import com.blocbuy.notification.model.Notification;
import java.util.Optional;

/**
 * Repository port for the Notification aggregate (one repository per aggregate root). Implemented in the
 * infrastructure layer; DOs and MyBatis mappers never cross this boundary.
 * TODO(TEAM8-notification): add intent-named methods (e.g. findById, saveIfVersion) per use case.
 */
public interface NotificationRepository {

  Optional<Notification> findById(String id);
}
