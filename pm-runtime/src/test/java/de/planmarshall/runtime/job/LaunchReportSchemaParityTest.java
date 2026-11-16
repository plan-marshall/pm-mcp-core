/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.job;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.JobRecord.Confinement;
import de.planmarshall.core.store.JobRecord.GitMetadata;

/**
 * {@link LaunchReport} keeps parity with {@code launch-report.schema.json}: the five members in schema order, the four
 * required members, the {@code confinement} and {@code git_metadata} enumerations, the Landlock minimum and the closed
 * object. The schema is the file beside the class, read from the class path through {@link SchemaResource}.
 * <p>
 * Every datum in this class is a contract: the schema file, its members and bounds are the specification.
 */
@DisplayName("Launch report schema parity")
class LaunchReportSchemaParityTest {

    private static final String SCHEMA = "de/planmarshall/runtime/job/launch-report.schema.json";

    /** The members of the report in the order the schema declares them. */
    private static final List<String> MEMBERS = List.of("pid", "process_started_at", "confinement", "landlock_abi",
            "git_metadata");

    private static Map<?, ?> schema;
    private static Map<?, ?> properties;

    @BeforeAll
    static void readSchema() {
        schema = SchemaResource.load(SCHEMA);
        properties = SchemaResource.object(schema.get("properties"));
    }

    @Test
    @DisplayName("the members and the required members are the schema's, in its order")
    void membersEqualSchema() {
        assertAll("the launch report members equal the schema's",
                () -> assertEquals(MEMBERS, names(properties.keySet()), "the properties follow the schema order"),
                () -> assertEquals(List.of("pid", "process_started_at", "confinement", "landlock_abi"),
                        names(SchemaResource.array(schema.get("required"))),
                        "the four members are required"),
                () -> assertNotEquals(Boolean.TRUE, schema.get("additionalProperties"), "the schema stays closed"));
    }

    @Test
    @DisplayName("the enumerations and the Landlock minimum are the parser's")
    void valuesEqualParser() {
        assertAll("the values equal the parser's",
                () -> assertEquals(wireNames(Confinement.values(), Confinement::wireName),
                        enumOf("confinement"), "the confinement values are the parser's"),
                () -> assertEquals(wireNames(GitMetadata.values(), GitMetadata::wireName),
                        enumOf("git_metadata"), "the git metadata values are the parser's"),
                () -> assertEquals(2, ((Number) SchemaResource.object(properties.get("landlock_abi"))
                        .get("minimum")).intValue(), "the Landlock ABI minimum is the parser's"));
    }

    private static Set<String> enumOf(String member) {
        return SchemaResource.array(SchemaResource.object(properties.get(member)).get("enum")).stream()
                .filter(Objects::nonNull).map(String::valueOf).collect(Collectors.toUnmodifiableSet());
    }

    private static <E> Set<String> wireNames(E[] values, Function<E, String> wireName) {
        return Arrays.stream(values).map(wireName).collect(Collectors.toUnmodifiableSet());
    }

    private static List<String> names(Collection<?> values) {
        return values.stream().map(String::valueOf).toList();
    }
}
