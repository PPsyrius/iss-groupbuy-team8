# Authoritative Sources and Version Verification

Read this only when framework behavior, version compatibility, or specific configuration is in question. Technical versions change; before implementation, defer to the official documentation matching the project's BOM/pinned version - never treat the "current latest" listed here as authorization to upgrade the project.

- COLA architecture and archetype: <https://github.com/alibaba/COLA>
- Java 21: <https://docs.oracle.com/en/java/javase/21/>
- Spring Boot Reference: <https://docs.spring.io/spring-boot/reference/>
- Spring Framework Transaction: <https://docs.spring.io/spring-framework/reference/data-access/transaction/>
- MyBatis 3: <https://mybatis.org/mybatis-3/>
- PostgreSQL current: <https://www.postgresql.org/docs/current/>
- Redis: <https://redis.io/docs/latest/>
- Sa-Token: <https://sa-token.cc/> and <https://github.com/dromara/sa-token>
- Maven: <https://maven.apache.org/guides/>
- HTTP Semantics: <https://www.rfc-editor.org/rfc/rfc9110>
- Problem Details: <https://www.rfc-editor.org/rfc/rfc9457>
- OWASP ASVS: <https://owasp.org/www-project-application-security-verification-standard/>

Deferred — not in the v1 stack, consult only if the user approves introducing them:

- Elasticsearch Java Client: <https://www.elastic.co/docs/reference/elasticsearch/clients/java>
- Amazon SQS dead-letter queues: <https://docs.aws.amazon.com/AWSSimpleQueueService/latest/SQSDeveloperGuide/sqs-dead-letter-queues.html>
- Amazon SNS message filtering: <https://docs.aws.amazon.com/sns/latest/dg/sns-message-filtering.html>
- Spring Cloud AWS: <https://docs.awspring.io/spring-cloud-aws/docs/current/reference/html/index.html>

When verifying versions, record: the project's current version, the target documentation version, the compatibility conclusion, and any deprecation/migration requirements. Without user authorization, discovering a new version never widens the current change's scope.
