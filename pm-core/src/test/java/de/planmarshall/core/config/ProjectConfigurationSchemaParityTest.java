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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.QualificationSettings;

/**
 * The project-configuration schema files of {@code store-schemas/07} and {@code store-schemas/08} are the files of
 * {@code de.planmarshall.core.config}, and the one configuration codec that exists — {@link QualificationSettings} for
 * {@code local-config.json}'s {@code settings.qualification} — keeps parity with the file: its members, its bounds and
 * its closed values are the file's. The four other configuration files have no codec yet; their tests are the task of
 * the package that builds it.
 */
@EnableGeneratorController
@DisplayName("Project configuration schema parity")
class ProjectConfigurationSchemaParityTest {

    private static final String DIRECTORY = "de/planmarshall/core/config/";
    private static final List<String> FILES = List.of("config-project.schema.json",
            "config-workflow.schema.json", "config-domains.schema.json", "config-build-map.schema.json",
            "local-config.schema.json");
    private static final String LOCAL_CONFIG = DIRECTORY + "local-config.schema.json";

    private static Map<?, ?> object(Object value) {
        return (Map<?, ?>) value;
    }

    private static Map<?, ?> qualification() {
        var local = object(SchemaResource.parse(SchemaResource.text(LOCAL_CONFIG)));
        var settings = object(object(local.get("$defs")).get("settings"));
        return object(object(settings.get("properties")).get("qualification"));
    }

    @Test
    @DisplayName("every configuration schema file is a JSON object with its id and format version 1")
    void filesAreSchemas() {
        assertAll(FILES.stream().map(file -> (org.junit.jupiter.api.function.Executable) () -> {
            var schema = object(SchemaResource.parse(SchemaResource.text(DIRECTORY + file)));

            assertAll(
                    () -> assertTrue(schema.get("$id") instanceof String, file + " should carry its id"),
                    () -> assertEquals(1, ((Number) object(object(schema.get("properties")).get("format_version"))
                            .get("const")).intValue(), file + " should stay at format version 1"));
        }));
    }

    @Test
    @DisplayName("the qualification settings keep parity with the local-config file")
    void qualificationParity() {
        var qualification = qualification();
        var properties = object(qualification.get("properties"));

        assertAll(
                () -> assertEquals(List.of("agreement_level", "allowed_gap", "skip_roles", "skip_decisions"),
                        properties.keySet().stream().map(String::valueOf).toList(),
                        "the settings members should equal the schema"),
                () -> assertTrue(qualification.get("required") == null,
                        "the file declares no required settings member"),
                () -> assertEquals(100, ((Number) object(properties.get("agreement_level")).get("maximum")).intValue(),
                        "the agreement level maximum stays 100"),
                () -> assertEquals(QualificationSettings.DEFAULT_AGREEMENT_LEVEL, 80,
                        "the compiled agreement default stays 80"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(101, QualificationSettings.DEFAULT_ALLOWED_GAP, List.of(),
                                false),
                        "the settings refuse an agreement level above the file's maximum"));
    }
}
