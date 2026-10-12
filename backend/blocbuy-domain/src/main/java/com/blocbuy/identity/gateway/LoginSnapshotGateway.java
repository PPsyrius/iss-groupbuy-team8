package com.blocbuy.identity.gateway;

import java.util.List;
import java.util.UUID;

/** Shared session publication with a freshness fence, independent of login-token metadata. */
public interface LoginSnapshotGateway {
    /** Starts a refresh; supersedes older in-flight refreshes for this account. */
    String beginRefresh(UUID userId);

    /** Publishes only if this refresh is still current; false means superseded or expired. */
    boolean publish(
            UUID userId,
            String refresh,
            List<String> roles,
            List<String> permissions,
            long timeoutSeconds);
}
