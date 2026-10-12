package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.query.ShareInviteView;
import com.blocbuy.groupbuy.service.ShareInviteAppService;
import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.LoginAuthorizationService;
import com.blocbuy.identity.service.LoginAuthorizationService.Snapshot;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** UC7 mint endpoint over real HTTP/session; the application service is mocked. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/placeholder",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(AuthControllerTest.SessionStore.class)
@DirtiesContext
class ShareInviteControllerTest {
    private static final UUID USER = UUID.fromString("00000000-0000-4000-8000-000000000001");
    private static final UUID DEAL = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final String REF = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String INVITES = "/api/v1/group-deals/" + DEAL + "/invites";

    @Autowired private MockMvc mvc;
    @MockitoBean private ShareInviteAppService invites;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private RoleRepository roles;
    @MockitoBean private LoginAuthorizationService authorization;

    private Cookie login() throws Exception {
        when(authorization.snapshot(USER))
                .thenReturn(new Snapshot(java.util.List.of(), java.util.List.of()));
        when(oauth.loginWithGoogle(any(), any(), any()))
                .thenReturn(new OAuthLoginResult(USER, "Local buyer", null));
        var result =
                mvc.perform(
                                get("/api/v1/auth/oauth2/google/callback")
                                        .param("code", "test")
                                        .param("state", "test")
                                        .cookie(new Cookie("blocbuy-oauth-state", "test")))
                        .andExpect(status().isFound())
                        .andReturn();
        Cookie cookie = result.getResponse().getCookie("blocbuy-token");
        assertThat(cookie).isNotNull();
        return cookie;
    }

    @Test
    void aMemberGetsTheirInviteInTheResultEnvelope() throws Exception {
        Instant expiry = Instant.parse("2026-09-26T10:00:00Z");
        when(invites.mint(USER, DEAL)).thenReturn(new ShareInviteView(REF, DEAL, PRODUCT, expiry));

        mvc.perform(post(INVITES).cookie(login()).header("X-BlocBuy-Request", "invite"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.ref").value(REF))
                .andExpect(jsonPath("$.data.dealId").value(DEAL.toString()))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-09-26T10:00:00Z"));
        verify(invites).mint(USER, DEAL);
    }

    @Test
    void anonymousCannotMint() throws Exception {
        mvc.perform(post(INVITES).header("X-BlocBuy-Request", "invite"))
                .andExpect(status().isUnauthorized());
        verifyNoInteractions(invites);
    }

    @Test
    void theCsrfHeaderIsRequired() throws Exception {
        Cookie cookie = login();
        mvc.perform(post(INVITES).cookie(cookie)).andExpect(status().isBadRequest());
        mvc.perform(post(INVITES).cookie(cookie).header("X-BlocBuy-Request", "checkout"))
                .andExpect(status().isForbidden());
        verifyNoInteractions(invites);
    }

    @Test
    void aNonMemberSeesTheSame404AsAMissingTeam() throws Exception {
        when(invites.mint(USER, DEAL))
                .thenThrow(new BizException(GroupbuyErrorCode.GROUPBUY_PLACEHOLDER));

        mvc.perform(post(INVITES).cookie(login()).header("X-BlocBuy-Request", "invite"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("GROUPBUY_404_001"));
    }

    @Test
    void aClosedTeamIsAConflict() throws Exception {
        when(invites.mint(USER, DEAL)).thenThrow(new BizException(GroupbuyErrorCode.DEAL_CLOSED));

        mvc.perform(post(INVITES).cookie(login()).header("X-BlocBuy-Request", "invite"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("GROUPBUY_409_002"));
    }

    @Test
    void aMalformedTeamIdIsABadRequest() throws Exception {
        mvc.perform(
                        post("/api/v1/group-deals/not-a-uuid/invites")
                                .cookie(login())
                                .header("X-BlocBuy-Request", "invite"))
                .andExpect(status().isBadRequest());
        verifyNoInteractions(invites);
    }
}
