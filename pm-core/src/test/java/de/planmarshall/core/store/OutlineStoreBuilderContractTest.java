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
import java.time.Instant;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.OutlineStore.Revision;

@EnableGeneratorController
@DisplayName("Outline store builder contract")
class OutlineStoreBuilderContractTest {

    @Test
    @DisplayName("the builder builds the constructor's revision")
    void builderBuildsRevision() {
        var outlineRecord = new OutlineRecord(Generators.letterStrings(20, 80).next(), null,
                OutlineRecord.ScopeEstimate.SINGLE_MODULE,
                List.of(OutlineFixtures.deliverable(1, "Write the intent record", List.of(), "AC-1", "A note")));
        var canonical = new Revision(1, Instant.parse("2026-10-27T11:00:00Z"), "TASK-001", outlineRecord);

        var built = Revision.builder().revision(canonical.revision()).submittedAt(canonical.submittedAt())
                .taskId(canonical.taskId()).outlineRecord(canonical.outlineRecord()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Revision(canonical.revision(), null, canonical.taskId(),
                        canonical.outlineRecord()),
                "the constructor refuses an absent instant");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> Revision.builder().revision(canonical.revision()).taskId(canonical.taskId())
                        .outlineRecord(canonical.outlineRecord()).build(),
                "the builder refuses an absent instant");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent instant with the same message"));
    }
}
