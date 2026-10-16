package com.blocbuy.identity.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.repository.UserRepository;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
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

    @Override
    @Transactional(readOnly = true)
    public CurrentUser currentUser(UUID userId) {
        var user =
                users.findById(userId)
                        .orElseThrow(() -> new BizException(IdentityErrorCode.USER_NOT_FOUND));
        return new CurrentUser(user.id(), user.nickname(), user.avatarUrl());
    }

    @Override
    @Transactional(readOnly = true)
    public Optional<String> publicNickname(UUID userId) {
        return users.findById(userId).flatMap(User::publicNickname);
    }

    /** One repository read for the whole set; an empty set reads nothing. */
    @Override
    @Transactional(readOnly = true)
    public Map<UUID, String> publicNicknames(Set<UUID> userIds) {
        if (userIds.isEmpty()) {
            return Map.of();
        }
        return users.findAllById(userIds).stream()
                .flatMap(
                        user ->
                                user
                                        .publicNickname()
                                        .map(name -> Map.entry(user.id(), name))
                                        .stream())
                .collect(Collectors.toUnmodifiableMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
