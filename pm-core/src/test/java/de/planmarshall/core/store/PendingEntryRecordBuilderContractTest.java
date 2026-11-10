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
import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.PendingEntryRecord.Bootstrap;
import de.planmarshall.core.store.PendingEntryRecord.Kind;

@DisplayName("Pending entry record builder contract")
class PendingEntryRecordBuilderContractTest {

    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    private static Bootstrap bootstrap() {
        return new Bootstrap("bootstrap.start", List.of(), "{}", null, "{}", null, null);
    }

    @Test
    @DisplayName("the builder builds the constructor's pending entry")
    void builderBuildsPendingEntry() {
        var canonical = new PendingEntryRecord(new EntryId("ent-0123456789abcdef"), Kind.PLAN, null, null,
                null, AT, AT, bootstrap());

        var built = PendingEntryRecord.builder().entryId(canonical.entryId()).kind(canonical.kind())
                .requestedId(canonical.requestedId()).hint(canonical.hint()).fromEpic(canonical.fromEpic())
                .createdAt(canonical.createdAt()).updatedAt(canonical.updatedAt())
                .bootstrap(canonical.bootstrap()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new PendingEntryRecord(null, canonical.kind(), canonical.requestedId(), canonical.hint(),
                        canonical.fromEpic(), canonical.createdAt(), canonical.updatedAt(),
                        canonical.bootstrap()),
                "the constructor refuses an absent entry id");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> PendingEntryRecord.builder().kind(canonical.kind()).requestedId(canonical.requestedId())
                        .hint(canonical.hint()).fromEpic(canonical.fromEpic()).createdAt(canonical.createdAt())
                        .updatedAt(canonical.updatedAt()).bootstrap(canonical.bootstrap()).build(),
                "the builder refuses an absent entry id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent entry id with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's bootstrap state")
    void builderBuildsBootstrap() {
        var canonical = bootstrap();

        var built = Bootstrap.builder().state(canonical.state()).callStack(canonical.callStack())
                .facts(canonical.facts()).source(canonical.source()).options(canonical.options())
                .pendingCondition(canonical.pendingCondition()).lastRefusal(canonical.lastRefusal()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Bootstrap(null, canonical.callStack(), canonical.facts(), canonical.source(),
                        canonical.options(), canonical.pendingCondition(), canonical.lastRefusal()),
                "the constructor refuses an absent state");
        var fromBuilder = assertThrows(NullPointerException.class,
                () -> Bootstrap.builder().callStack(canonical.callStack()).facts(canonical.facts())
                        .source(canonical.source()).options(canonical.options())
                        .pendingCondition(canonical.pendingCondition()).lastRefusal(canonical.lastRefusal())
                        .build(),
                "the builder refuses an absent state");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent state with the same message"));
    }
}
