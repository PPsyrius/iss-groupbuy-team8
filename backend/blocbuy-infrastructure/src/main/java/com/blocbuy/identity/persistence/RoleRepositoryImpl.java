package com.blocbuy.identity.persistence;

import com.blocbuy.identity.repository.RoleRepository;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

/**
 * MyBatis-backed {@link RoleRepository}; the RBAC source behind Sa-Token's {@code StpInterface} and
 * the default-role grant on registration.
 */
@Repository
@RequiredArgsConstructor
public class RoleRepositoryImpl implements RoleRepository {

    private final RbacMapper rbacMapper;

    @Override
    public Optional<UUID> findActiveRoleIdByCode(String roleCode) {
        return Optional.ofNullable(rbacMapper.selectActiveRoleIdByCode(roleCode));
    }

    @Override
    public void assignRole(UUID userId, UUID roleId) {
        rbacMapper.insertUserRole(userId, roleId);
    }

    @Override
    public List<String> findRoleCodesByUserId(UUID userId) {
        return rbacMapper.selectRoleCodesByUserId(userId);
    }

    @Override
    public List<String> findPermissionCodesByUserId(UUID userId) {
        return rbacMapper.selectPermissionCodesByUserId(userId);
    }
}
