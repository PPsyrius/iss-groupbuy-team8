package com.blocbuy.service.commerce.identity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.model.UserProfile;
import com.blocbuy.identity.model.UserStatus;
import com.blocbuy.identity.repository.UserRepository;
import com.blocbuy.identity.service.UserAppService;
import com.blocbuy.identity.service.UserAppServiceImpl;
import com.blocbuy.identity.web.internal.InternalUserController;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockMvcClientHttpRequestFactory;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.client.RestClient;

/**
 * One set of identity answers, run against both {@link UserAppService} implementations (#468,
 * #469): the local one the monolith and identity-svc use, and commerce-svc's remote one talking to
 * the real {@link InternalUserController} and global exception handler in process. A caller cannot
 * tell which one it has.
 */
class UserAppServiceContractTest {

    private static final UUID ACTIVE = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID DISABLED = UUID.fromString("a0000000-0000-7000-8000-000000000002");
    private static final UUID EMAIL_PREFIX =
            UUID.fromString("a0000000-0000-7000-8000-000000000003");
    private static final UUID EMAIL_SHAPED =
            UUID.fromString("a0000000-0000-7000-8000-000000000004");
    private static final UUID UNKNOWN = UUID.fromString("a0000000-0000-7000-8000-000000000009");

    /** The same identity data behind both implementations. */
    private static UserAppService local() {
        UserRepository users = mock(UserRepository.class);
        List<User> known =
                List.of(
                        user(ACTIVE, UserStatus.ACTIVE, "Tester"),
                        user(DISABLED, UserStatus.DISABLED, "Gone"),
                        user(EMAIL_PREFIX, UserStatus.ACTIVE, "buyer"),
                        user(EMAIL_SHAPED, UserStatus.ACTIVE, "someone@example.invalid"));
        for (User user : known) {
            when(users.findById(user.id())).thenReturn(Optional.of(user));
        }
        when(users.findById(UNKNOWN)).thenReturn(Optional.empty());
        when(users.findAllById(Set.of(ACTIVE, DISABLED, EMAIL_PREFIX, EMAIL_SHAPED, UNKNOWN)))
                .thenReturn(known);
        return new UserAppServiceImpl(users);
    }

    private static UserAppService remote() {
        var identity =
                MockMvcBuilders.standaloneSetup(new InternalUserController(local()))
                        .setControllerAdvice(new GlobalExceptionHandler())
                        .build();
        return new RemoteUserAppService(
                RestClient.builder()
                        .baseUrl("http://identity-svc:8589")
                        .requestFactory(new MockMvcClientHttpRequestFactory(identity))
                        .build());
    }

    private static User user(UUID id, UserStatus status, String nickname) {
        return User.reconstitute(
                id,
                null,
                "buyer@example.invalid",
                RegisterSource.GOOGLE,
                status,
                new UserProfile(nickname, null),
                null);
    }

    /** The cases every implementation must satisfy. */
    abstract static class Contract {

        abstract UserAppService users();

        @Test
        void anActiveAccountPasses() {
            UserAppService users = users();
            assertThatCode(() -> users.requireActive(ACTIVE)).doesNotThrowAnyException();
        }

        @Test
        void anUnknownAccountIsNotFound() {
            assertRefusedWith(UNKNOWN, IdentityErrorCode.USER_NOT_FOUND);
        }

        @Test
        void aDisabledAccountIsDisabled() {
            assertRefusedWith(DISABLED, IdentityErrorCode.ACCOUNT_DISABLED);
        }

        @Test
        void anActiveAccountShowsItsNickname() {
            assertThat(users().publicNickname(ACTIVE)).contains("Tester");
        }

        @Test
        void anUnknownAccountShowsNoNickname() {
            assertThat(users().publicNickname(UNKNOWN)).isEmpty();
        }

        @Test
        void aDisabledAccountShowsNoNickname() {
            assertThat(users().publicNickname(DISABLED)).isEmpty();
        }

        @Test
        void anEmailPrefixNicknameIsNotShown() {
            assertThat(users().publicNickname(EMAIL_PREFIX)).isEmpty();
        }

        @Test
        void anEmailShapedNicknameIsNotShown() {
            assertThat(users().publicNickname(EMAIL_SHAPED)).isEmpty();
        }

        @Test
        void theBatchLookupShowsOnlyShowableNicknames() {
            assertThat(
                            users().publicNicknames(
                                            Set.of(
                                                    ACTIVE,
                                                    DISABLED,
                                                    EMAIL_PREFIX,
                                                    EMAIL_SHAPED,
                                                    UNKNOWN)))
                    .isEqualTo(Map.of(ACTIVE, "Tester"));
        }

        @Test
        void anEmptyBatchShowsNothing() {
            assertThat(users().publicNicknames(Set.of())).isEmpty();
        }

        private void assertRefusedWith(UUID id, IdentityErrorCode expected) {
            UserAppService users = users();
            assertThatThrownBy(() -> users.requireActive(id))
                    .isInstanceOf(BizException.class)
                    .extracting(ex -> ((BizException) ex).errorCode())
                    .isEqualTo(expected);
        }
    }

    @Nested
    class Local extends Contract {
        private final UserAppService users = local();

        @Override
        UserAppService users() {
            return users;
        }
    }

    @Nested
    class Remote extends Contract {
        private final UserAppService users = remote();

        @Override
        UserAppService users() {
            return users;
        }
    }
}
