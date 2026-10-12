package com.blocbuy.identity.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.blocbuy.identity.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class LoginAuthorizationServiceTest {
    private final RoleRepository roles = mock(RoleRepository.class);
    private final LoginAuthorizationService service = new LoginAuthorizationService(roles);

    @Test
    void returnsTheIdentityAuthorizationSnapshot() {
        UUID user = UUID.randomUUID();
        when(roles.findRoleCodesByUserId(user)).thenReturn(List.of("admin"));
        when(roles.findPermissionCodesByUserId(user)).thenReturn(List.of("payment:read"));
        var snapshot = service.snapshot(user);
        assertThat(snapshot.roles()).containsExactly("admin");
        assertThat(snapshot.permissions()).containsExactly("payment:read");
        var snapshotRoles = snapshot.roles();
        assertThatThrownBy(() -> snapshotRoles.add("buyer"))
                .isInstanceOf(UnsupportedOperationException.class);
    }
}
