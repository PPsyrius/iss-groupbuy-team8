package com.blocbuy.architecture;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.lang.ArchRule;
import fixture.archunit.web.JoinedInternalMappingController;
import fixture.archunit.web.LookalikePublicController;
import fixture.archunit.web.SlashlessClassMappingController;
import fixture.archunit.web.SlashlessMethodMappingController;
import fixture.archunit.web.internal.DistributedOnlyInternalController;
import fixture.archunit.web.internal.MatchIfMissingInternalController;
import fixture.archunit.web.internal.PrefixedDistributedInternalController;
import java.util.List;
import java.util.function.Predicate;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

/**
 * Regression tests for the {@code /internal/**} rules in {@link ArchitectureTest}, run against
 * fixture controllers outside {@code com.blocbuy} (PR #470 review).
 */
class InternalEndpointRulesTest {

    private static final JavaClasses FIXTURES =
            new ClassFileImporter().importPackages("fixture.archunit");

    @Test
    void internalControllersMustNotMatchAMissingTopology() {
        List<String> violations =
                violations(ArchitectureTest.internal_controllers_are_hidden_and_distributed_only);

        assertThat(violations)
                .anyMatch(reported(MatchIfMissingInternalController.class))
                .noneMatch(reported(DistributedOnlyInternalController.class))
                .noneMatch(reported(PrefixedDistributedInternalController.class));
    }

    @Test
    void internalPathsAreCheckedAsSpringRegistersThem() {
        List<String> violations =
                violations(ArchitectureTest.internal_paths_live_in_internal_controllers);

        assertThat(violations)
                .anyMatch(reported(SlashlessClassMappingController.class))
                .anyMatch(reported(SlashlessMethodMappingController.class))
                .anyMatch(reported(JoinedInternalMappingController.class))
                .noneMatch(reported(LookalikePublicController.class))
                .noneMatch(reported(DistributedOnlyInternalController.class));
    }

    /** The Spring behaviour the rules rely on: what activates a controller and what it serves. */
    @Test
    void rulesMatchSpringBehaviour() throws Exception {
        new ApplicationContextRunner()
                .withUserConfiguration(DistributedOnlyInternalController.class)
                .run(
                        monolith ->
                                assertThat(monolith)
                                        .doesNotHaveBean(DistributedOnlyInternalController.class));
        new ApplicationContextRunner()
                .withUserConfiguration(DistributedOnlyInternalController.class)
                .withPropertyValues("blocbuy.topology=distributed")
                .run(
                        service ->
                                assertThat(service)
                                        .hasSingleBean(DistributedOnlyInternalController.class));
        new ApplicationContextRunner()
                .withUserConfiguration(MatchIfMissingInternalController.class)
                .run(
                        monolith ->
                                assertThat(monolith)
                                        .hasSingleBean(MatchIfMissingInternalController.class));

        MockMvcBuilders.standaloneSetup(new SlashlessClassMappingController())
                .build()
                .perform(get("/internal/users/1"))
                .andExpect(status().isOk());
    }

    private static List<String> violations(ArchRule rule) {
        return rule.evaluate(FIXTURES).getFailureReport().getDetails();
    }

    /** Matches both custom-condition details and ArchUnit's {@code Class <name> ...} details. */
    private static Predicate<String> reported(Class<?> fixture) {
        String name = fixture.getName();
        return detail -> detail.startsWith(name + " ") || detail.contains("<" + name + ">");
    }
}
