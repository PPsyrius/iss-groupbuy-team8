package com.blocbuy.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** OAuth binding mapping: null tolerance and full row-domain conversion with provider codes. */
class UserOAuthConverterTest {

    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final Instant LAST_LOGIN = Instant.parse("2026-09-17T01:02:03Z");

    @Test
    void toDomainNullMapsToNull() {
        assertThat(UserOAuthConverter.toDomain(null)).isNull();
    }

    @Test
    void toDomainMapsAllFields() {
        OAuthIdentity identity = UserOAuthConverter.toDomain(row());

        assertThat(identity.id()).isEqualTo(ID);
        assertThat(identity.userId()).isEqualTo(USER_ID);
        assertThat(identity.provider()).isEqualTo(OAuthProvider.GOOGLE);
        assertThat(identity.providerUserId()).isEqualTo("google-subject");
        assertThat(identity.email()).isEqualTo("buyer@example.com");
        assertThat(identity.displayName()).isEqualTo("Buyer");
        assertThat(identity.avatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(identity.rawProfile()).isEqualTo("{\"sub\":\"google-subject\"}");
        assertThat(identity.lastLoginTime()).isEqualTo(LAST_LOGIN);
    }

    @Test
    void toDoMapsAllFields() {
        OAuthIdentity identity =
                OAuthIdentity.reconstitute(
                        ID,
                        USER_ID,
                        OAuthProvider.GOOGLE,
                        "google-subject",
                        "buyer@example.com",
                        "Buyer",
                        "https://cdn.example.com/avatar.png",
                        "{\"sub\":\"google-subject\"}",
                        LAST_LOGIN);

        SysUserOAuthDO row = UserOAuthConverter.toDO(identity);

        assertThat(row.getId()).isEqualTo(ID);
        assertThat(row.getUserId()).isEqualTo(USER_ID);
        assertThat(row.getProvider()).isEqualTo("google");
        assertThat(row.getProviderUserId()).isEqualTo("google-subject");
        assertThat(row.getEmail()).isEqualTo("buyer@example.com");
        assertThat(row.getDisplayName()).isEqualTo("Buyer");
        assertThat(row.getAvatarUrl()).isEqualTo("https://cdn.example.com/avatar.png");
        assertThat(row.getRawProfile()).isEqualTo("{\"sub\":\"google-subject\"}");
        assertThat(row.getLastLoginTime()).isEqualTo(LAST_LOGIN);
    }

    private static SysUserOAuthDO row() {
        SysUserOAuthDO row = new SysUserOAuthDO();
        row.setId(ID);
        row.setUserId(USER_ID);
        row.setProvider("google");
        row.setProviderUserId("google-subject");
        row.setEmail("buyer@example.com");
        row.setDisplayName("Buyer");
        row.setAvatarUrl("https://cdn.example.com/avatar.png");
        row.setRawProfile("{\"sub\":\"google-subject\"}");
        row.setLastLoginTime(LAST_LOGIN);
        return row;
    }
}
