package com.blocbuy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.model.RoleCode;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.gateway.OAuthIdentityGateway.OAuthProfile;
import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;
import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.repository.RoleRepository;
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

/** Transactional account mutations for the OAuth login flow. */
@ExtendWith(MockitoExtension.class)
class OAuthUserTxServiceTest {

    private static final OAuthProfile PROFILE =
            new OAuthProfile(
                    OAuthProvider.GOOGLE, "sub-123", "ada@example.com", "Ada", "https://pic", "{}");

    @Mock private UserRepository userRepository;
    @Mock private UserOAuthRepository userOAuthRepository;
    @Mock private RoleRepository roleRepository;

    @InjectMocks private OAuthUserTxService service;

    @Test
    void registerNewUserPersistsUserBindingAndDefaultRole() {
        UUID buyerRole = UUID.randomUUID();
        when(roleRepository.findActiveRoleIdByCode(RoleCode.BUYER))
                .thenReturn(Optional.of(buyerRole));

        OAuthLoginResult result = service.registerNewUser(PROFILE);

        assertThat(result.userId()).isNotNull();
        assertThat(result.nickname()).isEqualTo("Ada");
        assertThat(result.avatarUrl()).isEqualTo("https://pic");
        verify(userRepository)
                .saveNew(
                        argThat(user ->
                                user.registerSource() == RegisterSource.GOOGLE
                                        && user.lastLoginTime() != null
                                        && user.email().equals("ada@example.com")
                                        && user.nickname().equals("Ada")
                                        && user.avatarUrl().equals("https://pic")));
        verify(userOAuthRepository)
                .saveNew(
                        argThat(binding ->
                                binding.provider() == OAuthProvider.GOOGLE
                                        && binding.providerUserId().equals("sub-123")
                                        && binding.userId().equals(result.userId())
                                        && binding.email().equals("ada@example.com")));
        verify(roleRepository).assignRole(result.userId(), buyerRole);
    }

    @Test
    void registerOrdersUserBindingThenBuyerRole() {
        UUID buyerRole = UUID.randomUUID();
        when(roleRepository.findActiveRoleIdByCode(RoleCode.BUYER))
                .thenReturn(Optional.of(buyerRole));

        OAuthLoginResult result = service.registerNewUser(PROFILE);

        var order = inOrder(userRepository, userOAuthRepository, roleRepository);
        order.verify(userRepository).saveNew(argThat(user -> user.id().equals(result.userId())));
        order.verify(userOAuthRepository)
                .saveNew(argThat(binding -> binding.userId().equals(result.userId())));
        order.verify(roleRepository).assignRole(result.userId(), buyerRole);
    }

    @Test
    void registerNewUserFailsWhenDefaultRoleMissing() {
        when(roleRepository.findActiveRoleIdByCode(RoleCode.BUYER)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.registerNewUser(PROFILE))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.IDENTITY_MISCONFIGURED);
        verify(userRepository, never()).saveNew(argThat(candidate -> true));
        verify(userOAuthRepository, never()).saveNew(argThat(candidate -> true));
        verify(roleRepository, never())
                .assignRole(argThat(candidate -> true), argThat(candidate -> true));
    }

    @Test
    void recordLoginStampsUserAndRefreshesSnapshot() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", "Ada", null);
        OAuthIdentity identity =
                OAuthIdentity.bind(
                        user.id(),
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "ada@example.com",
                        "Ada",
                        null,
                        "{}");
        when(userRepository.findById(identity.userId())).thenReturn(Optional.of(user));

        OAuthLoginResult result = service.recordLogin(identity, PROFILE);

        assertThat(result.userId()).isEqualTo(user.id());
        verify(userRepository).updateLastLogin(user);
        verify(userOAuthRepository).updateLoginSnapshot(identity);
        assertThat(identity.lastLoginTime()).isNotNull();
    }

    @Test
    void recordLoginRefreshesStaleSnapshotContent() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "old@example.com", "Old", null);
        OAuthIdentity identity =
                OAuthIdentity.bind(
                        user.id(),
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "old@example.com",
                        "Old",
                        null,
                        "{}");
        when(userRepository.findById(identity.userId())).thenReturn(Optional.of(user));

        OAuthLoginResult result = service.recordLogin(identity, PROFILE);

        assertThat(identity.email()).isEqualTo("ada@example.com");
        assertThat(identity.displayName()).isEqualTo("Ada");
        assertThat(identity.avatarUrl()).isEqualTo("https://pic");
        // Login refreshes the provider snapshot only; the local nickname is never renamed.
        assertThat(result.nickname()).isEqualTo("Old");
        var order = inOrder(userRepository, userOAuthRepository);
        order.verify(userRepository).updateLastLogin(user);
        order.verify(userOAuthRepository).updateLoginSnapshot(identity);
    }

    @Test
    void recordLoginRejectsDisabledAccount() {
        User user = User.registerViaOAuth(RegisterSource.GOOGLE, "ada@example.com", "Ada", null);
        user.disable();
        OAuthIdentity identity =
                OAuthIdentity.bind(
                        user.id(),
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "ada@example.com",
                        "Ada",
                        null,
                        "{}");
        when(userRepository.findById(identity.userId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.recordLogin(identity, PROFILE))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
        verify(userRepository, never()).updateLastLogin(argThat(candidate -> true));
        verify(userOAuthRepository, never()).updateLoginSnapshot(argThat(candidate -> true));
    }

    @Test
    void recordLoginFailsWhenBindingOrphansUser() {
        OAuthIdentity identity =
                OAuthIdentity.bind(
                        UUID.randomUUID(),
                        OAuthProvider.GOOGLE,
                        "sub-123",
                        "ada@example.com",
                        "Ada",
                        null,
                        "{}");
        when(userRepository.findById(identity.userId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.recordLogin(identity, PROFILE))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.IDENTITY_MISCONFIGURED);
        verify(userRepository, never()).updateLastLogin(argThat(candidate -> true));
        verify(userOAuthRepository, never()).updateLoginSnapshot(argThat(candidate -> true));
    }
}
