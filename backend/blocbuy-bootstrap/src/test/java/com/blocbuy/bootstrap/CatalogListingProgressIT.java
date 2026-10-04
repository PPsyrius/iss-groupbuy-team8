package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.blocbuy.common.util.UuidV7;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Issue #245: catalog cards carry the open deal's progress from real PostgreSQL. Redis is
 * unreachable, so every request takes the source path and statement counts are deterministic.
 */
@SpringBootTest(
        properties = {
            "spring.data.redis.host=localhost",
            "spring.data.redis.port=1",
            "spring.flyway.locations=classpath:db/migration"
        })
@AutoConfigureMockMvc
@Testcontainers
@Import({TeamPriceControllerTest.SessionStore.class, SqlStatementCapture.Configuration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CatalogListingProgressIT {
    private static final String PRODUCTS = "/api/v1/products";
    private static final int PAGE = 20;

    @Container
    @SuppressWarnings("resource") // Testcontainers owns this disposable database.
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18");

    /** Redirect all database access to the owned test container, never a developer database. */
    @DynamicPropertySource
    static void database(DynamicPropertyRegistry properties) {
        properties.add(
                "spring.datasource.url",
                () -> {
                    String url = POSTGRES.getJdbcUrl();
                    return url + (url.contains("?") ? "&" : "?") + "currentSchema=domain";
                });
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired private MockMvc mvc;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private ObjectMapper json;
    @Autowired private SqlStatementCapture sqlStatements;

    /** A per-test category and keyword isolate this test's rows from every other fixture. */
    private String category;

    private String keyword;
    private UUID withBloc;
    private UUID withoutBloc;
    private UUID bloc;

    /** Newest product has an open bloc (3 of 5); the next has none; older rows pad the page. */
    @BeforeEach
    void seed() {
        String suffix = UuidV7.next().toString().replace("-", "");
        category = "progress-" + suffix;
        keyword = "kw" + suffix;
        jdbc.update(
                """
                INSERT INTO biz_product (product_no, title, original_price, currency, stock,
                    status, category, create_time)
                SELECT ? || '-' || n, 'Progress tea ' || ?, 12, 'sgd', 5, 'on_sale', ?,
                    CURRENT_TIMESTAMP - n * interval '1 second'
                FROM generate_series(1, ?) AS fixture(n)
                """,
                category,
                keyword,
                category,
                PAGE);
        withBloc = productNo(1);
        withoutBloc = productNo(2);
        bloc = UuidV7.next();
        jdbc.update(
                """
                INSERT INTO biz_group_deal
                    (id, deal_no, product_id, leader_user_id, deal_price, currency,
                     target_size, current_size, status, start_time, expire_time)
                VALUES (?, ?, ?, ?, 9.00, 'sgd', 5, 3, 'open',
                        CURRENT_TIMESTAMP - INTERVAL '1 minute', CURRENT_TIMESTAMP + INTERVAL '1 hour')
                """,
                bloc,
                "PROGRESS-" + bloc,
                withBloc,
                UuidV7.next());
    }

    /** AC1/AC2: the card's deal equals the detail page's deal; no bloc means no deal. */
    @Test
    void browseCardsCarryTheDetailPagesDeal() throws Exception {
        JsonNode items = data(browse(PAGE)).path("items");

        JsonNode card = item(items, withBloc);
        JsonNode detail = data(get(PRODUCTS + "/" + withBloc)).path("deal");
        assertThat(card.path("deal").path("dealId").asString()).isEqualTo(bloc.toString());
        assertThat(card.path("deal").path("targetSize").asInt()).isEqualTo(5);
        assertThat(card.path("deal").path("currentSize").asInt()).isEqualTo(3);
        assertThat(card.path("deal")).isEqualTo(detail);
        assertThat(card.path("groupPrice").decimalValue()).isEqualByComparingTo("9.00");

        JsonNode plain = item(items, withoutBloc);
        assertThat(plain.get("deal").isNull()).isTrue();
        assertThat(plain.get("groupPrice").isNull()).isTrue();
    }

    /** AC3: keyword search fills the same field from the same deal. */
    @Test
    void keywordSearchCarriesTheSameDeal() throws Exception {
        JsonNode browseCard = item(data(browse(PAGE)).path("items"), withBloc);
        JsonNode items = data(search(PAGE)).path("items");

        assertThat(item(items, withBloc).path("deal")).isEqualTo(browseCard.path("deal"));
        assertThat(item(items, withoutBloc).get("deal").isNull()).isTrue();
    }

    /** AC4: one deal statement per page; a 20-row page runs as many statements as a 1-row page. */
    @Test
    void sqlStatementCountIsIndependentOfPageSize() throws Exception {
        for (var request : List.of(Request.BROWSE, Request.SEARCH)) {
            var small = captureStatements(request, 1);
            var large = captureStatements(request, PAGE);
            assertThat(large).hasSameSizeAs(small);
            assertThat(large.stream().filter(sql -> sql.contains("biz_group_deal")).toList())
                    .hasSize(1);
        }
    }

    private enum Request {
        BROWSE,
        SEARCH
    }

    /** Capture stops even if an HTTP assertion fails. */
    private List<String> captureStatements(Request request, int size) throws Exception {
        sqlStatements.start();
        List<String> captured;
        try {
            mvc.perform(request == Request.BROWSE ? browse(size) : search(size))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.data.items.length()").value(size))
                    .andExpect(jsonPath("$.data.items[0].deal.currentSize").value(3));
        } finally {
            captured = sqlStatements.finish();
        }
        return captured;
    }

    private MockHttpServletRequestBuilder browse(int size) {
        return get(PRODUCTS).param("category", category).param("size", Integer.toString(size));
    }

    private MockHttpServletRequestBuilder search(int size) {
        return browse(size).param("keyword", keyword);
    }

    private JsonNode data(MockHttpServletRequestBuilder request) throws Exception {
        String body =
                mvc.perform(request)
                        .andExpect(status().isOk())
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return json.readTree(body).path("data");
    }

    private UUID productNo(int n) {
        return jdbc.queryForObject(
                "SELECT id FROM biz_product WHERE product_no = ?", UUID.class, category + "-" + n);
    }

    private static JsonNode item(JsonNode items, UUID id) {
        for (JsonNode item : items) {
            if (id.toString().equals(item.path("id").asString())) {
                return item;
            }
        }
        throw new AssertionError("listing " + id + " not on the page");
    }
}
