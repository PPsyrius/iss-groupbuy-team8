package com.blocbuy.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noFields;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

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
          .resideInAnyPackage(DOMAIN_MODEL, DOMAIN_PORTS, DOMAIN_EVENT, DOMAIN_EXC, COMMON_DOMAIN)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(
              "org.springframework..",
              "org.apache.ibatis..",
              "org.mybatis..",
              "cn.dev33..",
              "jakarta..",
              "com.fasterxml.jackson..")
          .because("domain must stay framework-free (JDK + Lombok only)");

  /** Application layer never touches MyBatis/Redis directly — only domain ports. */
  @ArchTest
  static final ArchRule application_uses_ports_not_tech =
      noClasses()
          .that()
          .resideInAnyPackage(APP, APP2, APP3, APP4)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage("org.apache.ibatis..", "org.mybatis..", "org.springframework.data.redis..")
          .because("application depends on domain ports, not MyBatis mappers or RedisTemplate");

  /** No Controller → Mapper/persistence dependency. */
  @ArchTest
  static final ArchRule controllers_never_touch_persistence =
      noClasses()
          .that()
          .resideInAnyPackage(WEB)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(PERSISTENCE, "org.apache.ibatis..")
          .because("controllers do protocol adaptation only; persistence stays in infrastructure");

  /** Persistence internals are only used inside persistence itself (cross-domain access included). */
  @ArchTest
  static final ArchRule persistence_is_internal =
      noClasses()
          .that()
          .resideOutsideOfPackage(PERSISTENCE)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(PERSISTENCE)
          .because("Mapper/DO/RepositoryImpl/Converter never cross the infrastructure boundary");

  /** Domain must not depend on application, adapter, or infrastructure. */
  @ArchTest
  static final ArchRule domain_has_no_outward_dependencies =
      noClasses()
          .that()
          .resideInAnyPackage(DOMAIN_MODEL, DOMAIN_PORTS, DOMAIN_EVENT, DOMAIN_EXC)
          .should()
          .dependOnClassesThat()
          .resideInAnyPackage(APP, APP2, APP3, APP4, WEB, PERSISTENCE, "com.blocbuy.common.config..")
          .because("dependencies point toward the business core only");

  /** No field injection — constructor injection with {@code private final} is mandatory. */
  @ArchTest
  static final ArchRule no_field_injection =
      noFields()
          .should()
          .beAnnotatedWith("org.springframework.beans.factory.annotation.Autowired")
          .orShould()
          .beAnnotatedWith("jakarta.annotation.Resource")
          .because("beans use @RequiredArgsConstructor + private final constructor injection");
}
