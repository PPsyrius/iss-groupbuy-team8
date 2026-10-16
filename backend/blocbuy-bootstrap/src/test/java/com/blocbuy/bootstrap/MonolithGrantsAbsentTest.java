package com.blocbuy.bootstrap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * The SWE5001 per-service grants never reach the monolith (backend ADR 0008, #455 step A4): the
 * callback and the platform module are absent from its classpath, and no monolith profile lists the
 * grants location. The SWE5006 dev database therefore keeps every table privilege.
 */
class MonolithGrantsAbsentTest {

    private final PathMatchingResourcePatternResolver resources =
            new PathMatchingResourcePatternResolver();

    @Test
    void grantsCallbackIsNotOnTheClasspath() throws IOException {
        assertThat(resources.getResources("classpath*:db/swe5001/**")).isEmpty();
    }

    @Test
    void platformModuleIsNotOnTheClasspath() {
        assertThatThrownBy(() -> Class.forName("com.blocbuy.platform.PlatformAutoConfiguration"))
                .isInstanceOf(ClassNotFoundException.class);
    }

    @Test
    void noMonolithProfileListsTheGrantsLocation() throws IOException {
        Resource[] profiles = resources.getResources("classpath*:application*.yml");

        assertThat(profiles).isNotEmpty();
        for (Resource profile : profiles) {
            try (InputStream in = profile.getInputStream()) {
                assertThat(new String(in.readAllBytes(), StandardCharsets.UTF_8))
                        .as(profile.getDescription())
                        .doesNotContain("db/swe5001");
            }
        }
    }
}
