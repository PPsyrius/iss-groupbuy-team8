package com.blocbuy.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.blocbuy.identity.model.OAuthIdentity;
import com.blocbuy.identity.model.OAuthProvider;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** OAuth binding repository: lookup mapping, insert conversion, snapshot arguments. */
@ExtendWith(MockitoExtension.class)
class UserOAuthRepositoryImplTest {

    private static final UUID ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID USER_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");
    private static final Instant LAST_LOGIN = Instant.parse("2026-09-17T01:02:03Z");

    @Mock private SysUserOAuthMapper mapper;
    @InjectMocks private UserOAuthRepositoryImpl repository;

    @Test
    void findReturnsMappedIdentityAndPassesLookupArguments() {
        when(mapper.selectActiveByProviderAndSubject("google", "google-subject")).thenReturn(row());

        var result =
                repository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "google-subject");

        assertThat(result).isPresent();
        assertThat(result.orElseThrow().id()).isEqualTo(ID);
        verify(mapper).selectActiveByProviderAndSubject("google", "google-subject");
    }

    @Test
    void findReturnsEmptyWhenMapperReturnsNull() {
        when(mapper.selectActiveByProviderAndSubject("google", "missing")).thenReturn(null);

        var result = repository.findActiveByProviderAndSubject(OAuthProvider.GOOGLE, "missing");

        assertThat(result).isEmpty();
    }

    @Test
    void saveNewMapsIdentityToInsertRow() {
        OAuthIdentity identity = identity();

        repository.saveNew(identity);

        ArgumentCaptor<SysUserOAuthDO> captor = ArgumentCaptor.forClass(SysUserOAuthDO.class);
        verify(mapper).insert(captor.capture());
        SysUserOAuthDO row = captor.getValue();
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

    @Test
    void updateLoginSnapshotPassesAllIdentityFields() {
        OAuthIdentity identity = identity();

        repository.updateLoginSnapshot(identity);

        verify(mapper)
                .updateLoginSnapshot(
                        ID,
                        "buyer@example.com",
                        "Buyer",
                        "https://cdn.example.com/avatar.png",
                        "{\"sub\":\"google-subject\"}",
                        LAST_LOGIN);
    }

    private static OAuthIdentity identity() {
        return OAuthIdentity.reconstitute(
                ID,
                USER_ID,
                OAuthProvider.GOOGLE,
                "google-subject",
                "buyer@example.com",
                "Buyer",
                "https://cdn.example.com/avatar.png",
                "{\"sub\":\"google-subject\"}",
                LAST_LOGIN);
    }

    private static SysUserOAuthDO row() {
        SysUserOAuthDO row = UserOAuthConverter.toDO(identity());
        return row;
    }
}
