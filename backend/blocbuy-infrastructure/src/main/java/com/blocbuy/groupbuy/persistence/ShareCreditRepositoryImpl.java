package com.blocbuy.groupbuy.persistence;

import com.blocbuy.groupbuy.model.ShareCredit;
import com.blocbuy.groupbuy.repository.ShareCreditRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/** MyBatis-backed {@link ShareCreditRepository}. */
@Repository
@RequiredArgsConstructor
public class ShareCreditRepositoryImpl implements ShareCreditRepository {

    private final BizShareCreditMapper mapper;

    @Override
    public boolean recordIfAbsent(ShareCredit credit) {
        return mapper.insertIfAbsent(
                        credit.dealId(),
                        credit.paymentId(),
                        credit.sharerUserId(),
                        credit.inviteeUserId(),
                        credit.creditTime())
                == 1;
    }
}
