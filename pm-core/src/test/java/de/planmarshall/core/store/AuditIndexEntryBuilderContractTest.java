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

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link AuditIndexEntry} builds the value the canonical constructor builds, and an absent previous hash
 * is refused with the same refusal.
 */
@DisplayName("Audit index entry builder")
class AuditIndexEntryBuilderContractTest {

    private static final String EVENT_ID = "0190a5b4-6b1f-7a2c-8d3e-4f5a6b7c8d9e";

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent previous hash")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentPreviousHash() {
        var file = "audit-" + EVENT_ID + ".jsonl";
        var canonical = new AuditIndexEntry("a".repeat(64), file, EVENT_ID, EVENT_ID, 3, "b".repeat(64));
        var built = AuditIndexEntry.builder().prevHash(canonical.prevHash()).file(canonical.file())
                .firstEventId(canonical.firstEventId()).lastEventId(canonical.lastEventId())
                .recordCount(canonical.recordCount()).checkpointHash(canonical.checkpointHash()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new AuditIndexEntry(null, file, EVENT_ID, EVENT_ID, 3, "b".repeat(64)),
                            "the constructor refuses a null previous hash");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> AuditIndexEntry.builder().file(file).firstEventId(EVENT_ID).lastEventId(EVENT_ID)
                                    .recordCount(3).checkpointHash("b".repeat(64)).build(),
                            "the builder refuses a missing previous hash");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent previous hash with the same message");
                });
    }
}
