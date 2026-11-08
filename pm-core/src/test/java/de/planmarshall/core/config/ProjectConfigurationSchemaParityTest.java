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
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.lang.reflect.RecordComponent;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.function.Executable;

import de.planmarshall.core.SchemaResource;
import de.planmarshall.core.store.QualificationSettings;

/**
 * The project-configuration schema files of {@code store-schemas/07} and {@code store-schemas/08} are the files of
 * {@code de.planmarshall.core.config}, and the one configuration codec that exists — {@link QualificationSettings} for
 * {@code local-config.json}'s {@code settings.qualification} — keeps parity with the file: its members and its bounds
 * are the file's. The four other configuration files have no codec yet; their tests are the task of the package that
 * builds it.
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

    private static List<String> componentNames() {
        return Arrays.stream(QualificationSettings.class.getRecordComponents()).map(RecordComponent::getName)
                .map(ProjectConfigurationSchemaParityTest::snake).toList();
    }

    private static String snake(String name) {
        return name.replaceAll("([A-Z])", "_$1").toLowerCase(Locale.ROOT);
    }

    @Test
    @DisplayName("every configuration schema file is a JSON object with its id and format version 1")
    void filesAreSchemas() {
        assertAll(FILES.stream().map(file -> (Executable) () -> {
            var schema = object(SchemaResource.parse(SchemaResource.text(DIRECTORY + file)));

            assertAll(
                    () -> assertInstanceOf(String.class, schema.get("$id"), file + " should carry its id"),
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
                () -> assertEquals(componentNames(), properties.keySet().stream().map(String::valueOf).toList(),
                        "the settings components should equal the schema"),
                () -> assertNull(qualification.get("required"),
                        "the file declares no required settings member"),
                () -> assertEquals(100, ((Number) object(properties.get("agreement_level")).get("maximum")).intValue(),
                        "the agreement level maximum stays 100"),
                () -> assertEquals(QualificationSettings.DEFAULT_AGREEMENT_LEVEL,
                        ((Number) object(properties.get("agreement_level")).get("default")).intValue(),
                        "the compiled agreement default should equal the file's default"),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new QualificationSettings(101, QualificationSettings.DEFAULT_ALLOWED_GAP, List.of(),
                                false),
                        "the settings refuse an agreement level above the file's maximum"));
    }
}
