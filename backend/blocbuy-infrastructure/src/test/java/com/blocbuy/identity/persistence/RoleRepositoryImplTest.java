package com.blocbuy.identity.persistence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

/** RBAC repository: role resolution, assignment, role and permission delegation. */
@ExtendWith(MockitoExtension.class)
class RoleRepositoryImplTest {

    private static final UUID USER_ID = UUID.fromString("a0000000-0000-7000-8000-000000000001");
    private static final UUID ROLE_ID = UUID.fromString("b0000000-0000-7000-8000-000000000001");

    @Mock private RbacMapper mapper;
    @InjectMocks private RoleRepositoryImpl repository;

    @Test
    void findActiveRoleIdReturnsPresentAndPassesCode() {
        when(mapper.selectActiveRoleIdByCode("buyer")).thenReturn(ROLE_ID);

        assertThat(repository.findActiveRoleIdByCode("buyer")).contains(ROLE_ID);
        verify(mapper).selectActiveRoleIdByCode("buyer");
    }

    @Test
    void findActiveRoleIdReturnsEmptyWhenMapperReturnsNull() {
        when(mapper.selectActiveRoleIdByCode("missing")).thenReturn(null);

        assertThat(repository.findActiveRoleIdByCode("missing")).isEmpty();
    }

    @Test
    void assignRolePassesUserAndRoleIds() {
        repository.assignRole(USER_ID, ROLE_ID);

        verify(mapper).insertUserRole(USER_ID, ROLE_ID);
    }

    @Test
    void findRoleCodesReturnsMapperResultAndPassesUserId() {
        when(mapper.selectRoleCodesByUserId(USER_ID)).thenReturn(List.of("buyer", "seller"));

        assertThat(repository.findRoleCodesByUserId(USER_ID)).containsExactly("buyer", "seller");
        verify(mapper).selectRoleCodesByUserId(USER_ID);
    }

    @Test
    void findPermissionCodesReturnsMapperResultAndPassesUserId() {
        when(mapper.selectPermissionCodesByUserId(USER_ID))
                .thenReturn(List.of("product:read", "order:create"));

        assertThat(repository.findPermissionCodesByUserId(USER_ID))
                .containsExactly("product:read", "order:create");
        verify(mapper).selectPermissionCodesByUserId(USER_ID);
    }
}
