package com.blocbuy.notification.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationType;
import com.blocbuy.notification.repository.NotificationRepository;
import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class NotificationAppServiceTest {
    private final NotificationRepository repository = mock(NotificationRepository.class);
    private final NotificationAppService service = new NotificationAppService(repository);

    @Test
    void delegatesBoundedBatchAndOwnerScopedPage() {
        var user = UUID.randomUUID();
        var item =
                new Notification(
                        user,
                        user,
                        user,
                        NotificationType.TEAM_EXPIRED,
                        user,
                        "title",
                        "body",
                        Instant.now());
        var batch = List.of(item);
        when(repository.insertIfAbsent(batch)).thenReturn(1);
        assertThat(service.recordBatch(batch)).isEqualTo(1);
        var query = new PageQuery(1, 20);
        var page = PageResult.of(batch, 1, query);
        when(repository.pageByUserId(query, user)).thenReturn(page);
        assertThat(service.pageMine(query, user)).isSameAs(page);
        var maximum = Collections.nCopies(NotificationAppService.MAX_BATCH_SIZE, item);
        when(repository.insertIfAbsent(maximum)).thenReturn(0);
        assertThat(service.recordBatch(maximum)).isZero();
    }

    @Test
    void invalidBatchesAndMissingOwnerNeverReachPersistence() {
        assertThatThrownBy(() -> service.recordBatch(null)).isInstanceOf(BizException.class);
        var empty = List.<Notification>of();
        assertThatThrownBy(() -> service.recordBatch(empty)).isInstanceOf(BizException.class);
        var oversized = Collections.<Notification>nCopies(1001, null);
        assertThatThrownBy(() -> service.recordBatch(oversized)).isInstanceOf(BizException.class);
        var missing = Collections.<Notification>singletonList(null);
        assertThatThrownBy(() -> service.recordBatch(missing)).isInstanceOf(BizException.class);
        var query = new PageQuery(1, 20);
        assertThatThrownBy(() -> service.pageMine(query, null)).isInstanceOf(BizException.class);
        verifyNoInteractions(repository);
    }
}
