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
import java.util.Properties;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The reader of a numeric limit from a properties resource (PM-ARCH-8, PM-IMPL-5): the present resource is read, and
 * an absent resource, an absent key, a non-numeric value and a negative value fail the load instead of defaulting. The
 * int-ranged read reads up to {@link Integer#MAX_VALUE} and refuses a larger value the same way, by name; the two edge
 * values are the contract of that range.
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

    @Test
    @DisplayName("reads an int-ranged value of the resource")
    void readsAnIntRangedValue() {
        assertEquals(42, LimitProperties.readInt(RESOURCE, KEY), "the int reader must return the resource value");
    }

    @Test
    @DisplayName("reads the largest int value")
    void readsTheLargestIntValue() {
        assertEquals(Integer.MAX_VALUE, LimitProperties.intRanged(Integer.MAX_VALUE, KEY, RESOURCE),
                "the largest int value must be read");
    }

    @Test
    @DisplayName("a value above the int range fails the load, naming the resource and key")
    void aValueAboveTheIntRangeFails() {
        var refusal = assertThrows(IllegalStateException.class,
                () -> LimitProperties.intRanged(Integer.MAX_VALUE + 1L, KEY, RESOURCE),
                "a value above the int range must fail the load");

        assertAll(
                () -> assertTrue(refusal.getMessage().contains(KEY), "the refusal must name the limit key"),
                () -> assertTrue(refusal.getMessage().contains(RESOURCE), "the refusal must name the resource"));
    }
}
