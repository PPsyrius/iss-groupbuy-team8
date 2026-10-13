package com.blocbuy.common.config;

import cn.dev33.satoken.stp.StpInterface;
import com.blocbuy.identity.repository.RoleRepository;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * RBAC source for Sa-Token (D7): roles and permissions are loaded from the identity SQL tables
 * (sys_role / sys_permission / rel_user_role / rel_role_permission) via the {@link RoleRepository}
 * port. Permission identifiers use stable names like {@code order:read}, {@code order:cancel},
 * {@code catalog:product:write}.
 */
@Slf4j
@RequiredArgsConstructor
public class StpInterfaceImpl implements StpInterface {

    private final RoleRepository roleRepository;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        UUID userId = parseLoginId(loginId);
        return userId == null ? List.of() : roleRepository.findPermissionCodesByUserId(userId);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        UUID userId = parseLoginId(loginId);
        return userId == null ? List.of() : roleRepository.findRoleCodesByUserId(userId);
    }

    /** Login ids are local user UUIDs; anything else yields empty RBAC, never an exception. */
    private static UUID parseLoginId(Object loginId) {
        try {
            return UUID.fromString(String.valueOf(loginId));
        } catch (IllegalArgumentException ex) {
            log.warn("unparseable sa-token login id, denying RBAC lookup");
            return null;
        }
    }
}
