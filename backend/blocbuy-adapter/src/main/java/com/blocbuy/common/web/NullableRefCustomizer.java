package com.blocbuy.common.web;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.media.JsonSchema;
import io.swagger.v3.oas.models.media.Schema;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springdoc.core.customizers.OpenApiCustomizer;
import org.springframework.stereotype.Component;

/**
 * Makes {@code @Schema(nullable = true)} on an object-typed property survive into the OpenAPI 3.1
 * contract.
 *
 * <p>For a scalar, swagger-core already emits {@code type: [..., "null"]}. For a property whose
 * schema is a {@code $ref} it emits {@code type: "null"} beside the {@code $ref}, which JSON Schema
 * reads as "null and the referenced object" and code generators drop. This rewrites such a property
 * to {@code anyOf: [{$ref}, {type: "null"}]}, the 3.1 form generators map to {@code T | null}.
 */
@Component
public class NullableRefCustomizer implements OpenApiCustomizer {

    private static final String NULL_TYPE = "null";

    @Override
    public void customise(OpenAPI openApi) {
        if (openApi.getComponents() == null || openApi.getComponents().getSchemas() == null) {
            return;
        }
        for (Schema<?> schema : openApi.getComponents().getSchemas().values()) {
            rewriteProperties(schema.getProperties());
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void rewriteProperties(Map<String, Schema> properties) {
        if (properties == null) {
            return;
        }
        properties.replaceAll(
                (name, property) -> isNullableRef(property) ? nullableRef(property) : property);
    }

    private static boolean isNullableRef(Schema<?> property) {
        return property.get$ref() != null
                && property.getTypes() != null
                && property.getTypes().contains(NULL_TYPE);
    }

    private static Schema<?> nullableRef(Schema<?> property) {
        JsonSchema nullable = new JsonSchema();
        nullable.setDescription(property.getDescription());
        nullable.setAnyOf(
                List.of(
                        new JsonSchema().$ref(property.get$ref()),
                        new JsonSchema().types(Set.of(NULL_TYPE))));
        return nullable;
    }
}
