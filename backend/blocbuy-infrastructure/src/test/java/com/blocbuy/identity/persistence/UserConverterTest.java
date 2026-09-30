package com.blocbuy.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.identity.model.RegisterSource;
import com.blocbuy.identity.model.User;
import com.blocbuy.identity.model.UserProfile;
import com.blocbuy.identity.model.UserStatus;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** User mapping: null tolerance and full row-domain conversion with enum codes. */
class UserConverterTest {

    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final Instant LAST_LOGIN = Instant.parse("2026-09-17T01:02:03Z");

    @Test
    void toDomainNullMapsToNull() {
        assertThat(UserConverter.toDomain(null)).isNull();
    }

    @Test
    void toDomainMapsAllFields() {
        User user = UserConverter.toDomain(row());

        assertThat(user.id()).isEqualTo(ID);
        assertThat(user.username()).isEqualTo("buyer");
        assertThat(user.email()).isEqualTo("buyer@example.com");
        assertThat(user.registerSource()).isEqualTo(RegisterSource.GOOGLE);
        assertThat(user.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(user.nickname()).isEqualTo("Buyer");
        assertThat(user.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(user.lastLoginTime()).isEqualTo(LAST_LOGIN);
    }

    @Test
    void toDoMapsAllFields() {
        User user =
                User.reconstitute(
                        ID,
                        "buyer",
                        "buyer@example.com",
                        RegisterSource.GOOGLE,
                        UserStatus.DISABLED,
                        new UserProfile("Buyer", "https://cdn.example.com/avatar.png"),
                        LAST_LOGIN);

        SysUserDO row = UserConverter.toDO(user);

        assertThat(row.getId()).isEqualTo(ID);
        assertThat(row.getUsername()).isEqualTo("buyer");
        assertThat(row.getEmail()).isEqualTo("buyer@example.com");
        assertThat(row.getRegisterSource()).isEqualTo("google");
        assertThat(row.getStatus()).isEqualTo("disabled");
        assertThat(row.getNickname()).isEqualTo("Buyer");
        assertThat(row.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(row.getLastLoginTime()).isEqualTo(LAST_LOGIN);
    }

    private static SysUserDO row() {
        SysUserDO row = new SysUserDO();
        row.setId(ID);
        row.setUsername("buyer");
        row.setEmail("buyer@example.com");
        row.setRegisterSource("google");
        row.setStatus("disabled");
        row.setNickname("Buyer");
        row.setAvatarUrl("https://cdn.example.com/avatar.png");
        row.setLastLoginTime(LAST_LOGIN);
        return row;
    }
}
