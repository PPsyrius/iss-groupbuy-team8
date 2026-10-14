package com.blocbuy.identity.persistence;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

/**
 * MyBatis mapper for sys_user. All queries filter {@code deleted = false}; updates carry an
 * explicit WHERE and report affected rows. Parameters use {@code #{}} binding only.
 */
@Mapper
public interface SysUserMapper {

    /** Active user by primary key. */
    SysUserDO selectActiveById(@Param("id") UUID id);

    /** Active users among these ids, in one statement; callers never pass an empty collection. */
    List<SysUserDO> selectActiveByIds(@Param("ids") Collection<UUID> ids);

    /** Active user by canonical email (OAuth account-resolution lookup). */
    SysUserDO selectActiveByEmail(@Param("email") String email);

    /** Inserts one row; the id is supplied by the domain (UUIDv7), not the database default. */
    int insert(SysUserDO user);

    /** Stamps last successful login; returns affected rows (0 = user gone mid-request). */
    int updateLastLogin(@Param("id") UUID id, @Param("lastLoginTime") Instant lastLoginTime);
}
