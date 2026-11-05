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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.OptionalLong;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The waiver consumption log of a plan (PM-SEC-3, PM-IMPL-2, PM-IMPL-7): one line per evaluation that relied on a
 * waiver, appended through the transaction of the plan and never changed, and read by the id of the waiver. The store
 * file name and the torn and refused JSONL lines are wire contracts; the project, the plans, the waiver ids and the
 * state ids are generated within their grammar.
 */
@EnableGeneratorController
@DisplayName("Waiver consumption log")
class WaiverConsumptionLogTest {

    private static final String PROJECT = "project-" + lower();
    private static final Clock CLOCK = Clock.fixed(Instant.ofEpochSecond(Generators.longs(0, 4_000_000_000L).next()),
            ZoneOffset.UTC);

    @TempDir
    Path base;

    private JournalScope scope;
    private WaiverConsumptionLog log;
    private RecordingLockManager locks;
    private RecordingAuditSink audit;
    private String waiver;
    private String otherWaiver;
    private String absentWaiver;

    @BeforeEach
    void createScope() throws IOException {
        scope = JournalScope.plan(base, PROJECT, "plan-" + lower());
        Files.createDirectories(scope.journalFile().getParent());
        log = new WaiverConsumptionLog(scope);
        locks = new RecordingLockManager();
        audit = new RecordingAuditSink();
        var number = Generators.integers(1, 99_000).next();
        waiver = "W-" + number;
        otherWaiver = "W-" + (number + 1);
        absentWaiver = "W-" + (number + 2);
    }

    private static String lower() {
        return Generators.letterStrings(3, 12).next().toLowerCase(Locale.ROOT);
    }

    private static String stateId() {
        return lower() + "." + lower();
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
        var state = stateId();
        var first = consumption(waiver, state);
        var second = consumption(waiver, state + "-again");
        append(first);
        var before = Files.readAllBytes(log.store());

        append(second);

        var after = Files.readAllBytes(log.store());
        assertAll(
                () -> assertArrayEquals(before, Arrays.copyOf(after, before.length),
                        "the earlier line should stay byte for byte"),
                () -> assertArrayEquals(line(second), Arrays.copyOfRange(after, before.length, after.length),
                        "the second line should be appended after it"),
                () -> assertEquals(List.of(first, second), log.read().records(),
                        "the log should read both consumptions"),
                () -> assertEquals(List.of(first, second), log.read().of(waiver),
                        "the waiver should read both consumptions"));
    }

    @Test
    @DisplayName("is read by the id of a waiver, in the order of the log")
    void byWaiverId() {
        var state = stateId();
        var first = consumption(waiver, state);
        var other = consumption(otherWaiver, state);
        var third = consumption(waiver, stateId());
        append(first);
        append(other);
        append(third);

        var lines = log.read();

        assertAll(
                () -> assertEquals(List.of(first, third), lines.of(waiver),
                        "the waiver should read its lines in order"),
                () -> assertEquals(List.of(other), lines.of(otherWaiver),
                        "the other waiver should read its own line"),
                () -> assertEquals(List.of(), lines.of(absentWaiver),
                        "an absent waiver should read no line"));
    }

    @Test
    @DisplayName("is written only when the caller commits its transaction")
    void writtenAtCommit() {
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(scope.lockKey());
            var write = ScopeTransaction.open(transaction, scope, audit, CLOCK);
            log.append(write, consumption(waiver, stateId()));

            assertFalse(Files.exists(log.store()), "the log should not exist before the commit");

            write.commit();
        }

        assertEquals(1, log.read().records().size(), "the commit should write exactly one line");
    }

    @Test
    @DisplayName("reports a torn tail and a refused line apart from the consumptions it reads (JSONL wire lines)")
    void tornTailAndRefusedLine() throws Exception {
        var first = consumption(waiver, stateId());
        Files.write(log.store(), line(first));
        Files.write(log.store(), ("{\"format_version\":1,\"waiver_id\":\"" + waiver + "\"}\n").getBytes(UTF_8),
                StandardOpenOption.APPEND);
        var size = Files.size(log.store());
        Files.write(log.store(), "{\"format_version\":1,".getBytes(UTF_8), StandardOpenOption.APPEND);

        var lines = log.read();

        assertAll(
                () -> assertEquals(List.of(first), lines.records(), "only the readable line should be read"),
                () -> assertEquals(List.of(2L), lines.refused().stream().map(JsonlStoreFile.Refused::line).toList(),
                        "the second line should be reported refused"),
                () -> assertEquals(OptionalLong.of(size), lines.tornTailOffset(),
                        "the torn tail should start after line two"));
    }

    @Test
    @DisplayName("is refused for a transaction of another scope")
    void otherScope() throws Exception {
        var other = JournalScope.plan(base, PROJECT, "other-" + lower());
        Files.createDirectories(other.journalFile().getParent());
        var consumption = consumption(waiver, stateId());
        try (var transaction = locks.openTransaction()) {
            transaction.acquire(other.lockKey());
            var write = ScopeTransaction.open(transaction, other, audit, CLOCK);

            assertThrows(IllegalArgumentException.class, () -> log.append(write, consumption),
                    "a write of another scope should be refused");
        }
        assertFalse(Files.exists(log.store()), "the refused write should leave no log");
    }

    @Test
    @DisplayName("lies at the path base of a plan; no other scope has one (the file name is the layout contract)")
    void location() {
        var epic = JournalScope.epic(base, PROJECT, "epic-" + lower());
        var workspace = JournalScope.workspace(base, PROJECT);

        assertAll(
                () -> assertEquals(scope.pathBase().resolve("waiver-consumptions.jsonl"), log.store(),
                        "the log should lie at the plan path base"),
                () -> assertEquals("waiver-consumptions.jsonl", log.storePath(),
                        "the store path should be the log file name"),
                () -> assertThrows(IllegalArgumentException.class, () -> new WaiverConsumptionLog(epic),
                        "an epic should have no consumption log"),
                () -> assertThrows(IllegalArgumentException.class, () -> new WaiverConsumptionLog(workspace),
                        "the workspace should have no consumption log"));
    }
}
