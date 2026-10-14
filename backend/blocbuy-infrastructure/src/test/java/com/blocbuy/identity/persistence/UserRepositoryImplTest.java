package com.blocbuy.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.identity.exception.IdentityErrorCode;
import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.model.UserProfile;
import com.blocbuy.identity.model.UserStatus;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** User repository: lookup mapping, insert conversion, last-login update with not-found guard. */
@ExtendWith(MockitoExtension.class)
class UserRepositoryImplTest {

    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final Instant LAST_LOGIN = Instant.parse("2026-09-17T01:02:03Z");

    @Mock private SysUserMapper mapper;
    @InjectMocks private UserRepositoryImpl repository;

    @Test
    void findByIdReturnsMappedUserAndPassesId() {
        when(mapper.selectActiveById(ID)).thenReturn(row());

        var result = repository.findById(ID);

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().email()).isEqualTo("buyer@example.com");
        verify(mapper).selectActiveById(ID);
    }

    @Test
    void findByIdReturnsEmptyWhenMapperReturnsNull() {
        when(mapper.selectActiveById(ID)).thenReturn(null);

        assertThat(repository.findById(ID)).isEmpty();
    }

    @Test
    void findAllByIdConvertsEveryRowFromOneSelect() {
        when(mapper.selectActiveByIds(Set.of(ID))).thenReturn(List.of(row()));

        var result = repository.findAllById(Set.of(ID));

        assertThat(result).extracting(User::id).containsExactly(ID);
        verify(mapper).selectActiveByIds(Set.of(ID));
    }

    @Test
    void findAllByIdOfAnEmptySetSkipsTheSelect() {
        assertThat(repository.findAllById(Set.of())).isEmpty();
        verify(mapper, never()).selectActiveByIds(any());
    }

    @Test
    void findActiveByEmailReturnsMappedUserAndPassesEmail() {
        when(mapper.selectActiveByEmail("buyer@example.com")).thenReturn(row());

        var result = repository.findActiveByEmail("buyer@example.com");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(ID);
        verify(mapper).selectActiveByEmail("buyer@example.com");
    }

    @Test
    void findActiveByEmailReturnsEmptyWhenMapperReturnsNull() {
        when(mapper.selectActiveByEmail("missing@example.com")).thenReturn(null);

        assertThat(repository.findActiveByEmail("missing@example.com")).isEmpty();
    }

    @Test
    void saveNewMapsUserToInsertRow() {
        repository.saveNew(user());

        ArgumentCaptor<SysUserDO> captor = ArgumentCaptor.forClass(SysUserDO.class);
        verify(mapper).insert(captor.capture());
        SysUserDO row = captor.getValue();
        assertThat(row.getId()).isEqualTo(ID);
        assertThat(row.getUsername()).isEqualTo("buyer");
        assertThat(row.getEmail()).isEqualTo("buyer@example.com");
        assertThat(row.getRegisterSource()).isEqualTo("google");
        assertThat(row.getStatus()).isEqualTo("active");
        assertThat(row.getNickname()).isEqualTo("Buyer");
        assertThat(row.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(row.getLastLoginTime()).isEqualTo(LAST_LOGIN);
    }

    @Test
    void updateLastLoginSucceedsWhenOneRowIsUpdated() {
        when(mapper.updateLastLogin(ID, LAST_LOGIN)).thenReturn(1);

        repository.updateLastLogin(user());

        verify(mapper).updateLastLogin(ID, LAST_LOGIN);
    }

    @Test
    void updateLastLoginThrowsUserNotFoundWhenNoRowIsUpdated() {
        when(mapper.updateLastLogin(ID, LAST_LOGIN)).thenReturn(0);
        User user = user();

        assertThatThrownBy(() -> repository.updateLastLogin(user))
                .isInstanceOf(BizException.class)
                .extracting(ex -> ((BizException) ex).errorCode())
                .isEqualTo(IdentityErrorCode.USER_NOT_FOUND);
    }

    private static User user() {
        return User.reconstitute(
                ID,
                "buyer",
                "buyer@example.com",
                RegisterSource.GOOGLE,
                UserStatus.ACTIVE,
                new UserProfile("Buyer", "https://cdn.example.com/avatar.png"),
                LAST_LOGIN);
    }

    private static SysUserDO row() {
        return UserConverter.toDO(user());
    }
}
