package com.blocbuy.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import com.blocbuy.identity.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

/** Sa-Token RBAC source: identity tables via the RoleRepository port; garbage ids deny quietly. */
class StpInterfaceImplTest {

    private final RoleRepository roleRepository = mock(RoleRepository.class);
    private final StpInterfaceImpl stpInterface = new StpInterfaceImpl(roleRepository);

    @Test
    void loadsRolesAndPermissionsForUser() {
        UUID userId = UUID.randomUUID();
        when(roleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of("buyer"));
        when(roleRepository.findPermissionCodesByUserId(userId))
                .thenReturn(List.of("group-deal:create"));

        assertThat(stpInterface.getRoleList(userId.toString(), "login")).containsExactly("buyer");
        assertThat(stpInterface.getPermissionList(userId.toString(), "login"))
                .containsExactly("group-deal:create");
    }

    @Test
    void unparseableLoginIdYieldsEmptyRbac() {
        assertThat(stpInterface.getRoleList("not-a-uuid", "login")).isEmpty();
        assertThat(stpInterface.getPermissionList("not-a-uuid", "login")).isEmpty();
    }

    @Test
    void nullLoginIdYieldsEmptyRbacWithoutRepositoryCall() {
        assertThat(stpInterface.getRoleList(null, "login")).isEmpty();
        assertThat(stpInterface.getPermissionList(null, "login")).isEmpty();
        verifyNoInteractions(roleRepository);
    }

    @Test
    void uuidObjectLoginIdLoadsRbac() {
        UUID userId = UUID.randomUUID();
        when(roleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of("buyer"));
        when(roleRepository.findPermissionCodesByUserId(userId))
                .thenReturn(List.of("group-deal:create"));

        assertThat(stpInterface.getRoleList(userId, "login")).containsExactly("buyer");
        assertThat(stpInterface.getPermissionList(userId, "login"))
                .containsExactly("group-deal:create");
    }

    @Test
    void emptyRepositoryListsPassThrough() {
        UUID userId = UUID.randomUUID();
        when(roleRepository.findRoleCodesByUserId(userId)).thenReturn(List.of());
        when(roleRepository.findPermissionCodesByUserId(userId)).thenReturn(List.of());

        assertThat(stpInterface.getRoleList(userId.toString(), "login")).isEmpty();
        assertThat(stpInterface.getPermissionList(userId.toString(), "login")).isEmpty();
    }

    @Test
    void repositoryExceptionPropagates() {
        UUID userId = UUID.randomUUID();
        when(roleRepository.findRoleCodesByUserId(userId))
                .thenThrow(new IllegalStateException("db down"));

        String loginId = userId.toString();
        assertThatThrownBy(() -> stpInterface.getRoleList(loginId, "login"))
                .isInstanceOf(IllegalStateException.class);
    }
}
