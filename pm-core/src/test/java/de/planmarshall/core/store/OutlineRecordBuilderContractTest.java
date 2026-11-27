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

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.OutlineRecord.Deliverable;

@EnableGeneratorController
@DisplayName("Outline record builder contract")
class OutlineRecordBuilderContractTest {

    private static Deliverable deliverable() {
        // The satisfies entry follows the identifier grammar AC-<number> of the record.
        return OutlineFixtures.deliverable(1, Generators.letterStrings(3, 60).next(), List.of(),
                "AC-" + Generators.integers(1, 99).next(), Generators.letterStrings(3, 20).next());
    }

    @Test
    @DisplayName("the builder builds the constructor's deliverable")
    void builderBuildsDeliverable() {
        var canonical = deliverable();

        var built = Deliverable.builder().number(canonical.number()).title(canonical.title())
                .intentGloss(canonical.intentGloss()).changeType(canonical.changeType())
                .executionMode(canonical.executionMode()).domain(canonical.domain()).module(canonical.module())
                .depends(canonical.depends()).designNotes(canonical.designNotes()).profiles(canonical.profiles())
                .affectedFiles(canonical.affectedFiles()).changePerFile(canonical.changePerFile())
                .verification(canonical.verification()).satisfies(canonical.satisfies()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Deliverable(canonical.number(), canonical.title(), canonical.intentGloss(), null,
                        canonical.executionMode(), canonical.domain(), canonical.module(), canonical.depends(),
                        canonical.designNotes(), canonical.profiles(), canonical.affectedFiles(),
                        canonical.changePerFile(), canonical.verification(), canonical.satisfies()),
                "the constructor refuses an absent change type");
        var incomplete = Deliverable.builder().number(canonical.number()).title(canonical.title())
                .intentGloss(canonical.intentGloss()).executionMode(canonical.executionMode())
                .domain(canonical.domain()).module(canonical.module()).depends(canonical.depends())
                .designNotes(canonical.designNotes()).profiles(canonical.profiles())
                .affectedFiles(canonical.affectedFiles()).changePerFile(canonical.changePerFile())
                .verification(canonical.verification()).satisfies(canonical.satisfies());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent change type");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent change type alike"));
    }
}
