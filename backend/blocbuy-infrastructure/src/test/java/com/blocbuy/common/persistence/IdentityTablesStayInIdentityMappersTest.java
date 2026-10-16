package com.blocbuy.common.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * SQL ownership guard for the service split (backend ADR 0008, #469): only identity's mappers may
 * name identity tables, because commerce-svc's database role has no grant on them. ArchUnit cannot
 * see SQL inside mapper XML, so this reads it. The table list matches {@code identity_tables} in
 * {@code db/swe5001/afterMigrate__service_grants.sql}.
 */
class IdentityTablesStayInIdentityMappersTest {

    private static final Pattern IDENTITY_TABLE =
            Pattern.compile(
                    "\\b(sys_user|sys_user_oauth|sys_role|sys_permission|rel_[a-z_]+)\\b",
                    Pattern.CASE_INSENSITIVE);
    private static final Pattern XML_COMMENT = Pattern.compile("<!--.*?-->", Pattern.DOTALL);

    @Test
    void noMapperOutsideIdentityNamesAnIdentityTable() throws IOException {
        Resource[] mappers =
                new PathMatchingResourcePatternResolver()
                        .getResources("classpath*:mapper/**/*.xml");
        List<String> offenders = new ArrayList<>();
        int identityMappers = 0;
        for (Resource mapper : mappers) {
            String location = mapper.getURL().toString();
            String sql =
                    XML_COMMENT
                            .matcher(mapper.getContentAsString(StandardCharsets.UTF_8))
                            .replaceAll("");
            if (location.contains("/mapper/identity/")) {
                identityMappers += IDENTITY_TABLE.matcher(sql).find() ? 1 : 0;
            } else if (IDENTITY_TABLE.matcher(sql).find()) {
                offenders.add(location.substring(location.indexOf("mapper/")));
            }
        }
        // Not vacuous: the scan sees the identity mappers that legitimately name these tables.
        assertThat(identityMappers).isPositive();
        assertThat(offenders).isEmpty();
    }
}
