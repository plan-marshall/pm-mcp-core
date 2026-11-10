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
import java.util.OptionalLong;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import de.planmarshall.core.store.TransactionJournal.Mode;
import de.planmarshall.core.store.TransactionJournal.Target;

@DisplayName("Transaction journal builder contract")
class TransactionJournalBuilderContractTest {

    private static final String SHA_256 = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
    private static final Instant AT = Instant.parse("2026-10-22T10:00:00Z");

    @Test
    @DisplayName("the builder builds the constructor's journal")
    void builderBuildsJournal() {
        var transactionId = new TransactionId("0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b");
        var target = new Target("mcp/status.json", Mode.REPLACE,
                "mcp/status.json.tmp." + transactionId.value(), OptionalLong.empty(),
                new StoreDigest(3, SHA_256));
        var canonical = new TransactionJournal(transactionId, JournalScopeType.PLAN, "plan-core", AT,
                List.of(target), List.of());

        var built = TransactionJournal.builder().transactionId(canonical.transactionId())
                .scopeType(canonical.scopeType()).scopeId(canonical.scopeId())
                .createdAt(canonical.createdAt()).targets(canonical.targets())
                .owedEvents(canonical.owedEvents()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new TransactionJournal(null, canonical.scopeType(), canonical.scopeId(),
                        canonical.createdAt(), canonical.targets(), canonical.owedEvents()),
                "the constructor refuses an absent transaction id");
        var incomplete = TransactionJournal.builder().scopeType(canonical.scopeType())
                .scopeId(canonical.scopeId()).createdAt(canonical.createdAt())
                .targets(canonical.targets()).owedEvents(canonical.owedEvents());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent transaction id");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent id with the same message"));
    }

    @Test
    @DisplayName("the builder builds the constructor's target")
    void builderBuildsTarget() {
        var canonical = new Target("mcp/status.json", Mode.REPLACE,
                "mcp/status.json.tmp.0192a3b4-c5d6-7e8f-9a0b-1c2d3e4f5a6b", OptionalLong.empty(),
                new StoreDigest(3, SHA_256));

        var built = Target.builder().path(canonical.path()).mode(canonical.mode())
                .stagedPath(canonical.stagedPath()).sizeBefore(canonical.sizeBefore())
                .newDigest(canonical.newDigest()).build();

        var fromConstructor = assertThrows(NullPointerException.class,
                () -> new Target(null, canonical.mode(), canonical.stagedPath(),
                        canonical.sizeBefore(), canonical.newDigest()),
                "the constructor refuses an absent path");
        var incomplete = Target.builder().mode(canonical.mode()).stagedPath(canonical.stagedPath())
                .sizeBefore(canonical.sizeBefore()).newDigest(canonical.newDigest());
        var fromBuilder = assertThrows(NullPointerException.class, incomplete::build,
                "the builder refuses an absent path");

        assertAll(
                () -> assertEquals(canonical, built, "the builder builds the constructor's value"),
                () -> assertEquals(fromConstructor.getMessage(), fromBuilder.getMessage(),
                        "both refuse an absent path with the same message"));
    }
}
