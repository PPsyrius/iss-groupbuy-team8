package com.blocbuy.identity.persistence;

import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for the identity RBAC tables (sys_role, sys_permission, rel_user_role,
 * rel_role_permission). Read methods return stable codes only; joins stay inside the identity
 * domain. All queries filter soft-deleted rows on every joined table.
 */
@Mapper
public interface RbacMapper {

    /** Active role id by stable machine code. */
    UUID selectActiveRoleIdByCode(@Param("roleCode") String roleCode);

    /** Grants a role (rel_user_role insert); id comes from the database uuidv7() default. */
    int insertUserRole(@Param("userId") UUID userId, @Param("roleId") UUID roleId);

    /** Role codes of a user — Sa-Token {@code getRoleList} source. */
    List<String> selectRoleCodesByUserId(@Param("userId") UUID userId);

    /** Permission codes of a user via role grants — Sa-Token {@code getPermissionList} source. */
    List<String> selectPermissionCodesByUserId(@Param("userId") UUID userId);
}
