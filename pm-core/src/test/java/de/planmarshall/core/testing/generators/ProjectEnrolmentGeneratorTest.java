/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.ProjectEnrolment;

@DisplayName("Project enrolment generator")
@EnableGeneratorController
class ProjectEnrolmentGeneratorTest {

    private final ProjectEnrolmentGenerator generator = new ProjectEnrolmentGenerator();

    @Test
    @DisplayName("Should yield valid and distinct project enrolments")
    void shouldYieldValidAndDistinctProjectEnrolments() {
        var first = generator.next();
        var second = generator.next();

        assertAll("generated project enrolments",
                () -> assertNotNull(first, "The generator yields a value"),
                () -> assertNotEquals(first, second, "Two calls yield distinct values"),
                () -> assertTrue(first.projectRoot().startsWith("/tmp/"), "The root is an absolute path"),
                () -> assertNotNull(first.build(), "The enrolment carries the build defaults"));
    }

    @Test
    @DisplayName("Should report the generated type")
    void shouldReportTheGeneratedType() {
        assertEquals(ProjectEnrolment.class, generator.getType(), "The generator names the enrolment type");
    }
}
