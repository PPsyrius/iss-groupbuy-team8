package com.blocbuy.bootstrap;

import static org.hamcrest.Matchers.nullValue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import cn.dev33.satoken.stp.StpUtil;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.groupbuy.query.MyTeamView;
import com.blocbuy.groupbuy.service.GroupDealAppService;
import com.blocbuy.identity.repository.RoleRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real HTTP binding and Sa-Token session scoping for UC8 My Teams: the buyer id must come from the
 * session, never the wire, and out-of-range pagination must be rejected with 400 before any
 * business code runs. Only the application service and the RBAC repository are mocked; the
 * test-only session store never connects to a developer's Redis.
 */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/blocbuy_placeholder?currentSchema=domain",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(GroupDealControllerTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class GroupDealControllerTest {

    private static final String MY_TEAMS_PATH = "/api/v1/group-deals/me";
    private static final UUID BUYER = UUID.fromString("01990000-0000-7000-8000-000000000002");
    private static final UUID OTHER_BUYER = UUID.fromString("01990000-0000-7000-8000-000000000004");
    private static final String TOKEN_HEADER = "blocbuy-token";

    @Autowired private MockMvc mvc;
    @MockitoBean private GroupDealAppService groupDeals;
    @MockitoBean private RoleRepository roles;
    private final List<String> sessions = new ArrayList<>();

    @AfterEach
    void deleteOnlyTestSessions() {
        sessions.forEach(StpUtil::logoutByTokenValue);
    }

    @Test
    void anonymousMyTeamsIsRejectedBeforeBusinessCode() throws Exception {
        mvc.perform(get(MY_TEAMS_PATH))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("COMMON_401_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(groupDeals);
    }

    @ParameterizedTest
    @MethodSource("invalidPagination")
    void outOfRangePaginationIsRejectedBeforeBusinessCode(String query) throws Exception {
        String token = login(BUYER);
        mvc.perform(get(MY_TEAMS_PATH + query).header(TOKEN_HEADER, token))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(groupDeals);
    }

    static Stream<Arguments> invalidPagination() {
        return Stream.of(
                Arguments.of("?page=0"),
                Arguments.of("?page=-1"),
                Arguments.of("?size=0"),
                Arguments.of("?size=101"));
    }

    @Test
    void defaultsApplyAndEveryFieldRendersWhenPaginationParamsAreAbsent() throws Exception {
        when(groupDeals.pageMyTeams(PageQuery.of(null, null), BUYER))
                .thenReturn(PageResult.of(List.of(led(), joined()), 2, PageQuery.of(null, null)));
        String token = login(BUYER);
        mvc.perform(get(MY_TEAMS_PATH).header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.page").value(1))
                .andExpect(jsonPath("$.data.size").value(PageQuery.DEFAULT_SIZE))
                .andExpect(jsonPath("$.data.total").value(2))
                .andExpect(jsonPath("$.data.items[0].dealId").value(led().dealId().toString()))
                .andExpect(
                        jsonPath("$.data.items[0].productId").value(led().productId().toString()))
                .andExpect(jsonPath("$.data.items[0].productTitle").value("Mechanical keyboard"))
                .andExpect(jsonPath("$.data.items[0].productImage").value("kb.jpg"))
                .andExpect(jsonPath("$.data.items[0].dealPrice").value("9999999999999999.99"))
                .andExpect(jsonPath("$.data.items[0].currency").value("sgd"))
                .andExpect(jsonPath("$.data.items[0].targetSize").value(3))
                .andExpect(jsonPath("$.data.items[0].currentSize").value(3))
                .andExpect(jsonPath("$.data.items[0].status").value("succeeded"))
                .andExpect(jsonPath("$.data.items[0].expireTime").value("2026-09-21T10:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].successTime").value("2026-09-20T12:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].role").value("leader"))
                .andExpect(jsonPath("$.data.items[0].joinTime").value("2026-09-20T10:00:00Z"))
                .andExpect(jsonPath("$.data.items[0].orderId").value(led().orderId().toString()))
                .andExpect(jsonPath("$.data.items[1].productTitle").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].productImage").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].successTime").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].orderId").value(nullValue()))
                .andExpect(jsonPath("$.data.items[1].dealPrice").value("5.00"))
                .andExpect(jsonPath("$.data.items[1].role").value("member"))
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(groupDeals).pageMyTeams(PageQuery.of(null, null), BUYER);
    }

    /**
     * The buyer scope is the session identity — a {@code userId} on the wire is ignored, and
     * another logged-in user gets only their own page.
     */
    @Test
    void pageIsScopedToTheSessionBuyerNotTheWire() throws Exception {
        when(groupDeals.pageMyTeams(PageQuery.of(2, 5), OTHER_BUYER))
                .thenReturn(PageResult.of(List.of(), 0, PageQuery.of(2, 5)));
        String token = login(OTHER_BUYER);
        mvc.perform(
                        get(MY_TEAMS_PATH + "?page=2&size=5&userId=" + BUYER)
                                .header(TOKEN_HEADER, token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.total").value(0))
                .andExpect(jsonPath("$.data.items").isEmpty())
                .andExpect(header().string("Cache-Control", "no-store"));
        verify(groupDeals).pageMyTeams(PageQuery.of(2, 5), OTHER_BUYER);
    }

    private String login(UUID userId) {
        String token = StpUtil.getStpLogic().createLoginSession(userId.toString());
        sessions.add(token);
        return token;
    }

    private static MyTeamView led() {
        return new MyTeamView(
                UUID.fromString("01990000-0000-7000-8000-000000000003"),
                UUID.fromString("01990000-0000-7000-8000-000000000005"),
                "Mechanical keyboard",
                "kb.jpg",
                new BigDecimal("9999999999999999.99"),
                "sgd",
                3,
                3,
                "succeeded",
                Instant.parse("2026-09-21T10:00:00Z"),
                Instant.parse("2026-09-20T12:00:00Z"),
                "leader",
                Instant.parse("2026-09-20T10:00:00Z"),
                UUID.fromString("01990000-0000-7000-8000-000000000001"));
    }

    private static MyTeamView joined() {
        return new MyTeamView(
                UUID.fromString("01990000-0000-7000-8000-000000000006"),
                UUID.fromString("01990000-0000-7000-8000-000000000007"),
                null,
                null,
                new BigDecimal("5.00"),
                "sgd",
                3,
                1,
                "open",
                Instant.parse("2026-09-21T09:00:00Z"),
                null,
                "member",
                Instant.parse("2026-09-20T09:00:00Z"),
                null);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class SessionStore {
        @Bean(initMethod = "init", destroyMethod = "destroy")
        @Primary
        SaTokenDao testSessionDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }
}
