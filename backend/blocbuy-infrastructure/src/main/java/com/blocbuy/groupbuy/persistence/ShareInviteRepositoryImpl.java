package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.ShareInvite;
import com.blocbuy.groupbuy.repository.ShareInviteRepository;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-backed {@link ShareInviteRepository}; DO↔domain conversion is hand-written here. */
@Repository
@RequiredArgsConstructor
public class ShareInviteRepositoryImpl implements ShareInviteRepository {

    private final BizShareInviteMapper mapper;

    @Override
    public Optional<ShareInvite> findByRef(String ref) {
        return findByRefWithDeadline(ref).map(InviteWithDeadline::invite);
    }

    /** A single PostgreSQL lookup supplies the immutable invite and its team's cache deadline. */
    Optional<InviteWithDeadline> findByRefWithDeadline(String ref) {
        BizShareInviteDO row = mapper.selectByRef(ref);
        return row == null
                ? Optional.empty()
                : Optional.of(new InviteWithDeadline(toDomain(row), row.getDealExpireTime()));
    }

    record InviteWithDeadline(ShareInvite invite, Instant expireTime) {}

    @Override
    public Optional<ShareInvite> findByDealAndSharer(UUID dealId, UUID sharerUserId) {
        return Optional.ofNullable(toDomain(mapper.selectByDealAndSharer(dealId, sharerUserId)));
    }

    @Override
    public boolean insertIfAbsent(ShareInvite candidate) {
        var row = new BizShareInviteDO();
        row.setId(candidate.id());
        row.setInviteRef(candidate.ref());
        row.setDealId(candidate.dealId());
        row.setSharerUserId(candidate.sharerUserId());
        return mapper.insertIfAbsent(row) == 1;
    }

    private static ShareInvite toDomain(BizShareInviteDO row) {
        if (row == null) {
            return null;
        }
        return new ShareInvite(
                row.getId(), row.getInviteRef(), row.getDealId(), row.getSharerUserId());
    }
}
