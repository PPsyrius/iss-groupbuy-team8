package com.blocbuy.notification.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

/** Documents the repository skeleton contract until notification persistence lands. */
class NotificationRepositoryImplTest {

    private final NotificationRepositoryImpl repository = new NotificationRepositoryImpl();

    @Test
    void findByIdReturnsEmptyUntilPersistenceIsAvailable() {
        assertThat(repository.findById("notification-1")).isEmpty();
    }
}
