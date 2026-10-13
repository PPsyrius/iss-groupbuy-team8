package com.blocbuy.common.gateway;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.List;
import java.util.function.BooleanSupplier;

/** Provider-neutral transport port for committed integration events. */
public interface OutboxEventTransport {

    default List<OutboxPublishResult> publish(List<OutboxEvent> events) {
        return publish(events, () -> true);
    }

    /** Returns one ordered result per event, including unattempted rows after lease loss. */
    List<OutboxPublishResult> publish(List<OutboxEvent> events, BooleanSupplier leaseValid);
}
