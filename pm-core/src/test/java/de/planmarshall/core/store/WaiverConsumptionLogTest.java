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

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The waiver consumption log of a plan (PM-SEC-3, PM-IMPL-2, PM-IMPL-7): one line per evaluation that relied on a
 * waiver, appended through the transaction of the plan and never changed, and read by the id of the waiver.
 */
@DisplayName("Waiver consumption log")
class WaiverConsumptionLogTest {

    private static final String PROJECT = "pm-mcp-core";
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-10-17T09:00:00Z"), ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private WaiverConsumptionLog log;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, "a-plan");
        Files.createDirectories(scope.journalFile().getParent());
        log = new WaiverConsumptionLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
    }

    /** Appends a line in a transaction of its own and commits it. */
    private void append(WaiverConsumptionRecord consumption) {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            log.append(write, consumption);
            write.commit();
        }
    }

    private static WaiverConsumptionRecord consumption(String waiverId, String stateId) {
        var template = WaiverConsumptionCodecTest.consumption();
        return new WaiverConsumptionRecord(waiverId, template.gate(), template.at(), stateId, template.headSha(),
                template.covered());
    }

    private static byte[] line(WaiverConsumptionRecord consumption) {
        return (new String(WaiverConsumptionCodec.write(consumption), UTF_8) + "\n").getBytes(UTF_8);
    }

    @Test
    @DisplayName("two consumptions of one waiver are two lines, and every earlier line stays byte for byte")
    void appendOnly() throws Exception {
        var first = consumption("W-1", "finalize.ci-verify");
        var second = consumption("W-1", "finalize.ci-verify-again");
        append(first);
        var before = Files.readAllBytes(log.store());

        append(second);

        var after = Files.readAllBytes(log.store());
        assertAll(
                () -> assertArrayEquals(before, Arrays.copyOf(after, before.length)),
                () -> assertArrayEquals(line(second), Arrays.copyOfRange(after, before.length, after.length)),
                () -> assertEquals(List.of(first, second), log.read().records()),
                () -> assertEquals(List.of(first, second), log.read().of("W-1")));
    }

    @Test
    @DisplayName("is read by the id of a waiver, in the order of the log")
    void byWaiverId() {
        var first = consumption("W-1", "finalize.ci-verify");
        var other = consumption("W-2", "finalize.ci-verify");
        var third = consumption("W-1", "finalize.pre-push");
        append(first);
        append(other);
        append(third);

        var lines = log.read();

        assertAll(
                () -> assertEquals(List.of(first, third), lines.of("W-1")),
                () -> assertEquals(List.of(other), lines.of("W-2")),
                () -> assertEquals(List.of(), lines.of("W-3")));
    }

    @Test
    @DisplayName("is written only when the caller commits its transaction")
    void writtenAtCommit() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            log.append(write, consumption("W-1", "finalize.ci-verify"));

            assertFalse(Files.exists(log.store()));

            write.commit();
        }

        assertEquals(1, log.read().records().size());
    }

    @Test
    @DisplayName("reports a torn tail and a refused line apart from the consumptions it reads")
    void tornTailAndRefusedLine() throws Exception {
        var first = consumption("W-1", "finalize.ci-verify");
        Files.write(log.store(), line(first));
        Files.write(log.store(), "{\"format_version\":1,\"waiver_id\":\"W-1\"}\n".getBytes(UTF_8),
                StandardOpenOption.APPEND);
        var size = Files.size(log.store());
        Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

        var lines = log.read();

        assertAll(
                () -> assertEquals(List.of(first), lines.records()),
                () -> assertEquals(List.of(2L), lines.refused().stream().map(JsonlStoreFile.Refused::line).toList()),
                () -> assertEquals(OptionalLong.of(size), lines.tornTailOffset()));
    }

    @Test
    @DisplayName("is refused for a transaction of another scope")
    void otherScope() throws Exception {
        var other = JournalScope.plan(base, PROJECT, "other-plan");
        Files.createDirectories(other.journalFile().getParent());
        var consumption = consumption("W-1", "finalize.ci-verify");
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(other.lockKey());
            var write = ScopeTransaction.open(transaction, other, audit, CLOCK);

            assertThrows(IllegalArgumentException.class, () -> log.append(write, consumption));
        }
        assertFalse(Files.exists(log.store()));
    }

    @Test
    @DisplayName("lies at the path base of a plan; no other scope has one")
    void location() {
        assertAll(
                () -> assertEquals(scope.pathBase().resolve("waiver-consumptions.jsonl"), log.store()),
                () -> assertEquals("waiver-consumptions.jsonl", log.storePath()),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WaiverConsumptionLog(JournalScope.epic(base, PROJECT, "an-epic"))),
                () -> assertThrows(IllegalArgumentException.class,
                        () -> new WaiverConsumptionLog(JournalScope.workspace(base, PROJECT))));
    }
}
