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

import java.nio.file.Path;
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link JournalScope} builds the value the canonical constructor builds, and an absent scope type is
 * refused with the same refusal.
 */
@DisplayName("Journal scope builder")
class JournalScopeBuilderContractTest {

    private static final Path BASE = Path.of("/pm-base");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent scope type")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentType() {
        var pathBase = BASE.resolve("machine");
        var journal = pathBase.resolve("mcp").resolve("transaction-journal.json");
        var sidecar = pathBase.resolve("mcp").resolve("digests.json");
        var lockKey = LockKey.machineScope(BASE);
        var canonical = new JournalScope(JournalScopeType.MACHINE, null, pathBase, journal, sidecar,
                List.of(pathBase), lockKey);
        var built = JournalScope.builder().type(canonical.type()).scopeId(canonical.scopeId())
                .pathBase(canonical.pathBase()).journalFile(canonical.journalFile())
                .sidecarFile(canonical.sidecarFile()).ownedDirectories(canonical.ownedDirectories())
                .lockKey(canonical.lockKey()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var ownedDirectories = List.of(pathBase);
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new JournalScope(null, null, pathBase, journal, sidecar, ownedDirectories,
                                    lockKey),
                            "the constructor refuses a null scope type");
                    var incomplete = JournalScope.builder().pathBase(pathBase).journalFile(journal)
                            .sidecarFile(sidecar).ownedDirectories(List.of(pathBase)).lockKey(lockKey);
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing scope type");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent scope type with the same message");
                });
    }
}
