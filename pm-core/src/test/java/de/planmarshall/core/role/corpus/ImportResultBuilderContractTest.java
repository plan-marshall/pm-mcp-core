/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.corpus;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link ImportResult.MissedSource} builds the value the canonical constructor builds, and a missing
 * name is refused with the same refusal.
 */
@EnableGeneratorController
@DisplayName("Import result builder")
class ImportResultBuilderContractTest {

    @Test
    @DisplayName("the missed source builder builds the constructor's value and refuses an absent name alike")
    void shouldBuildMissedSourceLikeTheConstructorAndRefuseAnAbsentNameAlike() {
        var canonical = new ImportResult.MissedSource(Generators.letterStrings(3, 12).next(),
                ImportResult.Reason.UNREACHABLE, "data/rows.csv", Generators.letterStrings(10, 40).next());
        var built = ImportResult.MissedSource.builder().name(canonical.name()).reason(canonical.reason())
                .file(canonical.file()).detail(canonical.detail()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new ImportResult.MissedSource(null, ImportResult.Reason.UNREACHABLE,
                                    canonical.file(), canonical.detail()),
                            "the constructor refuses a null name");
                    var incomplete = ImportResult.MissedSource.builder()
                            .reason(ImportResult.Reason.UNREACHABLE).file(canonical.file())
                            .detail(canonical.detail());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing name");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent name with the same message");
                });
    }
}
