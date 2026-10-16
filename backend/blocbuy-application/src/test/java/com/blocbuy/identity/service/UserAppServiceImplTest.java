package com.blocbuy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.model.UserProfile;
import com.blocbuy.identity.model.UserStatus;
import com.blocbuy.identity.repository.UserRepository;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Session account guard: missing and disabled accounts fail, profile view maps safely. */
class UserAppServiceImplTest {
    private final UserRepository users = mock(UserRepository.class);
    private final UserAppService service = new UserAppServiceImpl(users);
    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");

    @Test
    void missingAccountFailsEvenWithExistingSession() {
        assertThatThrownBy(() -> service.requireActive(ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
    }

    @Test
    void disabledAccountFailsAndActiveAccountPasses() {
        when(users.findById(ID)).thenReturn(Optional.of(user(UserStatus.DISABLED)));
        assertThatThrownBy(() -> service.requireActive(ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.ACCOUNT_DISABLED);
        when(users.findById(ID)).thenReturn(Optional.of(user(UserStatus.ACTIVE)));
        service.requireActive(ID);
    }

    @Test
    void currentUserMapsTheProfileView() {
        when(users.findById(ID))
                .thenReturn(
                        Optional.of(
                                User.reconstitute(
                                        ID,
                                        null,
                                        "test@example.invalid",
                                        RegisterSource.GOOGLE,
                                        UserStatus.ACTIVE,
                                        new UserProfile("Tester", "https://img.example/avatar.png"),
                                        null)));
        var view = service.currentUser(ID);
        assertThat(view.userId()).isEqualTo(ID);
        assertThat(view.nickname()).isEqualTo("Tester");
        assertThat(view.avatarUrl()).isEqualTo("https://img.example/avatar.png");
    }

    @Test
    void currentUserFailsWhenTheAccountIsMissing() {
        assertThatThrownBy(() -> service.currentUser(ID))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
    }

    @Test
    void publicNicknameDelegatesToTheAggregateRule() {
        when(users.findById(ID))
                .thenReturn(
                        Optional.of(
                                User.reconstitute(
                                        ID,
                                        null,
                                        "test@example.invalid",
                                        RegisterSource.GOOGLE,
                                        UserStatus.ACTIVE,
                                        new UserProfile("Sarah Tan", null),
                                        null)));
        assertThat(service.publicNickname(ID)).contains("Sarah Tan");

        when(users.findById(ID)).thenReturn(Optional.of(user(UserStatus.DISABLED)));
        assertThat(service.publicNickname(ID)).isEmpty();
    }

    @Test
    void publicNicknameIsEmptyForAnUnknownUser() {
        assertThat(service.publicNickname(ID)).isEmpty();
    }

    @Test
    void publicNicknamesKeepsOnlyShowableNicknamesFromOneRead() {
        UUID disabled = UUID.fromString("a0000000-0000-7000-8000-000000000002");
        UUID masked = UUID.fromString("a0000000-0000-7000-8000-000000000003");
        UUID unknown = UUID.fromString("a0000000-0000-7000-8000-000000000009");
        Set<UUID> ids = Set.of(ID, disabled, masked, unknown);
        when(users.findAllById(ids))
                .thenReturn(
                        List.of(
                                user(ID, UserStatus.ACTIVE, "Sarah Tan"),
                                user(disabled, UserStatus.DISABLED, "Gone"),
                                user(masked, UserStatus.ACTIVE, "test")));

        assertThat(service.publicNicknames(ids)).isEqualTo(Map.of(ID, "Sarah Tan"));
        verify(users).findAllById(ids);
    }

    @Test
    void publicNicknamesOfAnEmptySetReadsNothing() {
        assertThat(service.publicNicknames(Set.of())).isEmpty();
        verify(users, never()).findAllById(any());
    }

    private static User user(UUID id, UserStatus status, String nickname) {
        return User.reconstitute(
                id,
                null,
                "test@example.invalid",
                RegisterSource.GOOGLE,
                status,
                new UserProfile(nickname, null),
                null);
    }

    private static User user(UserStatus status) {
        return User.reconstitute(
                ID, null, "test@example.invalid", RegisterSource.GOOGLE, status, null, null);
    }
}
