package com.blocbuy.identity.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Repository port for RBAC reads and user-role assignment (sys_role / sys_permission /
 * rel_user_role / rel_role_permission). Feeds both registration (default role grant) and the
 * Sa-Token {@code StpInterface} RBAC source. All queries filter soft-deleted rows.
 */
public interface RoleRepository {

    /** Active role id by stable machine code, e.g. {@code buyer}. */
    Optional<UUID> findActiveRoleIdByCode(String roleCode);

    /**
     * Grants a role to a user (insert into rel_user_role). Callers verify user and role exist and
     * are active in the same transaction (application-managed referential integrity, no FK).
     */
    void assignRole(UUID userId, UUID roleId);

    /** Role codes of a user, for Sa-Token {@code getRoleList}. */
    List<String> findRoleCodesByUserId(UUID userId);

    /** Permission codes of a user via role grants, for Sa-Token {@code getPermissionList}. */
    List<String> findPermissionCodesByUserId(UUID userId);
}
