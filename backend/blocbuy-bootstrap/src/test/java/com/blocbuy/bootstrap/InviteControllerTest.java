package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.common.exception.BizException;
import com.blocbuy.common.exception.CommonErrorCode;
import com.blocbuy.groupbuy.exception.GroupbuyErrorCode;
import com.blocbuy.groupbuy.query.ResolvedInviteView;
import com.blocbuy.groupbuy.service.ShareInviteAppService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/** UC7 invite resolve over real HTTP and Sa-Token wiring; the application service is mocked. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://localhost:1/placeholder",
            "spring.datasource.username=test",
            "spring.datasource.password=test",
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "blocbuy.web.client-address-header=CloudFront-Viewer-Address"
        })
@AutoConfigureMockMvc
@DirtiesContext
class InviteControllerTest {
    private static final UUID DEAL = UUID.fromString("00000000-0000-4000-8000-000000000010");
    private static final UUID PRODUCT = UUID.fromString("00000000-0000-4000-8000-000000000011");
    private static final String REF = "AAAAAAAAAAAAAAAAAAAAAA";
    private static final String INVITE = "/api/v1/invites/" + REF;
    private static final String VIEWER = "CloudFront-Viewer-Address";
    private static final Instant EXPIRY = Instant.parse("2026-10-01T10:00:00Z");

    @Autowired private MockMvc mvc;
    @Autowired private ObjectMapper json;
    @MockitoBean private ShareInviteAppService invites;

    @Test
    void anAnonymousVisitorResolvesAnInviteInTheResultEnvelope() throws Exception {
        when(invites.resolve(REF, "198.51.100.10"))
                .thenReturn(new ResolvedInviteView(DEAL, PRODUCT, "Sarah Tan", EXPIRY));

        mvc.perform(get(INVITE).header(VIEWER, "198.51.100.10:46532"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("COMMON_200_000"))
                .andExpect(jsonPath("$.data.dealId").value(DEAL.toString()))
                .andExpect(jsonPath("$.data.productId").value(PRODUCT.toString()))
                .andExpect(jsonPath("$.data.sharerNickname").value("Sarah Tan"))
                .andExpect(jsonPath("$.data.expiresAt").value("2026-10-01T10:00:00Z"));
    }

    @Test
    void theBodyCarriesExactlyTheFourDisplayFieldsAndANullNickname() throws Exception {
        when(invites.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedInviteView(DEAL, PRODUCT, null, EXPIRY));

        String body =
                mvc.perform(get(INVITE))
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        JsonNode data = json.readTree(body).get("data");
        assertThat(data.propertyNames())
                .containsExactlyInAnyOrder("dealId", "productId", "sharerNickname", "expiresAt");
        assertThat(data.get("sharerNickname").isNull()).isTrue();
    }

    @Test
    void theEdgeAddressHeaderIsUsedWithoutItsPortAndTheSocketAddressOtherwise() throws Exception {
        when(invites.resolve(anyString(), anyString()))
                .thenReturn(new ResolvedInviteView(DEAL, PRODUCT, null, EXPIRY));

        mvc.perform(get(INVITE).header(VIEWER, "2001:db8::1:46532")).andExpect(status().isOk());
        mvc.perform(get(INVITE).header("X-Forwarded-For", "203.0.113.9"))
                .andExpect(status().isOk());

        verify(invites).resolve(REF, "2001:db8::1");
        verify(invites).resolve(REF, "127.0.0.1");
    }

    @Test
    void anUnknownInviteIsNotFoundAndNotCached() throws Exception {
        when(invites.resolve(any(), any()))
                .thenThrow(new BizException(GroupbuyErrorCode.INVITE_NOT_FOUND));

        mvc.perform(get(INVITE))
                .andExpect(status().isNotFound())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("GROUPBUY_404_002"));
    }

    @Test
    void aClientOverItsLimitIsTooManyRequestsAndNotCached() throws Exception {
        when(invites.resolve(any(), any()))
                .thenThrow(new BizException(CommonErrorCode.RATE_LIMITED));

        mvc.perform(get(INVITE))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.code").value("COMMON_429_001"));
    }

    @Test
    void aMalformedReferenceIsABadRequest() throws Exception {
        mvc.perform(get("/api/v1/invites/not-a-ref"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("COMMON_400_002"));
        verifyNoInteractions(invites);
    }

    @Test
    void onlyASingleSegmentGetIsOpenWithoutASession() throws Exception {
        int postStatus = mvc.perform(post(INVITE)).andReturn().getResponse().getStatus();
        assertThat(postStatus).isGreaterThanOrEqualTo(400);

        mvc.perform(get(INVITE + "/credits")).andExpect(status().isUnauthorized());
        verifyNoInteractions(invites);
    }
}
