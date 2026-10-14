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

import static de.planmarshall.runtime.audit.AuditTrailFixture.USER;
import static de.planmarshall.runtime.audit.AuditTrailFixture.hashOf;
import static de.planmarshall.runtime.audit.AuditTrailFixture.lines;
import static de.planmarshall.runtime.audit.AuditTrailFixture.recordAt;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import javax.crypto.SecretKey;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.AuditAnchor;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.AuditChainVerification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The rotation of the audit log: checkpoint, index entry and rename under the audit lock, and the first record of the
 * new log chained to the checkpoint line.
 */
@EnableTestLogger
@DisplayName("The audit rotation")
class AuditRotationTest {

    private static final Duration MONTH = Duration.ofDays(30);

    /** Limits a log reaches with its first record, so the next append rotates it. */
    private static final AuditRotation.Limits BY_SIZE = new AuditRotation.Limits(1, MONTH);

    /** Limits only the age reaches. */
    private static final AuditRotation.Limits BY_AGE = new AuditRotation.Limits(Long.MAX_VALUE, Duration.ofDays(1));

    /** Limits never reached in a test. */
    private static final AuditRotation.Limits NEVER = new AuditRotation.Limits(Long.MAX_VALUE, Duration.ofDays(3650));

    @TempDir
    Path base;

    @Nested
    @DisplayName("limits")
    class Limits {

        @Test
        @DisplayName("default to 64 MiB and 30 days")
        void defaults() {
            assertAll(
                    () -> assertEquals(64L * 1024 * 1024, AuditRotation.Limits.DEFAULTS.maxBytes()),
                    () -> assertEquals(MONTH, AuditRotation.Limits.DEFAULTS.maxAge()));
        }

        @Test
        @DisplayName("refuse a limit that is not positive")
        void refuseNonPositive() {
            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new AuditRotation.Limits(0, MONTH)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new AuditRotation.Limits(1, Duration.ZERO)),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new AuditRotation.Limits(1, Duration.ofDays(-1))));
        }
    }

    @Test
    @DisplayName("by size: checkpoint, index entry, rename, and the new log chained to the checkpoint line")
    void rotatesBySize() {
        var trail = new AuditTrailFixture(base, BY_SIZE);
        var key = trail.createKey();
        trail.append(1);
        var first = recordAt(trail.logFile(), 1);
        var firstHash = hashOf(trail.logFile(), 1);

        trail.append(1);

        var rotated = trail.rotatedFile(first.eventId());
        var checkpoint = recordAt(rotated, 2);
        var checkpointHash = hashOf(rotated, 2);
        var entries = trail.head.index().snapshot().entries();
        var next = recordAt(trail.logFile(), 1);
        assertAll(
                () -> assertEquals(List.of(first), List.of(recordAt(rotated, 1))),
                () -> assertEquals(AuditRecord.EVENT_CHECKPOINT, checkpoint.event()),
                () -> assertEquals(AuditRecord.Category.SECURITY, checkpoint.category()),
                () -> assertEquals(AuditRecord.Scope.MACHINE, checkpoint.scopeType()),
                () -> assertEquals(USER, checkpoint.actor()),
                () -> assertEquals(Map.of(AuditRotation.DETAIL_RECORD_COUNT, 1L, AuditRotation.DETAIL_HEAD_HASH,
                                firstHash, AuditRotation.DETAIL_HMAC, AuditRotation.checkpointHmac(key, 1, firstHash)),
                        checkpoint.details()),
                () -> assertEquals(firstHash, checkpoint.prevHash()),
                () -> assertEquals(List.of(new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        "audit-" + first.eventId() + ".jsonl", first.eventId(), checkpoint.eventId(), 2,
                        checkpointHash)), entries),
                () -> assertEquals(1, lines(trail.logFile()).size()),
                () -> assertEquals(checkpointHash, next.prevHash()),
                () -> assertNotEquals(AuditCodec.GENESIS_HASH, next.prevHash()),
                () -> assertEquals(1, trail.readHead().orElseThrow().recordCount()),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.AUDIT_LOG_ROTATED.resolveIdentifierString()),
                () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
    }

    @Test
    @DisplayName("a torn tail of the index stays owed during the rotation and is handed into the new log")
    void tornIndexHandedIntoNewLog() throws Exception {
        var trail = new AuditTrailFixture(base, BY_SIZE);
        trail.createKey();
        trail.append(1);
        var first = recordAt(trail.logFile(), 1);
        Files.writeString(trail.indexFile(), "{\"format_version\":1,\"prev_hash\":\"00");
        var indexIntent = new JsonlStoreFile(trail.indexFile(), LockKey.auditLog(base)).auditIntentFile();

        trail.append(1);

        var rotated = trail.rotatedFile(first.eventId());
        var handedOver = recordAt(trail.logFile(), 1);
        assertAll(
                () -> assertEquals(2, lines(rotated).size()),
                () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                () -> assertEquals(2, lines(trail.logFile()).size()),
                () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, handedOver.event()),
                () -> assertEquals(trail.indexFile().toString(), handedOver.details().get(AuditEvent.DETAIL_STORE)),
                () -> assertEquals(hashOf(rotated, 2), handedOver.prevHash()),
                () -> assertEquals(AuditTrailFixture.EVENT, recordAt(trail.logFile(), 2).event()),
                () -> assertFalse(Files.exists(indexIntent), "the owed record was handed over"),
                () -> assertEquals(1, checkpoints(rotated) + checkpoints(trail.logFile())),
                () -> assertEquals(2, trail.readHead().orElseThrow().recordCount()),
                () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
    }

    @Test
    @DisplayName("by age: the log is rotated once its first record is older than the maximum age")
    void rotatesByAge() {
        var trail = new AuditTrailFixture(base, BY_AGE);
        trail.createKey();
        trail.append(1);
        var first = recordAt(trail.logFile(), 1);
        trail.clock.advance(Duration.ofHours(12));
        trail.append(1);
        var notYet = Files.exists(trail.indexFile());

        trail.clock.advance(Duration.ofHours(12));
        trail.append(1);

        assertAll(
                () -> assertFalse(notYet, "rotated before the maximum age"),
                () -> assertEquals(3, lines(trail.rotatedFile(first.eventId())).size()),
                () -> assertEquals(1, lines(trail.logFile()).size()),
                () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify()));
    }

    @Test
    @DisplayName("an empty log and a log below the limits are not rotated")
    void notDue() {
        var trail = new AuditTrailFixture(base, NEVER);
        var key = trail.createKey();
        var rotation = new AuditRotation(trail.head.index(), NEVER, trail.clock, USER);

        trail.underAuditLock(transaction -> assertFalse(rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));
        trail.append(2);
        trail.underAuditLock(transaction -> assertFalse(rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));

        assertAll(
                () -> assertEquals(NEVER, rotation.limits()),
                () -> assertFalse(Files.exists(trail.indexFile())),
                () -> assertEquals(2, lines(trail.logFile()).size()));
    }

    @Test
    @DisplayName("a log whose first line does not parse is not rotated")
    void unparsableFirstLineNotRotated() {
        var trail = new AuditTrailFixture(base, NEVER);
        var key = trail.createKey();
        trail.append(2);
        AuditTrailFixture.editLine(trail.logFile(), 1, _ -> "not a record");
        var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);

        trail.underAuditLock(transaction -> assertFalse(rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));

        assertFalse(Files.exists(trail.indexFile()));
    }

    @Nested
    @DisplayName("a due rotation over a tampered trail")
    class Refused {

        @Test
        @DisplayName("a cut that coincides with a due rotation signs no checkpoint and is reported by verify and full")
        void cutLogNotClosed() {
            var trail = new AuditTrailFixture(base, BY_AGE);
            trail.createKey();
            trail.append(4);
            var anchored = trail.readHead().orElseThrow();
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            AuditTrailFixture.removeLine(trail.logFile(), 3);
            trail.clock.advance(Duration.ofDays(2));

            trail.append(1);

            assertAll(
                    () -> assertFalse(Files.exists(trail.indexFile()), "no checkpoint closed the cut log"),
                    () -> assertEquals(0, checkpoints(trail.logFile())),
                    () -> assertEquals(3, lines(trail.logFile()).size()),
                    () -> assertEquals(anchored, trail.readHead().orElseThrow()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.format(AuditHead.ANCHORED_LINE_MISSING)),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED,
                            reason(trail.verification().verify())),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED,
                            reason(trail.verification().full())));
        }

        @Test
        @DisplayName("a cut log stays unrotated at every later due append, so no recount accepts it")
        void cutLogNeverClosedLater() {
            var trail = new AuditTrailFixture(base, BY_AGE);
            trail.createKey();
            trail.append(3);
            AuditTrailFixture.removeLine(trail.logFile(), 3);
            trail.clock.advance(Duration.ofDays(2));

            trail.append(3);

            assertAll(
                    () -> assertFalse(Files.exists(trail.indexFile())),
                    () -> assertEquals(5, lines(trail.logFile()).size()),
                    () -> assertEquals(3, trail.readHead().orElseThrow().recordCount()),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().full())));
        }

        @Test
        @DisplayName("a damaged head over a cut log refuses the due rotation and stays in place")
        void damagedHeadNotClosed() throws Exception {
            var trail = new AuditTrailFixture(base, BY_AGE);
            trail.createKey();
            trail.append(4);
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            Files.writeString(trail.headFile(), "not a head");
            trail.clock.advance(Duration.ofDays(2));

            trail.append(1);

            assertAll(
                    () -> assertFalse(Files.exists(trail.indexFile()), "no checkpoint closed the cut log"),
                    () -> assertEquals("not a head", Files.readString(trail.headFile())),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.format(AuditHead.HEAD_UNVERIFIABLE)),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().verify())),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().full())));
        }

        @Test
        @DisplayName("a due log without any head is closed: nothing is anchored yet")
        void absentHeadClosed() throws Exception {
            var trail = new AuditTrailFixture(base, BY_AGE);
            var key = trail.createKey();
            trail.append(2);
            Files.delete(trail.headFile());
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);

            trail.underAuditLock(transaction -> assertTrue(
                    rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));

            assertEquals(1, trail.head.index().snapshot().entries().size());
        }

        @Test
        @DisplayName("a head that anchors an index line the last entry does not chain to refuses the rotation")
        void foreignIndexAnchorNotClosed() throws Exception {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var claimed = AuditHead.sign(key, trail.readHead().orElseThrow().withIndexHeadHash(hashOf(
                    trail.logFile(), 1)));
            Files.write(trail.headFile(), AuditCodec.writeHead(claimed));
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);

            trail.underAuditLock(transaction -> assertFalse(
                    rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));

            assertFalse(Files.exists(trail.indexFile()));
        }

        @Test
        @DisplayName("a head stopped before the rotation refuses the next one until the next append rewrote it")
        void headBeforeRotationDefersTheNext() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var beforeRotation = Files.readAllBytes(trail.headFile());
            trail.append(1);
            Files.write(trail.headFile(), beforeRotation);

            trail.append(1);
            var deferred = trail.head.index().snapshot().entries().size();
            trail.append(1);

            assertAll(
                    () -> assertEquals(1, deferred, "the rotation waited for the head of the current log"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.format(AuditHead.ANCHORED_LINE_MISSING)),
                    () -> assertEquals(2, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(1, trail.readHead().orElseThrow().recordCount()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
        }

        private static AuditChainVerification.BreakReason reason(AuditChainVerification.Outcome outcome) {
            return assertInstanceOf(AuditChainVerification.Outcome.Broken.class, outcome).reason();
        }
    }

    @Nested
    @DisplayName("a stopped rotation")
    class Stopped {

        @Test
        @DisplayName("stopped after the rename: the next append still chains to the checkpoint line")
        void afterRename() {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var first = recordAt(trail.logFile(), 1);
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);
            trail.underAuditLock(transaction -> assertTrue(rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));
            var logAfterStop = Files.exists(trail.logFile());
            var checkpointHash = hashOf(trail.rotatedFile(first.eventId()), 3);

            trail.append(1);

            assertAll(
                    () -> assertFalse(logAfterStop, "the log was renamed"),
                    () -> assertEquals(checkpointHash, recordAt(trail.logFile(), 1).prevHash()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
        }

        @Test
        @DisplayName("stopped after the checkpoint: the next append completes it with one checkpoint and one entry")
        void afterCheckpoint() {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var first = recordAt(trail.logFile(), 1);
            var headHash = hashOf(trail.logFile(), 2);
            trail.underAuditLock(transaction -> trail.log.appendCheckpoint(transaction,
                    checkpoint(trail, key, 2, headHash)));
            var pending = trail.verification().full();

            trail.append(1);

            var rotated = trail.rotatedFile(first.eventId());
            assertAll(
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), pending),
                    () -> assertEquals(3, lines(rotated).size()),
                    () -> assertEquals(1, checkpoints(rotated) + checkpoints(trail.logFile())),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(hashOf(rotated, 3), recordAt(trail.logFile(), 1).prevHash()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
        }

        @Test
        @DisplayName("stopped after the index entry: the next append only renames the log, adding no second entry")
        void afterIndexEntry() {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var first = recordAt(trail.logFile(), 1);
            var headHash = hashOf(trail.logFile(), 2);
            trail.underAuditLock(transaction -> {
                var checkpoint = checkpoint(trail, key, 2, headHash);
                var checkpointHash = trail.log.appendCheckpoint(transaction, checkpoint);
                trail.head.index().append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        AuditIndexEntry.rotatedFileName(first.eventId()), first.eventId(), checkpoint.eventId(), 3,
                        checkpointHash), _ -> {
                });
            });
            var pending = trail.verification().full();

            trail.append(1);

            assertAll(
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), pending),
                    () -> assertEquals(3, lines(trail.rotatedFile(first.eventId())).size()),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(1, lines(trail.logFile()).size()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
        }
    }

    @Nested
    @DisplayName("a failed rotation")
    class Failed {

        @Test
        @DisplayName("a failed rename refuses the record, so none follows the checkpoint, and verifies intact")
        void failedRenameRefusesTheRecord() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var first = recordAt(trail.logFile(), 1);
            var blocker = blockRename(trail, first);

            assertThrows(UncheckedIOException.class, () -> trail.append(1));
            var verified = trail.verification().verify();
            unblock(blocker);

            assertAll(
                    () -> assertEquals(2, lines(trail.logFile()).size()),
                    () -> assertTrue(recordAt(trail.logFile(), 2).isCheckpoint()),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.resolveIdentifierString()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), verified),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
        }

        @Test
        @DisplayName("a rename that fails once is completed by the next append with exactly one index entry")
        void failedRenameCompletedByTheNextAppend() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var first = recordAt(trail.logFile(), 1);
            var blocker = blockRename(trail, first);
            assertThrows(UncheckedIOException.class, () -> trail.append(1));
            unblock(blocker);

            trail.append(1);

            var rotated = trail.rotatedFile(first.eventId());
            assertAll(
                    () -> assertEquals(2, lines(rotated).size()),
                    () -> assertEquals(1, checkpoints(rotated)),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(1, lines(trail.logFile()).size()),
                    () -> assertEquals(hashOf(rotated, 2), recordAt(trail.logFile(), 1).prevHash()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full()));
        }

        @Test
        @DisplayName("a pending rotation is completed without the key, and no record follows its checkpoint")
        void pendingCompletedWithoutKey() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var first = recordAt(trail.logFile(), 1);
            var blocker = blockRename(trail, first);
            assertThrows(UncheckedIOException.class, () -> trail.append(1));
            unblock(blocker);
            var withoutKey = new AuditTrailFixture(base, BY_SIZE);

            withoutKey.append(1);

            var rotated = trail.rotatedFile(first.eventId());
            assertAll(
                    () -> assertEquals(2, lines(rotated).size()),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(1, lines(trail.logFile()).size()),
                    () -> assertEquals(hashOf(rotated, 2), recordAt(trail.logFile(), 1).prevHash()));
        }

        @Test
        @DisplayName("a due log the index already names is never entered a second time")
        void enteredLogNotEnteredTwice() {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var first = recordAt(trail.logFile(), 1);
            var headHash = hashOf(trail.logFile(), 2);
            var chainOnly = new AuditLog(base, AuditAnchor.NONE, trail.clock, USER);
            trail.underAuditLock(transaction -> {
                var checkpoint = checkpoint(trail, key, 2, headHash);
                var checkpointHash = trail.log.appendCheckpoint(transaction, checkpoint);
                trail.head.index().append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        AuditIndexEntry.rotatedFileName(first.eventId()), first.eventId(), checkpoint.eventId(), 3,
                        checkpointHash), _ -> {
                });
                chainOnly.append(transaction, checkpoint(trail, key, 2, headHash).toBuilder()
                        .event(AuditTrailFixture.EVENT).details(Map.of()).build());
            });
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);
            var due = new AuditTrailFixture(base, BY_SIZE);
            due.keyring.entries.putAll(trail.keyring.entries);

            trail.underAuditLock(transaction -> assertThrows(UncheckedIOException.class,
                    () -> rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));
            due.append(1);

            assertAll(
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(5, lines(trail.logFile()).size()),
                    () -> assertFalse(Files.exists(trail.rotatedFile(first.eventId()))),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.resolveIdentifierString()));
        }

        /** Puts a non-empty directory where the log is renamed to, so the atomic move fails. */
        private Path blockRename(AuditTrailFixture trail, AuditRecord first) throws IOException {
            var blocker = trail.rotatedFile(first.eventId());
            Files.createDirectories(blocker);
            Files.writeString(blocker.resolve("blocker"), "blocks the rename");
            return blocker;
        }

        private void unblock(Path blocker) throws IOException {
            Files.delete(blocker.resolve("blocker"));
            Files.delete(blocker);
        }
    }

    private static AuditRecord checkpoint(AuditTrailFixture trail, SecretKey key, long recordCount, String headHash) {
        return AuditRecord.builder()
                .eventId(TransactionId.generate(trail.clock).value())
                .prevHash(AuditCodec.GENESIS_HASH)
                .timestamp(trail.clock.instant())
                .category(AuditRecord.Category.SECURITY)
                .event(AuditRecord.EVENT_CHECKPOINT)
                .scopeType(AuditRecord.Scope.MACHINE)
                .actor(USER)
                .outcome(AuditRecord.Outcome.SUCCEEDED)
                .details(Map.of(AuditRotation.DETAIL_RECORD_COUNT, recordCount, AuditRotation.DETAIL_HEAD_HASH,
                        headHash, AuditRotation.DETAIL_HMAC, AuditRotation.checkpointHmac(key, recordCount, headHash)))
                .build();
    }

    private static long checkpoints(Path file) {
        var count = 0L;
        for (var number = 1; number <= lines(file).size(); number++) {
            if (recordAt(file, number).isCheckpoint()) {
                count++;
            }
        }
        return count;
    }
}
