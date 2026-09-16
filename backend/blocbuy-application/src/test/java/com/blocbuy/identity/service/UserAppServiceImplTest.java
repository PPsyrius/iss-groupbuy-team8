package com.blocbuy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.model.UserStatus;
import com.blocbuy.identity.repository.UserRepository;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

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
                                        "Tester",
                                        "https://img.example/avatar.png",
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

    private static User user(UserStatus status) {
        return User.reconstitute(
                ID, null, "test@example.invalid", RegisterSource.GOOGLE, status, null, null, null);
    }
}
