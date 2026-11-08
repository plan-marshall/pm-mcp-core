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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reader of a numeric limit from a properties resource (PM-ARCH-8, PM-IMPL-5): the present resource is read, and
 * an absent resource, an absent key, a non-numeric value and a negative value fail the load instead of defaulting.
 */
@EnableGeneratorController
@DisplayName("LimitProperties")
class LimitPropertiesTest {

    private static final String RESOURCE = "/de/planmarshall/core/config/alternate-limits.properties";
    private static final String KEY = "pm.core.test.limit";

    @Test
    @DisplayName("reads the value of the resource")
    void readsTheValue() {
        assertEquals(42, LimitProperties.read(RESOURCE, KEY), "the reader must return the resource value");
    }

    @Test
    @DisplayName("an absent resource fails the load instead of defaulting")
    void anAbsentResourceFails() {
        assertThrows(IllegalStateException.class,
                () -> LimitProperties.read("/de/planmarshall/core/config/absent.properties", KEY),
                "an absent resource must fail the load");
    }

    @Test
    @DisplayName("an absent key fails the load instead of defaulting")
    void anAbsentKeyFails() {
        var properties = new Properties();

        assertThrows(IllegalStateException.class, () -> LimitProperties.value(properties, KEY, "limits"),
                "an absent key must fail the load");
    }

    @Test
    @DisplayName("a non-numeric value fails the load instead of defaulting")
    void aNonNumericValueFails() {
        var properties = new Properties();
        properties.setProperty(KEY, "not-a-number");

        assertThrows(IllegalStateException.class, () -> LimitProperties.value(properties, KEY, "limits"),
                "a non-numeric value must fail the load");
    }

    @Test
    @DisplayName("a negative value fails the load instead of defaulting")
    void aNegativeValueFails() {
        var properties = new Properties();
        properties.setProperty(KEY, "-1");

        assertThrows(IllegalStateException.class, () -> LimitProperties.value(properties, KEY, "limits"),
                "a negative value must fail the load");
    }
}
