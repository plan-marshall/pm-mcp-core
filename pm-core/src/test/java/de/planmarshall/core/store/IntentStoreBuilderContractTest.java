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

import java.time.Instant;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link IntentStore.Revision} builds the value the canonical constructor builds, and an absent intent
 * record is refused with the same refusal.
 */
@DisplayName("Intent store revision builder")
class IntentStoreBuilderContractTest {

    private static final Instant SUBMITTED_AT = Instant.parse("2026-10-28T11:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent intent record")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentIntentRecord() {
        var intentRecord = IntentFixtures.valid();
        var canonical = new IntentStore.Revision(1, SUBMITTED_AT, "TASK-001", intentRecord);
        var built = IntentStore.Revision.builder().revision(canonical.revision())
                .submittedAt(canonical.submittedAt()).taskId(canonical.taskId())
                .intentRecord(canonical.intentRecord()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new IntentStore.Revision(1, SUBMITTED_AT, "TASK-001", null),
                            "the constructor refuses an absent intent record");
                    var incomplete = IntentStore.Revision.builder().revision(1).submittedAt(SUBMITTED_AT)
                            .taskId("TASK-001");
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses an absent intent record");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent intent record with the same message");
                });
    }
}
