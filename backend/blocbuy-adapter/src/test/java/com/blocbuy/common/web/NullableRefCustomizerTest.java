package com.blocbuy.common.web;

import static org.assertj.core.api.Assertions.assertThat;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** Nullable $ref properties become anyOf [$ref, null]; everything else is left alone. */
class NullableRefCustomizerTest {

    private static final String REF = "#/components/schemas/ActiveDealResponse";

    private final NullableRefCustomizer customizer = new NullableRefCustomizer();

    @Test
    void rewritesNullableRefToAnyOfWithNull() {
        Schema<?> deal = new JsonSchema().$ref(REF).types(Set.of("null")).description("Open bloc");
        OpenAPI openApi = withSchema(new JsonSchema().addProperty("deal", deal));

        customizer.customise(openApi);

        Schema<?> rewritten = property(openApi, "deal");
        assertThat(rewritten.get$ref()).isNull();
        assertThat(rewritten.getDescription()).isEqualTo("Open bloc");
        assertThat(rewritten.getAnyOf()).hasSize(2);
        assertThat(rewritten.getAnyOf().get(0).get$ref()).isEqualTo(REF);
        assertThat(rewritten.getAnyOf().get(1).getTypes()).containsExactly("null");
    }

    @Test
    void leavesNonNullableRefsAndNullableScalarsUntouched() {
        Schema<?> deal = new JsonSchema().$ref(REF);
        Schema<?> untypedRef = new JsonSchema().$ref(REF).types(Set.of("object"));
        Schema<?> price = new JsonSchema().types(Set.of("number", "null"));
        OpenAPI openApi =
                withSchema(
                        new JsonSchema()
                                .addProperty("deal", deal)
                                .addProperty("other", untypedRef)
                                .addProperty("price", price));

        customizer.customise(openApi);

        assertThat(property(openApi, "deal")).isSameAs(deal);
        assertThat(property(openApi, "other")).isSameAs(untypedRef);
        assertThat(property(openApi, "price")).isSameAs(price);
    }

    @Test
    void toleratesMissingComponentsSchemasAndProperties() {
        OpenAPI noComponents = new OpenAPI();
        OpenAPI noSchemas = new OpenAPI().components(new Components());
        OpenAPI noProperties = withSchema(new JsonSchema());

        customizer.customise(noComponents);
        customizer.customise(noSchemas);
        customizer.customise(noProperties);

        assertThat(noComponents.getComponents()).isNull();
        assertThat(noSchemas.getComponents().getSchemas()).isNull();
        assertThat(noProperties.getComponents().getSchemas().get("Response").getProperties())
                .isNull();
    }

    private static OpenAPI withSchema(Schema<?> schema) {
        return new OpenAPI().components(new Components().addSchemas("Response", schema));
    }

    private static Schema<?> property(OpenAPI openApi, String name) {
        return (Schema<?>)
                openApi.getComponents().getSchemas().get("Response").getProperties().get(name);
    }
}
