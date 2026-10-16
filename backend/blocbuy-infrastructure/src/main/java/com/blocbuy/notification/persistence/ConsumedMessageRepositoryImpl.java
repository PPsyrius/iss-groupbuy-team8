package com.blocbuy.notification.persistence;

import com.blocbuy.notification.repository.ConsumedMessageRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** {@code INSERT ... ON CONFLICT DO NOTHING}; the primary key is the idempotency boundary. */
@Repository
@RequiredArgsConstructor
public class ConsumedMessageRepositoryImpl implements ConsumedMessageRepository {
    private final SysConsumedMessageMapper mapper;

    @Override
    public boolean markConsumed(UUID eventId, String consumer) {
        return mapper.insertIfAbsent(eventId, consumer) == 1;
    }
}
