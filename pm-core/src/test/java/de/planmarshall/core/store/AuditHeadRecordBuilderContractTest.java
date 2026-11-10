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
 * The builder of {@link AuditHeadRecord} builds the value the canonical constructor builds, and an absent file is
 * refused with the same refusal.
 */
@DisplayName("Audit head record builder")
class AuditHeadRecordBuilderContractTest {

    private static final String EVENT_ID = "0190a5b4-6b1f-7a2c-8d3e-4f5a6b7c8d9e";

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent file")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentFile() {
        var canonical = new AuditHeadRecord(AuditHeadRecord.CURRENT_LOG_FILE, 1, EVENT_ID, "a".repeat(64),
                "b".repeat(64), "c".repeat(64));
        var built = AuditHeadRecord.builder().file(canonical.file()).recordCount(canonical.recordCount())
                .lastEventId(canonical.lastEventId()).headHash(canonical.headHash())
                .indexHeadHash(canonical.indexHeadHash()).hmac(canonical.hmac()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new AuditHeadRecord(null, 1, EVENT_ID, "a".repeat(64), "b".repeat(64),
                                    "c".repeat(64)),
                            "the constructor refuses a null file");
                    var fromBuilder = assertThrows(NullPointerException.class,
                            () -> AuditHeadRecord.builder().recordCount(1).lastEventId(EVENT_ID)
                                    .headHash("a".repeat(64)).indexHeadHash("b".repeat(64)).hmac("c".repeat(64))
                                    .build(),
                            "the builder refuses a missing file");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent file with the same message");
                });
    }
}
