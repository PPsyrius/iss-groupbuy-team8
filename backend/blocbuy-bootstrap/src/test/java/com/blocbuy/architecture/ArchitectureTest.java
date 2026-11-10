package com.blocbuy.architecture;

import static com.tngtech.archunit.base.DescribedPredicate.not;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.belongToAnyOf;
import static com.tngtech.archunit.core.domain.JavaClass.Predicates.resideInAPackage;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.blocbuy.identity.service.UserAppService;
import com.tngtech.archunit.core.domain.JavaAnnotation;
import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethod;
import com.tngtech.archunit.core.domain.JavaParameter;
import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.util.pattern.PathPatternParser;

/**
 * Machine-enforced module boundaries (skill hard gate). Layers are Maven modules; these rules guard
 * the package conventions inside them. Violations fail {@code mvnw verify}.
 *
 * <p>Layer packages per domain {@code com.blocbuy.<domain>.<segment>}:
 * domain=model/repository/event/exception, application=command/query/service/assembler,
 * adapter=web, infrastructure=persistence; plus {@code com.blocbuy.common.*} per owning module.
 */
@AnalyzeClasses(packages = "com.blocbuy", importOptions = ImportOption.DoNotIncludeTests.class)
final class ArchitectureTest {

    private ArchitectureTest() {
        // rules container only; ArchUnit evaluates the static @ArchTest fields reflectively
    }

    private static final String DOMAIN_MODEL = "com.blocbuy..model..";
    private static final String DOMAIN_PORTS = "com.blocbuy..repository..";
    private static final String DOMAIN_PRICING = "com.blocbuy..pricing..";
    private static final String DOMAIN_EVENT = "com.blocbuy..event..";
    private static final String DOMAIN_EXC = "com.blocbuy..exception..";
    private static final String COMMON_DOMAIN = "com.blocbuy.common.exception..";

    private static final String APP = "com.blocbuy..command..";
    private static final String APP2 = "com.blocbuy..query..";
    private static final String APP3 = "com.blocbuy..service..";
    private static final String APP4 = "com.blocbuy..assembler..";

    private static final String WEB = "com.blocbuy..web..";
    private static final String PERSISTENCE = "com.blocbuy..persistence..";

    /** Domain layer depends on JDK/Lombok only — no Spring, MyBatis, Redis, Sa-Token, Jakarta. */
    @ArchTest
    static final ArchRule domain_is_framework_free =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            DOMAIN_MODEL,
                            DOMAIN_PORTS,
                            DOMAIN_PRICING,
                            DOMAIN_EVENT,
                            DOMAIN_EXC,
                            COMMON_DOMAIN)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.springframework..",
                            "org.apache.ibatis..",
                            "org.mybatis..",
                            "cn.dev33..",
                            "jakarta..",
                            "com.fasterxml.jackson..",
                            "tools.jackson..")
                    .because("domain must stay framework-free (JDK + Lombok only)");

    /** Application layer never touches MyBatis/Redis directly — only domain ports. */
    @ArchTest
    static final ArchRule application_uses_ports_not_tech =
            noClasses()
                    .that()
                    .resideInAnyPackage(APP, APP2, APP3, APP4)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            "org.apache.ibatis..",
                            "org.mybatis..",
                            "org.springframework.data.redis..")
                    .because(
                            "application depends on domain ports, not MyBatis mappers or RedisTemplate");

    /** No Controller → Mapper/persistence dependency. */
    @ArchTest
    static final ArchRule controllers_never_touch_persistence =
            noClasses()
                    .that()
                    .resideInAnyPackage(WEB)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(PERSISTENCE, "org.apache.ibatis..")
                    .because(
                            "controllers do protocol adaptation only; persistence stays in infrastructure");

    /**
     * Persistence internals are only used inside persistence itself (cross-domain access included).
     */
    @ArchTest
    static final ArchRule persistence_is_internal =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(PERSISTENCE)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(PERSISTENCE)
                    .because(
                            "Mapper/DO/RepositoryImpl/Converter never cross the infrastructure boundary");

    /** Domain must not depend on application, adapter, or infrastructure. */
    @ArchTest
    static final ArchRule domain_has_no_outward_dependencies =
            noClasses()
                    .that()
                    .resideInAnyPackage(
                            DOMAIN_MODEL, DOMAIN_PORTS, DOMAIN_PRICING, DOMAIN_EVENT, DOMAIN_EXC)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(
                            APP, APP2, APP3, APP4, WEB, PERSISTENCE, "com.blocbuy.common.config..")
                    .because("dependencies point toward the business core only");

    /** No field injection — constructor injection with {@code private final} is mandatory. */
    @ArchTest
    static final ArchRule no_field_injection =
            noFields()
                    .should()
                    .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
                    .orShould()
                    .beAnnotatedWith("jakarta.annotation.Resource")
                    .because(
                            "beans use @RequiredArgsConstructor + private final constructor injection");

    private static final Set<String> NUMERIC_PARAM_TYPES =
            Set.of("java.lang.Integer", "int", "java.lang.Long", "long");

    private static final Set<String> RANGE_CONSTRAINTS =
            Set.of(
                    "jakarta.validation.constraints.Min",
                    "jakarta.validation.constraints.Max",
                    "jakarta.validation.constraints.Positive",
                    "jakarta.validation.constraints.PositiveOrZero",
                    "jakarta.validation.constraints.Negative",
                    "jakarta.validation.constraints.NegativeOrZero",
                    "jakarta.validation.constraints.DecimalMin",
                    "jakarta.validation.constraints.DecimalMax");

    /**
     * Every REST controller carries {@code @Validated} — without it, method-parameter constraints
     * on {@code @RequestParam}/{@code @PathVariable} are silently ignored (issue #57).
     */
    @ArchTest
    static final ArchRule controllers_are_validated =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .beAnnotatedWith(Validated.class)
                    .because(
                            "method-parameter constraints need class-level @Validated to be enforced");

    /**
     * Numeric {@code @RequestParam}s must carry a range constraint — an unbounded/unvalidated
     * number penetrates to the domain and surfaces as a 500 instead of a 400 (issue #56).
     */
    @ArchTest
    static final ArchRule numeric_request_params_have_range_constraints =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .should(haveRangeConstraintsOnNumericRequestParams())
                    .because(
                            "numeric @RequestParam without @Min/@Max reaches the domain guard and 500s");

    private static ArchCondition<JavaClass> haveRangeConstraintsOnNumericRequestParams() {
        return new ArchCondition<>("have range constraints on numeric @RequestParam parameters") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (JavaMethod method : controller.getMethods()) {
                    for (JavaParameter param : method.getParameters()) {
                        if (!param.isAnnotatedWith(RequestParam.class)
                                || !NUMERIC_PARAM_TYPES.contains(param.getRawType().getName())) {
                            continue;
                        }
                        boolean constrained =
                                param.getAnnotations().stream()
                                        .anyMatch(annotation ->
                                                RANGE_CONSTRAINTS.contains(
                                                        annotation.getRawType().getName()));
                        if (!constrained) {
                            events.add(
                                    SimpleConditionEvent.violated(
                                            param,
                                            param.getDescription()
                                                    + " is a numeric @RequestParam without a"
                                                    + " range constraint"));
                        }
                    }
                }
            }
        };
    }

    // ---- Service boundaries of the SWE5001 split (backend ADR 0008, WP-B1). The monolith keeps
    // them too, so code that would break the three-service assembly fails on dev first.

    private static final String IDENTITY = "com.blocbuy.identity..";
    private static final String NOTIFICATION = "com.blocbuy.notification..";

    /** The outbox writer and relay ({@code integration}) belong to commerce (backend ADR 0007). */
    private static final String[] COMMERCE = {
        "com.blocbuy.catalog..",
        "com.blocbuy.groupbuy..",
        "com.blocbuy.order..",
        "com.blocbuy.payment..",
        "com.blocbuy.integration.."
    };

    private static final String INTERNAL_WEB = "..web.internal..";
    private static final String INTERNAL_PATH = "/internal";
    private static final String CONDITIONAL_ON_PROPERTY =
            "org.springframework.boot.autoconfigure.condition.ConditionalOnProperty";

    /** Commerce reaches identity only through the remote-capable {@code UserAppService} port. */
    @ArchTest
    static final ArchRule commerce_reaches_identity_only_through_user_port =
            noClasses()
                    .that()
                    .resideInAnyPackage(COMMERCE)
                    .should()
                    .dependOnClassesThat(
                            resideInAPackage(IDENTITY)
                                    .and(not(belongToAnyOf(UserAppService.class))))
                    .because(
                            "commerce-svc has no identity code or data; UserAppService has an HTTP"
                                    + " adapter (#468)");

    /** {@code currentUser} has no remote implementation, so only identity may call it. */
    @ArchTest
    static final ArchRule only_identity_reads_current_user =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(IDENTITY)
                    .should()
                    .callMethod(UserAppService.class, "currentUser", UUID.class)
                    .because("the remote UserAppService refuses currentUser (#468)");

    /** Notification is reached by events only (backend ADR 0007). */
    @ArchTest
    static final ArchRule nothing_depends_on_notification =
            noClasses()
                    .that()
                    .resideOutsideOfPackage(NOTIFICATION)
                    .should()
                    .dependOnClassesThat()
                    .resideInAPackage(NOTIFICATION)
                    .because("notification-svc is deployed apart and reached by events only");

    /** Notification stays independently deployable. */
    @ArchTest
    static final ArchRule notification_depends_on_no_other_service =
            noClasses()
                    .that()
                    .resideInAPackage(NOTIFICATION)
                    .should()
                    .dependOnClassesThat()
                    .resideInAnyPackage(concat(COMMERCE, IDENTITY))
                    .because("notification-svc has neither commerce nor identity code or data");

    /** Each service's persistence uses only its own mappers: tables stay with their owner. */
    @ArchTest
    static final ArchRule identity_persistence_is_owned =
            persistenceOwnedBy("identity", persistenceOf(IDENTITY), concat(COMMERCE, NOTIFICATION));

    @ArchTest
    static final ArchRule commerce_persistence_is_owned =
            persistenceOwnedBy("commerce", persistenceOf(COMMERCE), IDENTITY, NOTIFICATION);

    @ArchTest
    static final ArchRule notification_persistence_is_owned =
            persistenceOwnedBy(
                    "notification", persistenceOf(NOTIFICATION), concat(COMMERCE, IDENTITY));

    /**
     * Internal controllers are hidden from the contract and exist only in the distributed topology,
     * so the dev monolith never serves an unauthenticated {@code /internal/**} (backend ADR 0008).
     */
    @ArchTest
    static final ArchRule internal_controllers_are_hidden_and_distributed_only =
            classes()
                    .that()
                    .resideInAPackage(INTERNAL_WEB)
                    .and()
                    .areAnnotatedWith(RestController.class)
                    .should()
                    .beAnnotatedWith("io.swagger.v3.oas.annotations.Hidden")
                    .andShould(beConditionalOnDistributedTopology())
                    .allowEmptyShould(true)
                    .because(
                            "/internal/** relies on network isolation only; it must not exist on"
                                    + " the monolith or in contracts/openapi.json");

    /** {@code /internal} paths are declared only by internal controllers. */
    @ArchTest
    static final ArchRule internal_paths_live_in_internal_controllers =
            classes()
                    .that()
                    .areAnnotatedWith(RestController.class)
                    .and()
                    .resideOutsideOfPackage(INTERNAL_WEB)
                    .should(notMapInternalPaths())
                    .because(
                            "only ..web.internal.. controllers carry the internal-endpoint guards");

    private static ArchRule persistenceOwnedBy(
            String service, String[] ownPersistence, String... foreignContexts) {
        return noClasses()
                .that()
                .resideInAnyPackage(ownPersistence)
                .should()
                .dependOnClassesThat()
                .resideInAnyPackage(persistenceOf(foreignContexts))
                .because(service + "-svc reads and writes only the tables it owns");
    }

    private static String[] persistenceOf(String... contexts) {
        String[] packages = new String[contexts.length];
        for (int i = 0; i < contexts.length; i++) {
            packages[i] = contexts[i].replace("..", ".persistence..");
        }
        return packages;
    }

    private static String[] concat(String[] first, String... rest) {
        List<String> all = new ArrayList<>(List.of(first));
        all.addAll(List.of(rest));
        return all.toArray(String[]::new);
    }

    private static ArchCondition<JavaClass> beConditionalOnDistributedTopology() {
        return new ArchCondition<>("be @ConditionalOnProperty(blocbuy.topology=distributed)") {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                Optional<JavaAnnotation<JavaClass>> condition =
                        controller.tryGetAnnotationOfType(CONDITIONAL_ON_PROPERTY);
                // matchIfMissing=true would activate the controller in the default monolith.
                boolean distributedOnly =
                        condition.isPresent()
                                && propertyNames(condition.get()).contains("blocbuy.topology")
                                && "distributed"
                                        .equals(condition.get().get("havingValue").orElse(""))
                                && !Boolean.TRUE.equals(
                                        condition.get().get("matchIfMissing").orElse(false));
                if (!distributedOnly) {
                    events.add(
                            SimpleConditionEvent.violated(
                                    controller,
                                    controller.getName()
                                            + " is not conditional on blocbuy.topology=distributed"));
                }
            }
        };
    }

    private static List<String> propertyNames(JavaAnnotation<?> condition) {
        String prefix = (String) condition.get("prefix").orElse("");
        List<String> names = new ArrayList<>();
        for (String attribute : List.of("name", "value")) {
            for (Object name : (Object[]) condition.get(attribute).orElse(new Object[0])) {
                names.add(prefix.isEmpty() ? (String) name : prefix + "." + name);
            }
        }
        return names;
    }

    private static ArchCondition<JavaClass> notMapInternalPaths() {
        return new ArchCondition<>("not map paths under " + INTERNAL_PATH) {
            @Override
            public void check(JavaClass controller, ConditionEvents events) {
                for (String path : registeredPaths(controller)) {
                    if (path.equals(INTERNAL_PATH) || path.startsWith(INTERNAL_PATH + "/")) {
                        events.add(
                                SimpleConditionEvent.violated(
                                        controller,
                                        controller.getName()
                                                + " maps "
                                                + path
                                                + " outside "
                                                + INTERNAL_WEB));
                    }
                }
            }
        };
    }

    /**
     * The paths Spring MVC registers for the controller: class and method mappings parsed and
     * combined by Spring's own {@link RequestMappingInfo}, so {@code "internal/users"} counts as
     * {@code /internal/users} exactly as at run time.
     */
    private static Set<String> registeredPaths(JavaClass controller) {
        RequestMappingInfo type =
                mapping(controller.getAnnotations()).orElseGet(() -> mappingInfo(List.of()));
        Set<String> paths = new LinkedHashSet<>(type.getPatternValues());
        for (JavaMethod method : controller.getMethods()) {
            mapping(method.getAnnotations())
                    .ifPresent(info -> paths.addAll(type.combine(info).getPatternValues()));
        }
        return paths;
    }

    /** {@code @RequestMapping} or a composed variant ({@code @GetMapping}, ...), if present. */
    private static Optional<RequestMappingInfo> mapping(
            Set<? extends JavaAnnotation<?>> annotations) {
        for (JavaAnnotation<?> annotation : annotations) {
            JavaClass type = annotation.getRawType();
            if (!type.isEquivalentTo(RequestMapping.class)
                    && !type.isAnnotatedWith(RequestMapping.class)) {
                continue;
            }
            List<String> paths = new ArrayList<>();
            for (String attribute : List.of("value", "path")) {
                for (Object path : (Object[]) annotation.get(attribute).orElse(new Object[0])) {
                    paths.add((String) path);
                }
            }
            return Optional.of(mappingInfo(paths));
        }
        return Optional.empty();
    }

    private static RequestMappingInfo mappingInfo(List<String> paths) {
        RequestMappingInfo.BuilderConfiguration options =
                new RequestMappingInfo.BuilderConfiguration();
        options.setPatternParser(PathPatternParser.defaultInstance);
        return RequestMappingInfo.paths(paths.toArray(String[]::new)).options(options).build();
    }
}
