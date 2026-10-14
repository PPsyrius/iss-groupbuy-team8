package com.blocbuy.identity.persistence;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.repository.UserRepository;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-backed {@link UserRepository}. Duplicate-key violations from the partial unique indexes
 * propagate as {@code DuplicateKeyException} so the application layer can map races/conflicts.
 */
@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepository {

    private final SysUserMapper sysUserMapper;

    @Override
    public Optional<User> findById(UUID id) {
        return Optional.ofNullable(UserConverter.toDomain(sysUserMapper.selectActiveById(id)));
    }

    /** An empty set reads nothing: {@code IN ()} is not valid SQL. */
    @Override
    public List<User> findAllById(Set<UUID> ids) {
        if (ids.isEmpty()) {
            return List.of();
        }
        return sysUserMapper.selectActiveByIds(ids).stream().map(UserConverter::toDomain).toList();
    }

    @Override
    public Optional<User> findActiveByEmail(String email) {
        return Optional.ofNullable(
                UserConverter.toDomain(sysUserMapper.selectActiveByEmail(email)));
    }

    @Override
    public void saveNew(User user) {
        sysUserMapper.insert(UserConverter.toDO(user));
    }

    @Override
    public void updateLastLogin(User user) {
        int updated = sysUserMapper.updateLastLogin(user.id(), user.lastLoginTime());
        if (updated == 0) {
            throw new BizException(IdentityErrorCode.USER_NOT_FOUND);
        }
    }
}
