package com.blocbuy.identity.web;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import cn.dev33.satoken.config.SaTokenConfig;
import cn.dev33.satoken.stp.StpLogic;
import cn.dev33.satoken.stp.StpUtil;
import cn.dev33.satoken.stp.parameter.SaLoginParameter;
import com.blocbuy.identity.gateway.LoginSnapshotGateway;
import com.blocbuy.identity.service.LoginAuthorizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class LoginSessionPublisherTest {
    private static final UUID USER = UUID.randomUUID();
    private static final String TOKEN = "new-token";
    private final LoginAuthorizationService authorization = mock(LoginAuthorizationService.class);
    private final LoginSnapshotGateway snapshots = mock(LoginSnapshotGateway.class);
    private final StpLogic logic = mock(StpLogic.class);
    private final LoginSessionPublisher publisher =
            new LoginSessionPublisher(authorization, snapshots);

    @BeforeEach
    void setup() {
        when(snapshots.beginRefresh(USER)).thenReturn("refresh");
        when(authorization.snapshot(USER))
                .thenReturn(
                        new LoginAuthorizationService.Snapshot(
                                List.of("admin"), List.of("payment:read")));
        when(logic.createSaLoginParameter()).thenReturn(new SaLoginParameter(new SaTokenConfig()));
        when(logic.createTokenValue(eq(USER), any(), anyLong(), any())).thenReturn(TOKEN);
        when(logic.getSessionTimeoutByLoginId(USER)).thenReturn(86400L);
        when(snapshots.publish(USER, "refresh", List.of("admin"), List.of("payment:read"), 86400L))
                .thenReturn(true);
    }

    @Test
    void publishesOnlyAfterTheSnapshotAndNeverReusesAnExistingToken() {
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);
            publisher.login(USER);
            var order = inOrder(logic, snapshots);
            order.verify(logic).createLoginSession(eq(USER), any());
            order.verify(snapshots)
                    .publish(USER, "refresh", List.of("admin"), List.of("payment:read"), 86400L);
            var parameter = org.mockito.ArgumentCaptor.forClass(SaLoginParameter.class);
            order.verify(logic).setTokenValue(eq(TOKEN), parameter.capture());
            assertThat(parameter.getValue().getToken()).isEqualTo(TOKEN);
            assertThat(parameter.getValue().getIsShare()).isFalse();
            verify(logic, never()).logoutByTokenValue(any());
        }
    }

    @Test
    void snapshotReadFailureCreatesNoToken() {
        when(authorization.snapshot(USER))
                .thenThrow(new IllegalStateException("database unavailable"));
        assertThatThrownBy(() -> publisher.login(USER)).hasMessage("database unavailable");
        verifyNoInteractions(logic);
    }

    @Test
    void supersededRefreshRevokesOnlyTheNewToken() {
        when(snapshots.publish(USER, "refresh", List.of("admin"), List.of("payment:read"), 86400L))
                .thenReturn(false);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);
            assertThatThrownBy(() -> publisher.login(USER)).hasMessageContaining("superseded");
            verify(logic).logoutByTokenValue(TOKEN);
            verify(logic, never()).setTokenValue(any(), any(SaLoginParameter.class));
            verify(logic, never()).logout(USER);
        }
    }

    @Test
    void partialTokenCreationIsAlsoCleanedUp() {
        when(logic.createLoginSession(eq(USER), any()))
                .thenThrow(new IllegalStateException("partial write"));
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);
            assertThatThrownBy(() -> publisher.login(USER)).hasMessage("partial write");
            verify(logic).logoutByTokenValue(TOKEN);
            verify(logic, never()).setTokenValue(any(), any(SaLoginParameter.class));
        }
    }

    @Test
    void failedCleanupFallsBackToRemovingTheMappingWithoutHidingTheCause() {
        var failure = new IllegalStateException("publication failed");
        when(snapshots.publish(USER, "refresh", List.of("admin"), List.of("payment:read"), 86400L))
                .thenThrow(failure);
        doThrow(new IllegalStateException("cleanup failed")).when(logic).logoutByTokenValue(TOKEN);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);
            assertThatThrownBy(() -> publisher.login(USER)).isSameAs(failure);
            verify(logic).deleteTokenToIdMapping(TOKEN);
            assertThat(failure.getSuppressed()).hasSize(1);
        }
    }

    @Test
    void completeRedisOutageStillNeverPublishesTheNewToken() {
        var failure = new IllegalStateException("publication failed");
        when(snapshots.publish(USER, "refresh", List.of("admin"), List.of("payment:read"), 86400L))
                .thenThrow(failure);
        doThrow(new IllegalStateException("cleanup failed")).when(logic).logoutByTokenValue(TOKEN);
        doThrow(new IllegalStateException("mapping failed"))
                .when(logic)
                .deleteTokenToIdMapping(TOKEN);
        try (var stp = mockStatic(StpUtil.class)) {
            stp.when(StpUtil::getStpLogic).thenReturn(logic);
            assertThatThrownBy(() -> publisher.login(USER)).isSameAs(failure);
            assertThat(failure.getSuppressed()).hasSize(2);
            verify(logic, never()).setTokenValue(any(), any(SaLoginParameter.class));
        }
    }
}
