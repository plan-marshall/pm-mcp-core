/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.endpoint;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.runtime.launch.HarnessEnrolment;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link EndpointEnrolment.Refreshed} builds the value its canonical constructor builds,
 * including its null refusal for the enrolment. Every value is generated; the enrolment is the local form with an
 * absolute executable path, and the read changed one model.
 */
@EnableGeneratorController
@DisplayName("EndpointEnrolment.Refreshed builder")
class EndpointEnrolmentRecordsTest {

    @Test
    @DisplayName("the builder builds the refreshed enrolment the constructor builds")
    void refreshedBuilderEqualsConstructor() {
        var enrolment = enrolment();
        var changedModels = List.of(Generators.letterStrings(1, 20).next());
        var written = Generators.booleans().next();
        var detail = Generators.letterStrings(1, 40).next();

        var constructed = new EndpointEnrolment.Refreshed(enrolment, changedModels, written, detail);
        var built = EndpointEnrolment.Refreshed.builder()
                .enrolment(enrolment)
                .changedModels(changedModels)
                .written(written)
                .detail(detail)
                .build();

        assertEquals(constructed, built, "the builder builds the same refreshed enrolment");
    }

    @Test
    @DisplayName("the builder refuses a missing enrolment like the constructor")
    void refreshedBuilderRefusesMissingEnrolment() {
        var refusal = assertThrows(NullPointerException.class, () -> EndpointEnrolment.Refreshed.builder().build());

        assertEquals("enrolment", refusal.getMessage(), "the refusal names the enrolment");
    }

    private static HarnessEnrolment enrolment() {
        return new HarnessEnrolment("local-runner",
                Path.of("/tmp", Generators.letterStrings(1, 12).next(), "runner"),
                RuntimeGenerators.sha256Digests().next(), "1.0.0", Instant.EPOCH, Instant.EPOCH,
                new HarnessEnrolment.UsageReport(HarnessEnrolment.UsageSource.DECLARED, Map.of()), Map.of(), Map.of());
    }
}
