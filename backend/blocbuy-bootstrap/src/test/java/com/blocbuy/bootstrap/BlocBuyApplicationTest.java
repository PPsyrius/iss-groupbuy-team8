package com.blocbuy.bootstrap;

import static org.mockito.Mockito.mockStatic;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.boot.SpringApplication;
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
class BlocBuyApplicationTest {

    @Test
    void contextLoads() {
        // assembly success is the assertion
    }

    @Test
    void mainDelegatesToSpringApplication() {
        String[] args = {"--spring.profiles.active=test"};

        try (MockedStatic<SpringApplication> springApplication =
                mockStatic(SpringApplication.class)) {
            BlocBuyApplication.main(args);

            springApplication.verify(() -> SpringApplication.run(BlocBuyApplication.class, args));
        }
    }
}
