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
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.PairRecovery;
import de.planmarshall.core.store.StoreDigest;
import de.planmarshall.runtime.testing.generators.RuntimeGenerators;

/**
 * Proves that the builder of {@link Enrolments.Rebaseline} builds the value its canonical constructor builds, including
 * its null refusal for the file. Every value is generated; the recorded and the current digest are drawn from the
 * generator artifact of {@code pm-runtime}, and an empty settled list is the normal case.
 */
@EnableGeneratorController
@DisplayName("Enrolments.Rebaseline builder")
class EnrolmentsRecordsTest {

    @Test
    @DisplayName("the re-baseline builder builds the re-baseline the constructor builds")
    void rebaselineBuilderEqualsConstructor() {
        var file = Path.of("/tmp", Generators.letterStrings(1, 20).next(), "enrolment.json");
        var recorded = new StoreDigest(Generators.longs(0, 10_000).next(),
                RuntimeGenerators.sha256Digests().next());
        var current = new StoreDigest(Generators.longs(0, 10_000).next(),
                RuntimeGenerators.sha256Digests().next());
        var settled = List.<PairRecovery>of();

        var constructed = new Enrolments.Rebaseline(file, recorded, current, settled);
        var built = Enrolments.Rebaseline.builder()
                .file(file)
                .recorded(recorded)
                .current(current)
                .settled(settled)
                .build();

        assertEquals(constructed, built, "the builder builds the same re-baseline");
    }

    @Test
    @DisplayName("the builder refuses a missing file like the constructor")
    void rebaselineBuilderRefusesMissingFile() {
        var refusal = assertThrows(NullPointerException.class, () -> Enrolments.Rebaseline.builder().build());

        assertEquals("file", refusal.getMessage(), "the refusal names the enrolment file");
    }
}
