package com.blocbuy.identity.web;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.identity.gateway.LoginSnapshotGateway;
import com.blocbuy.identity.service.LoginAuthorizationService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/** Browser-session adaptation: persist authorization before publishing any token to the browser. */
@Component
@RequiredArgsConstructor
public class LoginSessionPublisher {
    private final LoginAuthorizationService authorization;
    private final LoginSnapshotGateway snapshots;

    /** Establishes a fresh login; no token is exposed when authorization persistence fails. */
    public void login(UUID userId) {
        String refresh = snapshots.beginRefresh(userId);
        var snapshot = authorization.snapshot(userId);
        var logic = StpUtil.getStpLogic();
        var parameter = logic.createSaLoginParameter().setIsShare(false);
        // Know the new token even if createLoginSession fails after a partial Redis write.
        String token =
                logic.createTokenValue(
                        userId,
                        parameter.getDeviceType(),
                        parameter.getTimeout(),
                        parameter.getExtraData());
        parameter.setToken(token);
        try {
            // Unlike login(), this does not set a cookie or response header.
            logic.createLoginSession(userId, parameter);
            if (!snapshots.publish(
                    userId,
                    refresh,
                    snapshot.roles(),
                    snapshot.permissions(),
                    logic.getSessionTimeoutByLoginId(userId))) {
                throw new IllegalStateException(
                        "Login authorization refresh superseded or expired");
            }
            logic.setTokenValue(token, parameter);
        } catch (RuntimeException failure) {
            try {
                // Never log out the whole account: other devices must remain logged in.
                logic.logoutByTokenValue(token);
            } catch (RuntimeException cleanupFailure) {
                failure.addSuppressed(cleanupFailure);
                try {
                    logic.deleteTokenToIdMapping(token);
                } catch (RuntimeException mappingFailure) {
                    failure.addSuppressed(mappingFailure);
                }
            }
            throw failure;
        }
    }
}
