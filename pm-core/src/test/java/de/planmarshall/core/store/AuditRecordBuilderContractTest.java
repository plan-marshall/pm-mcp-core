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
import java.util.Map;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

/**
 * The builder of {@link AuditRecord} builds the value the canonical constructor builds, and an absent event id is
 * refused with the same refusal.
 */
@DisplayName("Audit record builder")
class AuditRecordBuilderContractTest {

    private static final String EVENT_ID = "0190a5b4-6b1f-7a2c-8d3e-4f5a6b7c8d9e";
    private static final String PREV_HASH = "a".repeat(64);
    private static final Instant AT = Instant.parse("2026-10-17T10:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's value and refuses an absent event id")
    void shouldBuildLikeTheConstructorAndRefuseAnAbsentEventId() {
        var canonical = new AuditRecord(EVENT_ID, PREV_HASH, AT, AuditRecord.Category.STORE, "store.locked",
                AuditRecord.Scope.PLAN, "plan-one", "operator", AuditRecord.Outcome.SUCCEEDED,
                Map.of("lease", "plan-one"));
        var built = AuditRecord.builder().eventId(canonical.eventId()).prevHash(canonical.prevHash())
                .timestamp(canonical.timestamp()).category(canonical.category()).event(canonical.event())
                .scopeType(canonical.scopeType()).scopeId(canonical.scopeId()).actor(canonical.actor())
                .outcome(canonical.outcome()).details(canonical.details()).build();

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> {
                    Map<String, Object> details = Map.of();
                    var fromConstructor = assertThrows(NullPointerException.class,
                            () -> new AuditRecord(null, PREV_HASH, AT, AuditRecord.Category.STORE, "store.locked",
                                    AuditRecord.Scope.PLAN, "plan-one", "operator", AuditRecord.Outcome.SUCCEEDED,
                                    details),
                            "the constructor refuses a null event id");
                    var incomplete = AuditRecord.builder().prevHash(PREV_HASH).timestamp(AT)
                            .category(AuditRecord.Category.STORE).event("store.locked")
                            .scopeType(AuditRecord.Scope.PLAN).scopeId("plan-one").actor("operator")
                            .outcome(AuditRecord.Outcome.SUCCEEDED).details(Map.of());
                    var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                            "the builder refuses a missing event id");
                    assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                            "both refuse an absent event id with the same message");
                });
    }
}
