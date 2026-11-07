/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.config;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.networknt.schema.InputFormat;
import com.networknt.schema.JsonSchema;
import com.networknt.schema.JsonSchemaFactory;
import com.networknt.schema.SpecVersion;
import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;

/**
 * {@link ModelCatalogueCodec} keeps parity with the schema of Store Schemas § Model Catalogue: an empty catalogue carries
 * exactly the schema's members, the source values are those of {@link ModelSource}, the model bound is the schema's, and
 * a written catalogue is valid against the schema. The schema is the file {@code model-catalogue.schema.json} beside the
 * codec in {@code pm-core/src/main/resources/de/planmarshall/core/config/}, read from the class path through
 * {@link SchemaResource}. The member names, the source values and the bound are contracts; the model id and the instant
 * are generated.
 */
@EnableGeneratorController
@DisplayName("Model catalogue schema parity")
class ModelCatalogueSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/config/model-catalogue.schema.json";

    @Test
    @DisplayName("an empty catalogue has exactly the schema's members, every one required")
    void storeMembers() {
        var schema = SchemaResource.load(SCHEMA);
        var written = document(ModelCatalogueCodec.write(Map.of(), 0));

        assertAll(
                () -> assertEquals(names(SchemaResource.object(schema.get("properties")).keySet()),
                        names(written.keySet()), "The catalogue members are the schema's in order"),
                () -> assertEquals(names(SchemaResource.array(schema.get("required"))), names(written.keySet()),
                        "Every catalogue member is required"));
    }

    @Test
    @DisplayName("the source values and the model bound are those of the schema")
    void sourceValuesAndBound() {
        var harness = SchemaResource.object(
                SchemaResource.object(
                        SchemaResource.object(SchemaResource.load(SCHEMA).get("properties")).get("harnesses"))
                        .get("additionalProperties"));
        var models = SchemaResource.object(SchemaResource.object(harness.get("properties")).get("models"));
        var model = SchemaResource.object(models.get("items"));
        var properties = SchemaResource.object(model.get("properties"));

        assertAll(
                () -> assertEquals(Arrays.stream(ModelSource.values()).map(ModelSource::wireName).toList(),
                        names(SchemaResource.array(SchemaResource.object(properties.get("source")).get("enum"))),
                        "The source values are the schema's"),
                () -> {
                    var bound = ((Number) SchemaResource.object(properties.get("model")).get("maxLength")).intValue();
                    var recorded = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
                    assertDoesNotThrow(() -> new CatalogueModel("m".repeat(bound), ModelSource.OPERATOR, recorded),
                            "The schema's bound is the codec's bound");
                    assertThrows(IllegalArgumentException.class,
                            () -> new CatalogueModel("m".repeat(bound + 1), ModelSource.OPERATOR, recorded),
                            "One character above the bound is refused");
                });
    }

    @Test
    @DisplayName("a written catalogue is valid against the schema")
    void writtenCatalogueIsValid() {
        var recorded = Instant.ofEpochSecond(Generators.longs(1_600_000_000L, 1_900_000_000L).next());
        var written = ModelCatalogueCodec.write(Map.of("opencode",
                List.of(new CatalogueModel("deepseek-v4.1", ModelSource.OPERATOR, recorded))), 1);

        assertEquals(Set.of(), validator().validate(new String(written, UTF_8), InputFormat.JSON),
                () -> "The written catalogue is valid: " + new String(written, UTF_8));
    }

    private static JsonSchema validator() {
        return JsonSchemaFactory.getInstance(SpecVersion.VersionFlag.V202012).getSchema(SchemaResource.text(SCHEMA));
    }

    private static Map<?, ?> document(byte[] written) {
        return SchemaResource.object(SchemaResource.parse(new String(written, UTF_8)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
