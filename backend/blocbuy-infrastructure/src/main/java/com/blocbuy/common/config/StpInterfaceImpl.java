package com.blocbuy.common.config;

import cn.dev33.satoken.stp.StpInterface;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * RBAC source for Sa-Token (D7): roles and permissions are loaded from the identity SQL tables.
 *
 * <p>Skeleton: returns empty lists until the identity schema lands (V2 migration + UserAuthMapper).
 * TODO(TEAM8-identity): implement via identity persistence once the user/role tables exist.
 * Permission identifiers use stable names like {@code order:read}, {@code order:cancel},
 * {@code catalog:product:write}.
 */
@Slf4j
@Component
public class StpInterfaceImpl implements StpInterface {

  @Override
  public List<String> getPermissionList(Object loginId, String loginType) {
    log.debug("permission lookup skeleton, loginId={}", loginId);
    return List.of();
  }

  @Override
  public List<String> getRoleList(Object loginId, String loginType) {
    log.debug("role lookup skeleton, loginId={}", loginId);
    return List.of();
  }
}
