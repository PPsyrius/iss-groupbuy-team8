package com.blocbuy.bootstrap;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Context smoke test. Flyway is disabled and datasource/Redis point at placeholders — this test
 * proves the wiring assembles, not that infrastructure is reachable. Real connectivity is covered
 * by the repository integration tests (Testcontainers) in the infrastructure module.
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
class BlocBuyApplicationTests {

    @Test
    void contextLoads() {
        // assembly success is the assertion
    }
}
