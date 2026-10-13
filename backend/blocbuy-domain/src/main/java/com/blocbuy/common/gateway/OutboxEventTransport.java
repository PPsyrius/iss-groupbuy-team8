package com.blocbuy.common.gateway;

import com.blocbuy.common.model.OutboxEvent;
import com.blocbuy.common.model.OutboxPublishResult;
import java.util.List;

/** Provider-neutral transport port for committed integration events. */
public interface OutboxEventTransport {

    List<OutboxPublishResult> publish(List<OutboxEvent> events);
}
