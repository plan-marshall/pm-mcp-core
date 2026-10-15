/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.audit;

import static de.planmarshall.runtime.audit.AuditTrailFixture.editLine;
import static de.planmarshall.runtime.audit.AuditTrailFixture.lines;
import static de.planmarshall.runtime.audit.AuditTrailFixture.removeLine;
import static de.planmarshall.runtime.audit.AuditTrailFixture.writeLines;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.TransactionId;

/**
 * The audit index: append-only and hash-chained, so an edited or removed entry breaks its chain.
 */
@DisplayName("The audit index")
class AuditIndexTest {

    @TempDir
    Path base;

    private AuditTrailFixture trail;
    private AuditIndex index;

    @BeforeEach
    void createIndex() {
        trail = new AuditTrailFixture(base);
        index = new AuditIndex(base);
    }

    private AuditIndexEntry entry() {
        var first = TransactionId.generate(trail.clock).value();
        var last = TransactionId.generate(trail.clock).value();
        return new AuditIndexEntry(AuditCodec.GENESIS_HASH, AuditIndexEntry.rotatedFileName(first), first, last, 3,
                AuditCodec.sha256Hex(first.getBytes(StandardCharsets.UTF_8)));
    }

    /** Appends the entries under the audit lock and returns the hashes of their lines. */
    private List<String> append(AuditIndexEntry... entries) {
        var hashes = new ArrayList<String>();
        trail.underAuditLock(transaction -> {
            for (var entry : entries) {
                hashes.add(index.append(transaction, entry, _ -> {
                }));
            }
        });
        return hashes;
    }

    @Test
    @DisplayName("an index that does not exist has no entry and the genesis hash")
    void empty() {
        var snapshot = index.snapshot();

        assertAll(
                () -> assertEquals(List.of(), snapshot.entries()),
                () -> assertEquals(AuditCodec.GENESIS_HASH, snapshot.headHash()),
                () -> assertEquals(OptionalLong.empty(), snapshot.firstBrokenLine()),
                () -> assertEquals(Optional.empty(), snapshot.last()),
                () -> assertEquals(Optional.empty(), snapshot.lastCheckpointHash()));
    }

    @Test
    @DisplayName("round trip: each entry chains to the line before it, the first to genesis")
    void roundTrip() {
        var first = entry();
        var second = entry();

        var hashes = append(first, second);

        var snapshot = index.snapshot();
        var lines = lines(index.file());
        assertAll(
                () -> assertEquals(List.of(first, second.withPrevHash(AuditCodec.sha256Hex(
                        lines.getFirst().getBytes(StandardCharsets.UTF_8)))), snapshot.entries()),
                () -> assertEquals(hashes.getLast(), snapshot.headHash()),
                () -> assertEquals(OptionalLong.empty(), snapshot.firstBrokenLine()),
                () -> assertEquals(Optional.of(second.checkpointHash()), snapshot.lastCheckpointHash()),
                () -> trail.underAuditLock(transaction -> assertEquals(snapshot, index.read(transaction))));
    }

    @Test
    @DisplayName("an edited entry breaks the chain at the line after it")
    void editedEntry() {
        append(entry(), entry(), entry());
        editLine(index.file(), 1, line -> line.replace("\"record_count\":3", "\"record_count\":4"));

        var snapshot = index.snapshot();

        assertAll(
                () -> assertEquals(OptionalLong.of(2), snapshot.firstBrokenLine()),
                () -> assertEquals(1, snapshot.entries().size()));
    }

    @Test
    @DisplayName("a removed entry breaks the chain where it was")
    void removedEntry() {
        append(entry(), entry(), entry());
        removeLine(index.file(), 2);

        assertEquals(OptionalLong.of(2), index.snapshot().firstBrokenLine());
    }

    @Test
    @DisplayName("a line that does not parse breaks the chain")
    void unparsableEntry() {
        append(entry());
        var lines = new ArrayList<>(lines(index.file()));
        lines.add("not an entry");
        writeLines(index.file(), lines);

        var snapshot = index.snapshot();

        assertAll(
                () -> assertEquals(OptionalLong.of(2), snapshot.firstBrokenLine()),
                () -> assertEquals(AuditCodec.sha256Hex("not an entry".getBytes(StandardCharsets.UTF_8)),
                        snapshot.headHash()));
    }

    @Test
    @DisplayName("an append without the audit lock is refused and writes nothing")
    void appendWithoutLock() {
        var entry = entry();

        try (var transaction = trail.locks.openTransaction()) {
            assertThrows(InternalFaultException.class, () -> index.append(transaction, entry, _ -> {
            }));
        }
        assertEquals(List.of(), lines(index.file()));
    }
}
