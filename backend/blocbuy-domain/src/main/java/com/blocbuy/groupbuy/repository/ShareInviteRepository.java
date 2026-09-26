package com.blocbuy.groupbuy.repository;

import com.blocbuy.groupbuy.model.ShareInvite;
import java.util.Optional;
import java.util.UUID;

/** Repository port for UC7 invite references. Implemented in the infrastructure layer. */
public interface ShareInviteRepository {

    /** The active invite a reference names, or empty when it names none. */
    Optional<ShareInvite> findByRef(String ref);

    /** The sharer's existing active invite into this team, or empty. */
    Optional<ShareInvite> findByDealAndSharer(UUID dealId, UUID sharerUserId);

    /**
     * Inserts the candidate unless an active row already holds its team-and-sharer pair or its
     * reference. Never updates, never fails on either conflict.
     *
     * @return true when the candidate was inserted
     */
    boolean insertIfAbsent(ShareInvite candidate);
}
