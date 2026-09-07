package com.blocbuy.identity.persistence;

import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;
import com.blocbuy.identity.repository.UserOAuthRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-backed {@link UserOAuthRepository}. Duplicate-key violations from {@code
 * uq_sys_user_oauth_provider_subject} propagate so the application layer can resolve
 * double-register races (retry-then-login, per the identity DDL obligations).
 */
@Repository
@RequiredArgsConstructor
public class UserOAuthRepositoryImpl implements UserOAuthRepository {

    private final SysUserOAuthMapper sysUserOAuthMapper;

    @Override
    public Optional<OAuthIdentity> findActiveByProviderAndSubject(
            OAuthProvider provider, String subject) {
        return Optional.ofNullable(
                UserOAuthConverter.toDomain(
                        sysUserOAuthMapper.selectActiveByProviderAndSubject(
                                provider.code(), subject)));
    }

    @Override
    public void saveNew(OAuthIdentity identity) {
        sysUserOAuthMapper.insert(UserOAuthConverter.toDO(identity));
    }

    @Override
    public void updateLoginSnapshot(OAuthIdentity identity) {
        sysUserOAuthMapper.updateLoginSnapshot(
                identity.id(),
                identity.email(),
                identity.displayName(),
                identity.avatarUrl(),
                identity.rawProfile(),
                identity.lastLoginTime());
    }
}
