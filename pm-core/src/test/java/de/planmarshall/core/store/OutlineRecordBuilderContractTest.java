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

import de.planmarshall.core.store.OutlineRecord.AffectedFile;
import de.planmarshall.core.store.OutlineRecord.ChangeType;
import de.planmarshall.core.store.OutlineRecord.Deliverable;
import de.planmarshall.core.store.OutlineRecord.ExecutionMode;
import de.planmarshall.core.store.OutlineRecord.FileIntent;
import de.planmarshall.core.store.OutlineRecord.Profile;
import de.planmarshall.core.store.OutlineRecord.ScopeEstimate;
import de.planmarshall.core.store.OutlineRecord.Verification;

@EnableGeneratorController
@DisplayName("Outline record builder contract")
class OutlineRecordBuilderContractTest {

    private static Deliverable deliverable() {
        return new Deliverable(1, "Write the intent record", "Record the accepted intent",
                ChangeType.FEATURE, ExecutionMode.AUTOMATED, "core-store", "pm-core", List.of(), null,
                List.of(Profile.IMPLEMENTATION),
                List.of(new AffectedFile("pm-core/src/main/java/A.java", FileIntent.WRITE_NEW, null)),
                "Replace the placeholder with the record",
                List.of(new Verification("mvn verify", "the build is green")),
                List.of("AC-1"), List.of(), List.of(), List.of());
    }

    @Test
    @DisplayName("the builder builds the constructor's outline")
    void builderBuildsOutline() {
        var canonical = new OutlineRecord(Generators.letterStrings(20, 80).next(), null,
                ScopeEstimate.SINGLE_MODULE, List.of(deliverable()));

        var built = OutlineRecord.builder().summary(canonical.summary()).overview(canonical.overview())
                .scopeEstimate(canonical.scopeEstimate()).deliverables(canonical.deliverables()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new OutlineRecord(null, canonical.overview(), canonical.scopeEstimate(),
                        canonical.deliverables()),
                "the constructor refuses an absent summary");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> OutlineRecord.builder().overview(canonical.overview())
                        .scopeEstimate(canonical.scopeEstimate()).deliverables(canonical.deliverables()).build(),
                "the builder refuses an absent summary");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent summary with the same message"));
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
                .verification(canonical.verification()).satisfies(canonical.satisfies())
                .addedDependencies(canonical.addedDependencies()).interfaces(canonical.interfaces())
                .keyLinks(canonical.keyLinks()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Deliverable(canonical.number(), canonical.title(), canonical.intentGloss(), null,
                        canonical.executionMode(), canonical.domain(), canonical.module(), canonical.depends(),
                        canonical.designNotes(), canonical.profiles(), canonical.affectedFiles(),
                        canonical.changePerFile(), canonical.verification(), canonical.satisfies(),
                        canonical.addedDependencies(), canonical.interfaces(), canonical.keyLinks()),
                "the constructor refuses an absent change type");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> Deliverable.builder().number(canonical.number()).title(canonical.title())
                        .intentGloss(canonical.intentGloss()).executionMode(canonical.executionMode())
                        .domain(canonical.domain()).module(canonical.module()).depends(canonical.depends())
                        .designNotes(canonical.designNotes()).profiles(canonical.profiles())
                        .affectedFiles(canonical.affectedFiles()).changePerFile(canonical.changePerFile())
                        .verification(canonical.verification()).satisfies(canonical.satisfies())
                        .addedDependencies(canonical.addedDependencies()).interfaces(canonical.interfaces())
                        .keyLinks(canonical.keyLinks()).build(),
                "the builder refuses an absent change type");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent change type alike"));
    }
}
