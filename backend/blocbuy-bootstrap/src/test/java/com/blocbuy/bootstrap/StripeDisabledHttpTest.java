package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.identity.repository.RoleRepository;
import com.blocbuy.identity.service.OAuthLoginAppService;
import com.blocbuy.identity.service.OAuthLoginAppService.OAuthLoginResult;
import com.blocbuy.payment.service.PaymentBrowserService;
import jakarta.servlet.http.Cookie;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/** No database, no Stripe profile: all dependent requests must stop at the web boundary. */
@SpringBootTest(
        properties = {
            "spring.flyway.enabled=false",
            "spring.datasource.url=jdbc:postgresql://127.0.0.1:1/unused",
            "spring.datasource.username=unused",
            "spring.datasource.password=unused",
            "spring.data.redis.host=127.0.0.1",
            "spring.data.redis.port=1"
        })
@AutoConfigureMockMvc
@Import(AuthControllerTest.SessionStore.class)
@DirtiesContext
class StripeDisabledHttpTest {
    private static final String ID = "00000000-0000-4000-8000-000000000001";
    @Autowired private MockMvc mvc;
    @MockitoBean private PaymentBrowserService payments;
    @MockitoBean private OAuthLoginAppService oauth;
    @MockitoBean private RoleRepository roles;

    @Autowired
    private java.util.List<
                    org.springframework.web.servlet.mvc.method.annotation
                            .RequestMappingHandlerMapping>
            mappings;

    @Test
    void localLoginAndOperatorEndpointsAreNotRegistered() {
        assertThat(
                        mappings.stream()
                                .flatMap(handler -> handler.getHandlerMethods().keySet().stream()))
                .isNotEmpty()
                .allSatisfy(
                        mapping ->
                                assertThat(mapping.getPatternValues())
                                        .noneMatch(path -> path.startsWith("/local/stripe")));
    }

    @Test
    void capabilitiesAreAuthenticatedAndDefaultOffWithoutBusinessAccess() throws Exception {
        mvc.perform(get("/api/v1/payments/capabilities")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/v1/payments/capabilities").cookie(login()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.data.enabled").value(false));
        verifyNoInteractions(payments);
    }

    @Test
    void allStripeBrowserRoutesRejectBeforeTouchingServices() throws Exception {
        Cookie cookie = login();
        mvc.perform(
                        post("/api/v1/payments/intents")
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "checkout")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"operation\":\"START\",\"businessId\":\"" + ID + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(
                        post("/api/v1/payments/checkout-sessions")
                                .cookie(cookie)
                                .header("X-BlocBuy-Request", "checkout")
                                .header("Idempotency-Key", "disabled-test")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content("{\"intentId\":\"" + ID + "\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(get("/api/v1/payments/" + ID).cookie(cookie)).andExpect(status().isNotFound());
        verifyNoInteractions(payments);
    }

    private Cookie login() throws Exception {
        when(oauth.loginWithGoogle(any(), any(), any()))
                .thenReturn(new OAuthLoginResult(UUID.fromString(ID), "Test buyer", null));
        return mvc.perform(
                        get("/api/v1/auth/oauth2/google/callback")
                                .param("code", "test")
                                .param("state", "test")
                                .cookie(new Cookie("blocbuy-oauth-state", "test")))
                .andExpect(status().isFound())
                .andReturn()
                .getResponse()
                .getCookie("blocbuy-token");
    }
}
