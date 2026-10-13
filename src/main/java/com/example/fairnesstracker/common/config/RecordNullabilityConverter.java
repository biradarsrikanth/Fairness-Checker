package com.example.fairnesstracker.common.config;

import io.swagger.v3.core.converter.AnnotatedType;
import io.swagger.v3.core.converter.ModelConverter;
import io.swagger.v3.core.converter.ModelConverterContext;
import io.swagger.v3.core.util.Json;
import io.swagger.v3.core.util.RefUtils;
import io.swagger.v3.oas.models.media.Schema;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

import java.lang.reflect.RecordComponent;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Our DTOs are records, and Jackson writes every component (nulls too), so each one is a required property;
 * a component annotated {@link Nullable} may be null. Springdoc can't tell either on its own and would make
 * every field optional, so the generated TypeScript client would type everything as possibly missing.
 *
 * <p>For request bodies, required means the client must send it: a {@code @Nullable} component may be
 * left out (it arrives as null).
 */
@Component
public class RecordNullabilityConverter implements ModelConverter {

    @Override
    @SuppressWarnings({"rawtypes", "unchecked"})
    public Schema resolve(AnnotatedType type, ModelConverterContext context, Iterator<ModelConverter> chain) {
        if (!chain.hasNext()) return null;
        Schema resolved = chain.next().resolve(type, context, chain);
        Class<?> raw = Json.mapper().constructType(type.getType()).getRawClass();
        if (resolved == null || !raw.isRecord()) return resolved;

        Schema model = resolved.get$ref() != null
                ? context.getDefinedModels().get((String) RefUtils.extractSimpleName(resolved.get$ref()).getKey())
                : resolved;
        if (model == null || model.getProperties() == null) return resolved;

        Map<String, Schema> properties = model.getProperties();
        for (RecordComponent component : raw.getRecordComponents()) {
            String name = component.getName();
            Schema property = properties.get(name);
            if (property == null) continue;
            if (component.getAnnotatedType().isAnnotationPresent(Nullable.class)) {
                properties.put(name, nullable(property));
                if (model.getRequired() != null) model.getRequired().remove(name);
            } else if (model.getRequired() == null || !model.getRequired().contains(name)) {
                model.addRequiredItem(name);
            }
        }
        return resolved;
    }

    // OpenAPI 3.1: a type list with "null", or a reference or null. Resolving a type again finds it done already
    @SuppressWarnings({"rawtypes", "unchecked"})
    private static Schema nullable(Schema property) {
        if (property.getOneOf() != null) return property;
        if (property.get$ref() != null) {
            Schema either = new Schema().oneOf(List.of(new Schema().$ref(property.get$ref()),
                    new Schema().types(Set.of("null"))));
            either.setDescription(property.getDescription());
            return either;
        }
        Set<String> types = new LinkedHashSet<>();
        if (property.getTypes() != null) types.addAll(property.getTypes());
        else if (property.getType() != null) types.add(property.getType());
        types.add("null");
        property.setTypes(types);
        return property;
    }
}
