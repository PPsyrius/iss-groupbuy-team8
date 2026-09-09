package com.blocbuy.identity.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.repository.UserRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Public account-status query; callers never receive identity aggregates or persistence types. */
@Service
@RequiredArgsConstructor
public class UserAppServiceImpl implements UserAppService {
    private final UserRepository users;

    @Override
    @Transactional(readOnly = true)
    public void requireActive(UUID userId) {
        users.findById(userId)
                .orElseThrow(() -> new BizException(IdentityErrorCode.USER_NOT_FOUND))
                .ensureActive();
    }
}
