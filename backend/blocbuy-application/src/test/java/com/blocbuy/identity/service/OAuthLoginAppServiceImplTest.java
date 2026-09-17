package com.blocbuy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.OAuthIdentityGateway;
import com.blocbuy.identity.gateway.OAuthIdentityGateway.OAuthProfile;
import com.blocbuy.identity.gateway.OAuthStateStore;
import com.blocbuy.identity.gateway.OAuthStateStore.IssuedState;
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
        when(oAuthStateStore.issueState()).thenReturn(new IssuedState("state-1", "binding-1"));
        when(oAuthIdentityGateway.buildAuthorizeUrl("state-1"))
                .thenReturn("https://accounts.google.com/o/oauth2/v2/auth?...");

        var result = service.createGoogleAuthorizeUrl();

        assertThat(result.url()).isEqualTo("https://accounts.google.com/o/oauth2/v2/auth?...");
        assertThat(result.stateBinding()).isEqualTo("binding-1");
    }

    @Test
    void loginRejectsUnknownStateWithoutCallingProvider() {
        when(oAuthStateStore.consume("bad-state", "b")).thenReturn(false);

        assertThatThrownBy(() -> service.loginWithGoogle("code", "bad-state", "b"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_STATE_INVALID);
        verify(oAuthIdentityGateway, never()).exchangeCodeForProfile(any());
    }

    @Test
    void loginWithExistingBindingRecordsLogin() {
        OAuthIdentity identity = binding();
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.of(identity));
        OAuthLoginResult expected = new OAuthLoginResult(identity.userId(), "Ada", "https://pic");
        when(oAuthUserTxService.recordLogin(identity, PROFILE)).thenReturn(expected);

        assertThat(service.loginWithGoogle("c", "s", "b")).isEqualTo(expected);
        verify(oAuthUserTxService, never()).registerNewUser(any());
    }

    @Test
    void loginWithUnknownSubjectRegistersNewUser() {
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        OAuthLoginResult expected = new OAuthLoginResult(UUID.randomUUID(), "Ada", "https://pic");
        when(oAuthUserTxService.registerNewUser(PROFILE)).thenReturn(expected);

        assertThat(service.loginWithGoogle("c", "s", "b")).isEqualTo(expected);
        verify(oAuthUserTxService, never()).recordLogin(any(), any());
    }

    @Test
    void exchangeFailurePropagatesWithoutTouchingRepositories() {
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c"))
                .thenThrow(new BizException(IdentityErrorCode.OAUTH_PROVIDER_ERROR));

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s", "b"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.OAUTH_PROVIDER_ERROR);
        verify(userOAuthRepository, never()).findActiveByProviderAndSubject(any(), any());
        verify(oAuthUserTxService, never()).registerNewUser(any());
    }

    @Test
    void existingBindingDisabledPropagatesAccountDisabled() {
        OAuthIdentity identity = binding();
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.of(identity));
        when(oAuthUserTxService.recordLogin(identity, PROFILE))
                .thenThrow(new BizException(IdentityErrorCode.ACCOUNT_DISABLED));

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s", "b"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
        verify(oAuthUserTxService, never()).registerNewUser(any());
    }

    /** Proves login orders state-consume, code-exchange, binding-lookup, registration. */
    @Test
    void collaborationFollowsConsumeExchangeLookupOrder() {
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        OAuthLoginResult expected = new OAuthLoginResult(UUID.randomUUID(), "Ada", "https://pic");
        when(oAuthUserTxService.registerNewUser(PROFILE)).thenReturn(expected);

        service.loginWithGoogle("c", "s", "b");

        var order =
                inOrder(
                        oAuthStateStore,
                        oAuthIdentityGateway,
                        userOAuthRepository,
                        oAuthUserTxService);
        order.verify(oAuthStateStore).consume("s", "b");
        order.verify(oAuthIdentityGateway).exchangeCodeForProfile("c");
        order.verify(userOAuthRepository)
                .findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123");
        order.verify(oAuthUserTxService).registerNewUser(PROFILE);
    }

    /** Proves loser of uq_sys_user_oauth_provider_subject with re-lookup hit retries as login. */
    @Test
    void registerRaceRetriesAsLogin() {
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
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

        assertThat(service.loginWithGoogle("c", "s", "b")).isEqualTo(expected);
    }

    /** Proves losing uq_sys_user_email with active email maps to EMAIL_ALREADY_REGISTERED. */
    @Test
    void registerRaceWithEmailCollisionMapsToConflict() {
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
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

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s", "b"))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.EMAIL_ALREADY_REGISTERED);
    }

    @Test
    void unknownDuplicateKeyRethrowsWithoutMapping() {
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(PROFILE);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        when(oAuthUserTxService.registerNewUser(PROFILE))
                .thenThrow(new DuplicateKeyException("mystery"));
        when(userRepository.findActiveByEmail("ada@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s", "b"))
                .isInstanceOf(DuplicateKeyException.class);
    }

    @Test
    void nullEmailRaceRethrowsSkippingEmailLookup() {
        OAuthProfile anonymous =
                new OAuthProfile(OAuthProvider.GOOGLE, "sub-123", null, "Ada", null, "{}");
        when(oAuthStateStore.consume("s", "b")).thenReturn(true);
        when(oAuthIdentityGateway.exchangeCodeForProfile("c")).thenReturn(anonymous);
        when(userOAuthRepository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "sub-123"))
                .thenReturn(Optional.empty());
        when(oAuthUserTxService.registerNewUser(anonymous))
                .thenThrow(new DuplicateKeyException("mystery"));

        assertThatThrownBy(() -> service.loginWithGoogle("c", "s", "b"))
                .isInstanceOf(DuplicateKeyException.class);
        verify(userRepository, never()).findActiveByEmail(any());
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
