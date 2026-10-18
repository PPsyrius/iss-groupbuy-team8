package com.blocbuy.bootstrap;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import cn.dev33.satoken.dao.SaTokenDao;
import cn.dev33.satoken.dao.SaTokenDaoDefaultImpl;
import com.blocbuy.catalog.service.ProductAppService;
import com.blocbuy.common.model.PageQuery;
import com.blocbuy.common.model.PageResult;
import com.blocbuy.identity.repository.RoleRepository;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Real HTTP binding for protocol-level failures: a wrong method, {@code Content-Type} or {@code
 * Accept} header must come back as a 405/415/406 {@code Result} envelope, never the 500 catch-all
 * (NFR-12). Only the application service and the RBAC repository are mocked; the test-only session
 * store never connects to a developer's Redis.
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
@Import(HttpProtocolErrorsTest.SessionStore.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class HttpProtocolErrorsTest {
    private static final String PRODUCTS_PATH = "/api/v1/products";
    private static final String STRIPE_WEBHOOK_PATH = "/api/v1/webhooks/stripe";

    @Autowired private MockMvc mvc;
    @MockitoBean private ProductAppService products;
    @MockitoBean private RoleRepository roles;

    @Test
    void wrongMethodOnKnownPathIs405Envelope() throws Exception {
        mvc.perform(delete(PRODUCTS_PATH))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", containsString("GET")))
                .andExpect(jsonPath("$.code").value("COMMON_405_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
        verifyNoInteractions(products);
    }

    @Test
    void unsupportedContentTypeOnWebhookIs415Envelope() throws Exception {
        mvc.perform(post(STRIPE_WEBHOOK_PATH).contentType(MediaType.TEXT_PLAIN).content("x"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.code").value("COMMON_415_001"))
                .andExpect(jsonPath("$.data").doesNotExist());
    }

    /**
     * The stub matters: a null body would return quietly with no exception, so no 406 would be
     * raised.
     */
    @Test
    void acceptHeaderExcludingJsonStillGets406JsonEnvelope() throws Exception {
        when(products.pageListings(null, null, null))
                .thenReturn(PageResult.of(List.of(), 0, PageQuery.of(null, null)));
        mvc.perform(get(PRODUCTS_PATH).header("Accept", "application/x-blocbuy-unsupported"))
                .andExpect(status().isNotAcceptable())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.code").value("COMMON_406_001"));
    }

    /** Test-only session store: keeps Sa-Token off a developer's Redis. */
    @TestConfiguration(proxyBeanMethods = false)
    static class SessionStore {
        @Bean(initMethod = "init", destroyMethod = "destroy")
        @Primary
        SaTokenDao testSessionDao() {
            return new SaTokenDaoDefaultImpl();
        }
    }
}
