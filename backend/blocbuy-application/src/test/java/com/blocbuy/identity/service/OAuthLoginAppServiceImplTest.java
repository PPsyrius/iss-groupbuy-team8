package com.blocbuy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.OAuthIdentityGateway;
import com.blocbuy.identity.gateway.OAuthIdentityGateway.OAuthProfile;
import com.blocbuy.identity.gateway.OAuthStateStore;
import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;
import com.blocbuy.identity.repository.UserOAuthRepository;
import com.blocbuy.identity.repository.UserRepository;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DuplicateKeyException;

/** OAuth login use case: state guard, resolve-vs-register branch, register-race handling. */
@ExtendWith(MockitoExtension.class)
class OAuthLoginAppServiceImplTest {

    private static final OAuthProfile PROFILE =
            new OAuthProfile(
                    OAuthProvider.GOOGLE, "sub-123", "ada@example.com", "Ada", "https://pic", "{}");

    @Mock private OAuthIdentityGateway oAuthIdentityGateway;
    @Mock private OAuthStateStore oAuthStateStore;
    @Mock private UserRepository userRepository;
    @Mock private UserOAuthRepository userOAuthRepository;
    @Mock private OAuthUserTxService oAuthUserTxService;

    @InjectMocks private OAuthLoginAppServiceImpl service;

    @Test
    void createGoogleAuthorizeUrlEmbedsIssuedState() {
        when(oAuthStateStore.issueState()).thenReturn("state-1");
        when(oAuthIdentityGateway.buildAuthorizeUrl("state-1"))
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?...");

        assertThat(service.createGoogleAuthorizeUrl())
                .isEqualTo("https://accounts.google.com/o/oauth2/v2/auth?...");
    }

    @Test
    void loginRejectsUnknownStateWithoutCallingProvider() {
        when(oAuthStateStore.consume("bad-state")).thenReturn(false);

        assertThatThrownBy(() -> service.loginWithGoogle("code", "bad-state"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_STATE_INVALID);
        verify(oAuthIdentityGateway, never()).exchangeCodeForProfile(any());
    }

    @Test
    void loginWithExistingBindingRecordsLogin() {
        OAuthIdentity identity = binding();
        when(oAuthStateStore.consume("s")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.of(identity));
        OAuthLoginResult expected = new OAuthLoginResult(identity.userId(), "Ada", "https://pic");
        when(oAuthUserTxService.recordLogin(identity, PROFILE)).thenReturn(expected);

        assertThat(service.loginWithGoogle("c", "s")).isEqualTo(expected);
        verify(oAuthUserTxService, never()).registerNewUser(any());
    }

    @Test
    void loginWithUnknownSubjectRegistersNewUser() {
        when(oAuthStateStore.consume("s")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        OAuthLoginResult expected = new OAuthLoginResult(UUID.randomUUID(), "Ada", "https://pic");
        when(oAuthUserTxService.registerNewUser(PROFILE)).thenReturn(expected);

        assertThat(service.loginWithGoogle("c", "s")).isEqualTo(expected);
    }

    @Test
    void registerRaceRetriesAsLogin() {
        when(oAuthStateStore.consume("s")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        OAuthIdentity identity = binding();
        // first lookup misses, the loser's insert violates the unique index, re-lookup hits
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(identity));
        when(oAuthUserTxService.registerNewUser(PROFILE))
                .thenThrow(new DuplicateKeyException("uq_sys_user_oauth_provider_subject"));
        OAuthLoginResult expected = new OAuthLoginResult(identity.userId(), "Ada", "https://pic");
        when(oAuthUserTxService.recordLogin(identity, PROFILE)).thenReturn(expected);

        assertThat(service.loginWithGoogle("c", "s")).isEqualTo(expected);
    }

    @Test
    void registerRaceWithEmailCollisionMapsToConflict() {
        when(oAuthStateStore.consume("s")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        when(oAuthUserTxService.registerNewUser(PROFILE))
                .thenThrow(new DuplicateKeyException("uq_sys_user_email"));
        when(userRepository.findActiveByEmail("ada@example.com"))
                .thenReturn(
                        Optional.of(
                                com.blocbuy.identity.model.User.registerViaOAuth(
                                        com.blocbuy.identity.model.RegisterSource.GOOGLE,
                                        "ada@example.com",
                                        "Ada",
                                        null)));

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void registerRaceWithoutExplanationRethrows() {
        when(oAuthStateStore.consume("s")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        when(oAuthUserTxService.registerNewUser(PROFILE))
                .thenThrow(new DuplicateKeyException("mystery"));
        when(userRepository.findActiveByEmail("ada@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    private static OAuthIdentity binding() {
        return OAuthIdentity.bind(
                UUID.randomUUID(),
                OAuthProvider.GOOGLE,
                "sub-123",
                "ada@example.com",
                "Ada",
                null,
                "{}");
    }
}
