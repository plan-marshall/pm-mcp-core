/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.enrolment;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.EnrolmentCodec;
import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * Consumes the generator artifact of {@code pm-core} (PLAN-V2-107): an enrolment the foundation's generator produced is
 * written and read back by the enrolment codec, so the generated value is one the production schema accepts.
 */
@DisplayName("Generated enrolment")
@EnableGeneratorController
class GeneratedEnrolmentTest {

    @Test
    @DisplayName("the enrolment codec reads back a generated enrolment")
    void shouldRoundTripAGeneratedEnrolment() {
        var enrolment = CoreGenerators.projectEnrolments().next();
        var file = Path.of("enrolments", enrolment.projectId() + ".json");

        var document = EnrolmentCodec.write(enrolment);
        var read = EnrolmentCodec.read(file, document);

        assertNotNull(read, "The codec reads a generated enrolment");
        assertEquals(enrolment, read, "The codec preserves the generated enrolment");
    }
}
