package com.blocbuy.notification.persistence;

import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.repository.NotificationRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** PostgreSQL is authoritative; no identity/commerce joins or per-item queries. */
@Repository
@RequiredArgsConstructor
public class NotificationRepositoryImpl implements NotificationRepository {
    private final BizNotificationMapper mapper;

    @Override
    public int insertIfAbsent(List<Notification> notifications) {
        return mapper.insertIfAbsent(
                notifications.stream().map(NotificationConverter::toRow).toList());
    }

    @Override
    public Optional<Notification> findById(UUID id) {
        return Optional.ofNullable(NotificationConverter.toDomain(mapper.selectById(id)));
    }

    @Override
    public PageResult<Notification> pageByUserId(PageQuery query, UUID userId) {
        var items =
                mapper.selectPageByUserId(userId, query.size(), query.offset()).stream()
                        .map(NotificationConverter::toDomain)
                        .toList();
        return PageResult.of(items, mapper.countByUserId(userId), query);
    }
}
