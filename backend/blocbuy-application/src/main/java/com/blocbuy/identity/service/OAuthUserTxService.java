package com.blocbuy.identity.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.RoleCode;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.OAuthIdentityGateway.OAuthProfile;
import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.repository.UserOAuthRepository;
import com.blocbuy.identity.repository.UserRepository;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import java.time.Instant;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Transactional account mutations for the OAuth login flow, isolated on their own bean so {@link
 * OAuthLoginAppServiceImpl} keeps provider network calls outside any transaction and can catch a
 * register-race duplicate-key violation after the inner transaction has fully rolled back.
 */
@Service
@RequiredArgsConstructor
public class OAuthUserTxService {

    private final UserRepository userRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final RoleRepository roleRepository;

    /**
     * Creates the local account, the OAuth binding, and the default buyer role grant in one local
     * transaction (identity DDL obligation). A duplicate-key violation from {@code
     * uq_sys_user_oauth_provider_subject} or {@code uq_sys_user_email} propagates for race
     * handling.
     */
    @Transactional
    public OAuthLoginResult registerNewUser(OAuthProfile profile) {
        UUID buyerRoleId =
                roleRepository
                        .findActiveRoleIdByCode(RoleCode.BUYER)
                        .orElseThrow(
                                () -> new BizException(IdentityErrorCode.IDENTITY_MISCONFIGURED));
        User user =
                User.registerViaOAuth(
                        RegisterSource.GOOGLE,
                        profile.email(),
                        profile.displayName(),
                        profile.avatarUrl());
        user.touchLogin(Instant.now());
        userRepository.saveNew(user);
        userOAuthRepository.saveNew(
                OAuthIdentity.bind(
                        user.id(),
                        profile.provider(),
                        profile.subject(),
                        profile.email(),
                        profile.displayName(),
                        profile.avatarUrl(),
                        profile.rawProfileJson()));
        roleRepository.assignRole(user.id(), buyerRoleId);
        return new OAuthLoginResult(user.id(), user.nickname(), user.avatarUrl());
    }

    /** Records a login on an existing binding: stamps user and refreshes the provider snapshot. */
    @Transactional
    public OAuthLoginResult recordLogin(OAuthIdentity identity, OAuthProfile profile) {
        User user =
                userRepository
                        .findById(identity.userId())
                        .orElseThrow(
                                () -> new BizException(IdentityErrorCode.IDENTITY_MISCONFIGURED));
        user.ensureActive();
        user.touchLogin(Instant.now());
        userRepository.updateLastLogin(user);
        identity.touchLogin(
                Instant.now(),
                profile.email(),
                profile.displayName(),
                profile.avatarUrl(),
                profile.rawProfileJson());
        userOAuthRepository.updateLoginSnapshot(identity);
        return new OAuthLoginResult(user.id(), user.nickname(), user.avatarUrl());
    }
}
