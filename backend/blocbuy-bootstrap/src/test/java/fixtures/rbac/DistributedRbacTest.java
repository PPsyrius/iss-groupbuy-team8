package fixtures.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.SaManager;
import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.session.SaSession;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.config.SaTokenConfig;
import com.blocbuy.common.config.SessionStpInterface;
import com.blocbuy.common.constant.SessionRbac;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.common.web.GlobalExceptionHandler;
import com.blocbuy.common.web.Result;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.payment.service.AdminPaymentAppService;
import com.blocbuy.payment.web.AdminPaymentController;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ApplicationContext;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Real Sa-Token login and HTTP authorization without any identity repository in the assembly. */
@SpringBootTest(
        classes = DistributedRbacTest.Assembly.class,
        properties = {
            "blocbuy.topology=distributed",
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/placeholder",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class DistributedRbacTest {
    private static final String ADMIN = "/api/v1/admin/payments/attention";
    @Autowired private MockMvc mvc;
    @Autowired private ApplicationContext context;
    @MockitoBean private AdminPaymentAppService payments;

    @Test
    void adminSessionAuthorizesTheRealAdminEndpointWithoutIdentity() throws Exception {
        assertThat(context.getBeansOfType(RoleRepository.class)).isEmpty();
        assertThat(context.getBeansOfType(SessionStpInterface.class)).hasSize(1);
        when(payments.pageAttention(any(), any()))
                .thenReturn(new PageResult<>(List.of(), 0, 1, 20, null));
        var cookie = login("admin");
        var session =
                SaManager.getSaTokenDao()
                        .getSession(
                                SessionRbac.sessionId(
                                        StpUtil.getTokenName(),
                                        StpUtil.getLoginType(),
                                        UUID.fromString(
                                                StpUtil.getLoginIdByToken(cookie.getValue())
                                                        .toString())));
        var serializer = SaManager.getSaSerializerTemplate();
        var restored =
                serializer.stringToObject(serializer.objectToString(session), SaSession.class);
        assertThat(restored.get(SessionRbac.SNAPSHOT)).isEqualTo(session.get(SessionRbac.SNAPSHOT));
        mvc.perform(get(ADMIN).cookie(cookie)).andExpect(status().isOk());
    }

    @Test
    void buyerAndIncompleteAdminSessionsAreDeniedBeforeTheService() throws Exception {
        mvc.perform(get(ADMIN)).andExpect(status().isUnauthorized());
        mvc.perform(get(ADMIN).cookie(login("buyer"))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).cookie(login("no-permission"))).andExpect(status().isForbidden());
        mvc.perform(get(ADMIN).cookie(login("legacy"))).andExpect(status().isForbidden());
        verifyNoInteractions(payments);
    }

    @Test
    void internalRouteDoesNotRequireABrowserSession() throws Exception {
        mvc.perform(get("/internal/probe")).andExpect(status().isOk());
    }

    private jakarta.servlet.http.Cookie login(String role) throws Exception {
        return mvc.perform(get("/test/login").param("role", role))
                .andExpect(status().isOk())
                .andReturn()
                .getResponse()
                .getCookie("blocbuy-token");
    }

    @Configuration(proxyBeanMethods = false)
    @EnableAutoConfiguration
    @Import({
        SaTokenConfig.class,
        GlobalExceptionHandler.class,
        AdminPaymentController.class,
        TestEndpoints.class
    })
    static class Assembly {
        @Bean(initMethod = "init", destroyMethod = "destroy")
        @Primary
        SaTokenDao testSessionDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }

    /** Test-only identity-side session writer; never scanned by a production assembly. */
    @RestController
    static class TestEndpoints {
        @GetMapping("/test/login")
        Result<Void> login(@RequestParam String role) {
            UUID user = UUID.randomUUID();
            StpUtil.login(user);
            if (!"legacy".equals(role)) {
                var session =
                        new SaSession(
                                SessionRbac.sessionId(
                                        StpUtil.getTokenName(), StpUtil.getLoginType(), user));
                session.getDataMap()
                        .put(
                                SessionRbac.SNAPSHOT,
                                Map.of(
                                        "roles",
                                        List.of("buyer".equals(role) ? "buyer" : "admin"),
                                        "permissions",
                                        "admin".equals(role)
                                                ? List.of("payment:read")
                                                : List.of()));
                SaManager.getSaTokenDao()
                        .setSession(
                                session, StpUtil.getStpLogic().getSessionTimeoutByLoginId(user));
            }
            return Result.ok();
        }

        @GetMapping("/internal/probe")
        Result<Void> internal() {
            return Result.ok();
        }
    }
}
