package com.blocbuy.common.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.SessionRbac;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

class SessionStpInterfaceTest {
    private static final UUID USER = UUID.randomUUID();
    private final SaTokenDao sessions = mock(SaTokenDao.class);
    private final SessionStpInterface rbac = new SessionStpInterface(sessions);

    @Test
    void roleAndPermissionChecksUseOneSnapshotPerRequest() {
        SaSession session = new SaSession("test");
        session.getDataMap()
                .put(
                        SessionRbac.SNAPSHOT,
                        Map.of("roles", List.of("admin"), "permissions", List.of()));
        when(sessions.getSession(anyString())).thenReturn(session);
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginType).thenReturn("login");
            RequestContextHolder.setRequestAttributes(
                    new ServletRequestAttributes(new MockHttpServletRequest()));
            try {
                assertThat(rbac.getRoleList(USER, "login")).containsExactly("admin");
                session.getDataMap()
                        .put(
                                SessionRbac.SNAPSHOT,
                                Map.of(
                                        "roles",
                                        List.of("buyer"),
                                        "permissions",
                                        List.of("payment:read")));
                assertThat(rbac.getPermissionList(USER, "login")).isEmpty();
                verify(sessions, times(1)).getSession(anyString());
                RequestContextHolder.setRequestAttributes(
                        new ServletRequestAttributes(new MockHttpServletRequest()));
                assertThat(rbac.getRoleList(USER, "login")).containsExactly("buyer");
                assertThat(rbac.getPermissionList(USER, "login")).containsExactly("payment:read");
                verify(sessions, times(2)).getSession(anyString());
            } finally {
                RequestContextHolder.resetRequestAttributes();
            }
        }
    }

    @Test
    void readsBothListsFromTheSnapshot() {
        SaSession session = new SaSession("test");
        session.set(
                SessionRbac.SNAPSHOT,
                Map.of("roles", List.of("admin"), "permissions", List.of("payment:read")));
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginType).thenReturn("login");
            when(sessions.getSession(anyString())).thenReturn(session);
            assertThat(rbac.getRoleList(USER, "login")).containsExactly("admin");
            assertThat(rbac.getPermissionList(USER, "login")).containsExactly("payment:read");
        }
    }

    @Test
    void missingAndMalformedSnapshotsDenyAccess() {
        SaSession session = new SaSession("test");
        try (MockedStatic<StpUtil> stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getLoginType).thenReturn("login");
            assertThat(rbac.getRoleList(USER, "login")).isEmpty();
            when(sessions.getSession(anyString())).thenReturn(session);
            assertThat(rbac.getRoleList(USER, "login")).isEmpty();
            session.set(SessionRbac.SNAPSHOT, "invalid");
            assertThat(rbac.getRoleList(USER, "login")).isEmpty();
            for (Object invalid :
                    List.of("admin", List.of("admin", 1), List.of(""), List.of(" "))) {
                session.set(
                        SessionRbac.SNAPSHOT,
                        Map.of("roles", invalid, "permissions", List.of("catalog:product:write")));
                assertThat(rbac.getRoleList(USER, "login")).isEmpty();
                assertThat(rbac.getPermissionList(USER, "login")).isEmpty();
                session.set(
                        SessionRbac.SNAPSHOT,
                        Map.of("roles", List.of("admin"), "permissions", invalid));
                assertThat(rbac.getRoleList(USER, "login")).isEmpty();
                assertThat(rbac.getPermissionList(USER, "login")).isEmpty();
            }
            session.set(SessionRbac.SNAPSHOT, Map.of("permissions", List.of()));
            assertThat(rbac.getRoleList(USER, "login")).isEmpty();
            assertThat(rbac.getPermissionList(USER, "login")).isEmpty();
            session.set(SessionRbac.SNAPSHOT, Map.of("permissions", List.of("payment:read")));
            assertThat(rbac.getPermissionList(USER, "login")).isEmpty();
            session.set(SessionRbac.SNAPSHOT, Map.of("roles", List.of("admin")));
            assertThat(rbac.getRoleList(USER, "login")).isEmpty();
            session.set(SessionRbac.SNAPSHOT, Map.of("roles", List.of(), "permissions", List.of()));
            assertThat(rbac.getRoleList(USER, "login")).isEmpty();
            assertThat(rbac.getPermissionList(USER, "login")).isEmpty();
            assertThat(rbac.getRoleList("invalid", "login")).isEmpty();
            assertThat(rbac.getRoleList(null, "login")).isEmpty();
            assertThat(rbac.getRoleList(USER, "other")).isEmpty();
        }
    }
}
