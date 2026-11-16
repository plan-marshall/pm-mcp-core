/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.store;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.Instant;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.PlanOverrides.Axis;
import de.planmarshall.core.store.PlanOverrides.Channel;
import de.planmarshall.core.store.PlanOverrides.Integration;
import de.planmarshall.core.store.PlanOverrides.Posture;
import de.planmarshall.core.store.PlanOverrides.Providers;
import de.planmarshall.core.store.PlanOverrides.Role;
import de.planmarshall.core.store.PlanOverrides.UpdatedBy;

/**
 * {@link PlanOverridesCodec} keeps parity with the schema of Store Schemas § Plan Overrides: the written document carries
 * the schema's members in its order, its format version is the schema's const, its axis keys, posture, integration and
 * channel values are the schema's closed lists, and its length bounds are the schema's. The schema is the file
 * {@code plan-overrides.schema.json} beside the model in {@code pm-core/src/main/resources/de/planmarshall/core/store/},
 * read from the class path through {@link SchemaResource}.
 */
@DisplayName("Plan overrides schema parity")
class PlanOverridesSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/core/store/plan-overrides.schema.json";

    @Test
    @DisplayName("a written document has the schema's members in the schema's order")
    void memberOrder() {
        var schema = SchemaResource.load(SCHEMA);
        var written = written();

        assertAll(
                () -> assertEquals(names(SchemaResource.object(schema.get("properties")).keySet()),
                        names(written.keySet()), "the written members are the schema's in its order"),
                () -> assertEquals(SchemaResource.object(SchemaResource.object(schema.get("properties"))
                                .get("format_version")).get("const"), written.get("format_version"),
                        "the format version is the schema const"));
    }

    @Test
    @DisplayName("the axis keys, posture, integration and channel values are the schema's")
    void closedValues() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var axes = SchemaResource.object(properties.get("axes"));
        var posture = SchemaResource.object(properties.get("posture"));
        var integrations = SchemaResource.object(properties.get("integrations"));
        var channel = SchemaResource.object(SchemaResource.object(SchemaResource.object(
                properties.get("updated_by")).get("properties")).get("channel"));

        assertAll(
                () -> assertEquals(Arrays.stream(Axis.values()).map(Axis::key).toList(),
                        names(SchemaResource.object(axes.get("properties")).keySet()),
                        "the axis keys are the schema's"),
                () -> assertEquals(Arrays.stream(Posture.values()).map(Posture::wireName).toList(),
                        names(SchemaResource.array(posture.get("enum"))), "the postures are the schema's"),
                () -> assertEquals(Arrays.stream(Integration.values()).map(Integration::wireName).toList(),
                        names(SchemaResource.array(SchemaResource.object(integrations.get("additionalProperties"))
                                .get("enum"))), "the integration variants are the schema's"),
                () -> assertEquals(Arrays.stream(Channel.values()).map(Channel::wireName).toList(),
                        names(SchemaResource.array(channel.get("enum"))), "the channels are the schema's"));
    }

    @Test
    @DisplayName("the length bounds of the codec are the schema's")
    void bounds() {
        var properties = SchemaResource.object(SchemaResource.load(SCHEMA).get("properties"));
        var role = SchemaResource.object(SchemaResource.object(properties.get("roles"))
                .get("additionalProperties"));
        var roleProperties = SchemaResource.object(role.get("properties"));

        assertAll(
                () -> assertEquals(PlanOverridesCodec.MODEL_MAX_LENGTH,
                        ((Number) SchemaResource.object(roleProperties.get("model")).get("maxLength")).intValue(),
                        "the model bound is the schema's"),
                () -> assertEquals(PlanOverridesCodec.EFFORT_MAX_LENGTH,
                        ((Number) SchemaResource.object(roleProperties.get("effort")).get("maxLength")).intValue(),
                        "the effort bound is the schema's"),
                () -> assertEquals(PlanOverridesCodec.BASE_BRANCH_MAX_LENGTH,
                        ((Number) SchemaResource.object(properties.get("base_branch")).get("maxLength")).intValue(),
                        "the base branch bound is the schema's"));
    }

    private Map<?, ?> written() {
        var roles = new LinkedHashMap<String, Role>();
        roles.put("implementer", new Role("claude", "model", "high"));
        return SchemaResource.object(SchemaResource.parse(new String(PlanOverridesCodec.write(
                PlanOverrides.builder()
                        .revision(1)
                        .roles(roles)
                        .axes(Map.of(Axis.LANDING, "landing.pull-request"))
                        .posture(Posture.STANDARD)
                        .baseBranch("main")
                        .integrations(Map.of("github", Integration.CLI))
                        .providers(Providers.none())
                        .updatedAt(Instant.EPOCH)
                        .updatedBy(new UpdatedBy(Channel.CLI, null))
                        .build()), UTF_8)));
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
