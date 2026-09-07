package com.blocbuy.identity.service;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.OAuthIdentityGateway;
import com.blocbuy.identity.gateway.OAuthIdentityGateway.OAuthProfile;
import com.blocbuy.identity.gateway.OAuthStateStore;
import com.blocbuy.identity.repository.UserOAuthRepository;
import com.blocbuy.identity.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

/**
 * Default {@link OAuthLoginAppService}. Deliberately NOT {@code @Transactional}: the state check
 * and the provider code exchange are network/Redis calls that must not hold a database connection;
 * account mutations run in {@link OAuthUserTxService} short transactions.
 *
 * <p>Register race: two concurrent callbacks for the same new Google subject both miss the lookup;
 * the loser's insert violates {@code uq_sys_user_oauth_provider_subject}, its transaction rolls
 * back, and we retry as a plain login (identity DDL obligation). An email collision with an
 * existing non-Google account maps to 409 — account merging is an explicit future action, never
 * automatic.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class OAuthLoginAppServiceImpl implements OAuthLoginAppService {

    private final OAuthIdentityGateway oAuthIdentityGateway;
    private final OAuthStateStore oAuthStateStore;
    private final UserRepository userRepository;
    private final UserOAuthRepository userOAuthRepository;
    private final OAuthUserTxService oAuthUserTxService;

    @Override
    public GoogleAuthorizeUrl createGoogleAuthorizeUrl() {
        var issued = oAuthStateStore.issueState();
        return new GoogleAuthorizeUrl(
                oAuthIdentityGateway.buildAuthorizeUrl(issued.state()), issued.binding());
    }

    @Override
    public OAuthLoginResult loginWithGoogle(String code, String state, String stateBinding) {
        if (!oAuthStateStore.consume(state, stateBinding)) {
            throw new BizException(IdentityErrorCode.OAUTH_STATE_INVALID);
        }
        OAuthProfile profile = oAuthIdentityGateway.exchangeCodeForProfile(code);
        var existing =
                userOAuthRepository.findActiveByProviderAndSubject(
                        profile.provider(), profile.subject());
        if (existing.isPresent()) {
            return oAuthUserTxService.recordLogin(existing.get(), profile);
        }
        try {
            return oAuthUserTxService.registerNewUser(profile);
        } catch (DuplicateKeyException race) {
            return resolveRegisterRace(profile, race);
        }
    }

    /** The insert lost a race (or hit an email conflict): re-read and classify. */
    private OAuthLoginResult resolveRegisterRace(OAuthProfile profile, DuplicateKeyException race) {
        var binding =
                userOAuthRepository.findActiveByProviderAndSubject(
                        profile.provider(), profile.subject());
        if (binding.isPresent()) {
            log.info("oauth register race resolved as login: provider={}", profile.provider());
            return oAuthUserTxService.recordLogin(binding.get(), profile);
        }
        if (profile.email() != null
                && userRepository.findActiveByEmail(profile.email()).isPresent()) {
            throw new BizException(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
        }
        // neither backstop explains the violation — treat as a system failure
        throw race;
    }
}
