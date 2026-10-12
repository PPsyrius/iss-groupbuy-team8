package com.blocbuy.common.config;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpInterface;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.constant.SessionRbac;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;

/** Fail-closed RBAC from identity's shared account-session snapshot, without identity SQL. */
@RequiredArgsConstructor
public class SessionStpInterface implements StpInterface {
    private static final Snapshot EMPTY = new Snapshot(List.of(), List.of());
    private final SaTokenDao sessions;

    @Override
    public List<String> getPermissionList(Object loginId, String loginType) {
        return read(loginId, loginType, SessionRbac.PERMISSIONS);
    }

    @Override
    public List<String> getRoleList(Object loginId, String loginType) {
        return read(loginId, loginType, SessionRbac.ROLES);
    }

    private List<String> read(Object loginId, String loginType, String key) {
        if (!StpUtil.getLoginType().equals(loginType)) {
            return List.of();
        }
        UUID userId;
        try {
            userId = UUID.fromString(String.valueOf(loginId));
        } catch (IllegalArgumentException ex) {
            return List.of();
        }
        Snapshot snapshot = snapshot(userId, loginType);
        return SessionRbac.ROLES.equals(key) ? snapshot.roles() : snapshot.permissions();
    }

    private Snapshot snapshot(UUID userId, String loginType) {
        String id = SessionRbac.sessionId(StpUtil.getTokenName(), loginType, userId);
        RequestAttributes request = RequestContextHolder.getRequestAttributes();
        String cacheKey = SessionStpInterface.class.getName() + "." + id;
        if (request != null
                && request.getAttribute(cacheKey, RequestAttributes.SCOPE_REQUEST)
                        instanceof Snapshot cached) {
            return cached;
        }
        Snapshot loaded = load(id);
        if (request != null) {
            // Role + permission checks must never combine two snapshots during a refresh.
            request.setAttribute(cacheKey, loaded, RequestAttributes.SCOPE_REQUEST);
        }
        return loaded;
    }

    private Snapshot load(String id) {
        SaSession session = sessions.getSession(id);
        if (session == null || !(session.get(SessionRbac.SNAPSHOT) instanceof Map<?, ?> snapshot)) {
            return EMPTY;
        }
        if (!validList(snapshot.get(SessionRbac.ROLES))
                || !validList(snapshot.get(SessionRbac.PERMISSIONS))) {
            return EMPTY;
        }
        return new Snapshot(
                strings(snapshot.get(SessionRbac.ROLES)),
                strings(snapshot.get(SessionRbac.PERMISSIONS)));
    }

    private static List<String> strings(Object value) {
        return ((List<?>) value).stream().map(String.class::cast).toList();
    }

    private static boolean validList(Object value) {
        return value instanceof List<?> values
                && values.stream().allMatch(item -> item instanceof String text && !text.isBlank());
    }

    private record Snapshot(List<String> roles, List<String> permissions) {
        private Snapshot {
            roles = List.copyOf(roles);
            permissions = List.copyOf(permissions);
        }
    }
}
