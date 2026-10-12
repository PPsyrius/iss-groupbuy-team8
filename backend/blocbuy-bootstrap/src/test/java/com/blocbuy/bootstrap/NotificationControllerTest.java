package com.blocbuy.bootstrap;

import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.notification.model.Notification;
import com.blocbuy.notification.model.NotificationType;
import com.blocbuy.notification.service.NotificationAppService;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
                    "spring.datasource.url=jdbc:postgresql://localhost:1/unused",
            "spring.datasource.username=test", "spring.datasource.password=test",
            "spring.data.redis.host=localhost", "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(OrderControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationControllerTest {
    private static final String PATH = "/api/v1/notifications";
    private static final UUID USER = UUID.randomUUID();
    @Autowired private MockMvc mvc;
    @MockitoBean private NotificationAppService notifications;
    @MockitoBean private RoleRepository roles;
    private String token;

    @AfterEach
    void cleanup() {
        if (token != null) {
            StpUtil.logoutByTokenValue(token);
        }
    }

    @Test
    void anonymousRequestNeverReachesTheService() throws Exception {
        mvc.perform(get(PATH)).andExpect(status().isUnauthorized());
        verifyNoInteractions(notifications);
    }

    @Test
    void defaultsAreOwnerScopedAndExcludeInternalIdentifiers() throws Exception {
        var item =
                new Notification(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        USER,
                        NotificationType.PAYMENT_SUCCEEDED,
                        UUID.randomUUID(),
                        "Payment succeeded",
                        "Your payment succeeded.",
                        Instant.parse("2026-10-13T00:00:00Z"));
        when(notifications.pageMine(PageQuery.of(null, null), USER))
                .thenReturn(PageResult.of(List.of(item), 1, PageQuery.of(null, null)));
        login();
        mvc.perform(
                        get(PATH)
                                .param("userId", UUID.randomUUID().toString())
                                .header("blocbuy-token", token))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(20))
                .andExpect(jsonPath("$.data.items[0].id").value(item.id().toString()))
                .andExpect(jsonPath("$.data.items[0].type").value("PaymentSucceeded"))
                .andExpect(jsonPath("$.data.items[0].userId").doesNotExist())
                .andExpect(jsonPath("$.data.items[0].eventId").doesNotExist());
    }

    @Test
    void acceptsTheMaximumPageSizeAndEmptyResults() throws Exception {
        var query = new PageQuery(2, 100);
        when(notifications.pageMine(query, USER)).thenReturn(PageResult.of(List.of(), 0, query));
        login();
        mvc.perform(
                        get(PATH)
                                .param("page", "2")
                                .param("size", "100")
                                .header("blocbuy-token", token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(jsonPath("$.data.total").value(0));
    }

    @ParameterizedTest
    @ValueSource(
            strings = {
                "?page=0",
                "?page=-1",
                "?size=0",
                "?size=101",
                "?page=bad",
                "?page=2147483648"
            })
    void invalidPaginationNeverReachesTheService(String query) throws Exception {
        login();
        mvc.perform(get(PATH + query).header("blocbuy-token", token))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(notifications);
    }

    private void login() {
        token = StpUtil.getStpLogic().createLoginSession(USER.toString());
    }
}
