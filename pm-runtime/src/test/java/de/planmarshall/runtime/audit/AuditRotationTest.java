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
import static de.planmarshall.runtime.audit.AuditTrailFixture.foreignKey;
import static de.planmarshall.runtime.audit.AuditTrailFixture.hashOf;
import static de.planmarshall.runtime.audit.AuditTrailFixture.lines;
import static de.planmarshall.runtime.audit.AuditTrailFixture.recordAt;
import static de.planmarshall.runtime.audit.AuditTrailFixture.words;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.OptionalLong;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.parallel.ResourceLock;
import org.junit.jupiter.api.parallel.Resources;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.store.AuditAnchor;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.AuditChainVerification;

/**
 * The rotation of the audit log: checkpoint, index entry and rename under the audit lock, and the first record of the
 * new log chained to the checkpoint line.
 *
 * <p>
 * The foreign key, the checkpoint's key, head hash and record count, a negative age, the damaged head, unparsable lines,
 * blocker texts and the keyring's failure message are generated. The literals that stay are the contract: the default
 * and boundary limits of the rotation and the clock steps around them, the locales of the HMAC test, the signed
 * content of a checkpoint, the record counts and line numbers the appends produce, the torn line of the store's wire
 * format, and the head, rotation and break reason codes.
 */
@EnableGeneratorController
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

    /** A torn line, as a stop while a line of the store's wire format was written leaves it. */
    private static final String TORN = "{\"format_version\":1,\"prev_hash\":\"00";

    @TempDir
    Path base;

    @Nested
    @DisplayName("limits")
    class Limits {

        @Test
        @DisplayName("default to 64 MiB and 30 days, the specified limits")
        void defaults() {
            assertAll(
                    () -> assertEquals(64L * 1024 * 1024, AuditRotation.Limits.DEFAULTS.maxBytes(),
                            "the default size limit is 64 MiB"),
                    () -> assertEquals(MONTH, AuditRotation.Limits.DEFAULTS.maxAge(),
                            "the default age limit is 30 days"));
        }

        @Test
        @DisplayName("refuse a limit that is not positive")
        void refuseNonPositive() {
            var negative = Duration.ofDays(-Generators.integers(1, 3650).next());

            assertAll(
                    () -> assertThrows(IllegalArgumentException.class, () -> new AuditRotation.Limits(0, MONTH),
                            "a size limit of zero is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new AuditRotation.Limits(1, Duration.ZERO), "an age limit of zero is refused"),
                    () -> assertThrows(IllegalArgumentException.class,
                            () -> new AuditRotation.Limits(1, negative), "a negative age limit is refused"));
        }
    }

    @Nested
    @DisplayName("the checkpoint HMAC")
    @ResourceLock(Resources.LOCALE)
    class CheckpointHmac {

        /** A locale whose default number format writes non-ASCII digits. */
        private static final Locale ARABIC_DIGITS = Locale.forLanguageTag("ar-EG");

        private final Locale formatLocale = Locale.getDefault(Locale.Category.FORMAT);

        @AfterEach
        void restoreLocale() {
            Locale.setDefault(Locale.Category.FORMAT, formatLocale);
        }

        @Test
        @DisplayName("the non-ASCII-digit locale of the test does localize the digits of a formatted number")
        void localeWritesNonAsciiDigits() {
            long number = Generators.longs(1, Long.MAX_VALUE).next();
            Locale.setDefault(Locale.Category.FORMAT, ARABIC_DIGITS);

            var formatted = "%d".formatted(number);

            assertNotEquals(Long.toString(number), formatted, "the locale must localize the formatted digits");
        }

        @ParameterizedTest(name = "{0}")
        @ValueSource(strings = {"en-US", "de-DE", "ar-EG", "th-TH-u-nu-thai"})
        @DisplayName("signs the record count with ASCII digits under any default locale")
        void asciiDigitsUnderAnyLocale(String languageTag) {
            var key = foreignKey();
            var headHash = AuditCodec.sha256Hex(words().getBytes(StandardCharsets.UTF_8));
            long recordCount = Generators.longs(1, Long.MAX_VALUE).next();
            Locale.setDefault(Locale.Category.FORMAT, Locale.forLanguageTag(languageTag));
            // the signed content of a checkpoint is the contract of the HMAC
            var content = "{\"record_count\":" + Long.toString(recordCount) + ",\"head_hash\":\"" + headHash + "\"}";

            var hmac = AuditRotation.checkpointHmac(key, recordCount, headHash);

            assertEquals(EnrolmentHmac.hmac(key, content.getBytes(StandardCharsets.US_ASCII)), hmac,
                    "the HMAC signs the count in ASCII digits");
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
                () -> assertEquals(List.of(first), List.of(recordAt(rotated, 1)),
                        "the rotated log keeps the first record"),
                () -> assertEquals(AuditRecord.EVENT_CHECKPOINT, checkpoint.event(),
                        "the rotated log ends with a checkpoint"),
                () -> assertEquals(AuditRecord.Category.SECURITY, checkpoint.category(),
                        "the checkpoint is a security record"),
                () -> assertEquals(AuditRecord.Scope.MACHINE, checkpoint.scopeType(),
                        "the checkpoint has the machine scope"),
                () -> assertEquals(USER, checkpoint.actor(), "the checkpoint names the OS principal"),
                () -> assertEquals(Map.of(AuditRotation.DETAIL_RECORD_COUNT, 1L, AuditRotation.DETAIL_HEAD_HASH,
                                firstHash, AuditRotation.DETAIL_HMAC, AuditRotation.checkpointHmac(key, 1, firstHash)),
                        checkpoint.details(), "the checkpoint signs the count and head"),
                () -> assertEquals(firstHash, checkpoint.prevHash(), "the checkpoint chains to the last record"),
                () -> assertEquals(List.of(new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        "audit-" + first.eventId() + ".jsonl", first.eventId(), checkpoint.eventId(), 2,
                        checkpointHash)), entries, "the index enters the rotated log"),
                () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                () -> assertEquals(checkpointHash, next.prevHash(), "the new log chains to the checkpoint"),
                () -> assertNotEquals(AuditCodec.GENESIS_HASH, next.prevHash(),
                        "the new log does not restart at genesis"),
                () -> assertEquals(1, trail.readHead().orElseThrow().recordCount(),
                        "the head counts the records of the new log"),
                () -> LogAsserts.assertSingleLogMessagePresentContaining(TestLogLevel.INFO,
                        INFO.AUDIT_LOG_ROTATED.resolveIdentifierString()),
                () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                        "the rotated trail verifies intact"));
    }

    @Test
    @DisplayName("a torn tail of the index stays owed during the rotation and is handed into the new log")
    void tornIndexHandedIntoNewLog() throws Exception {
        var trail = new AuditTrailFixture(base, BY_SIZE);
        trail.createKey();
        trail.append(1);
        var first = recordAt(trail.logFile(), 1);
        Files.writeString(trail.indexFile(), TORN);
        var indexIntent = new JsonlStoreFile(trail.indexFile(), LockKey.auditLog(base)).auditIntentFile();

        trail.append(1);

        var rotated = trail.rotatedFile(first.eventId());
        var handedOver = recordAt(trail.logFile(), 1);
        assertAll(
                () -> assertEquals(2, lines(rotated).size(), "the rotated log holds record and checkpoint"),
                () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                        "the index enters the rotated log once"),
                () -> assertEquals(2, lines(trail.logFile()).size(), "the new log holds repair and record"),
                () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, handedOver.event(),
                        "the repair is handed into the new log"),
                () -> assertEquals(trail.indexFile().toString(), handedOver.details().get(AuditEvent.DETAIL_STORE),
                        "the repair names the index as its store"),
                () -> assertEquals(hashOf(rotated, 2), handedOver.prevHash(),
                        "the repair chains to the checkpoint"),
                () -> assertEquals(trail.event, recordAt(trail.logFile(), 2).event(),
                        "the record follows the handed-over repair"),
                () -> assertFalse(Files.exists(indexIntent), "the owed record was handed over"),
                () -> assertEquals(1, checkpoints(rotated) + checkpoints(trail.logFile()),
                        "exactly one checkpoint was written"),
                () -> assertEquals(2, trail.readHead().orElseThrow().recordCount(),
                        "the head counts the records of the new log"),
                () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                        "the rotated trail verifies intact"));
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
                () -> assertEquals(3, lines(trail.rotatedFile(first.eventId())).size(),
                        "the rotated log holds two records and checkpoint"),
                () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                        "the rotated trail verifies intact"));
    }

    @Test
    @DisplayName("an empty log and a log below the limits are not rotated")
    void notDue() {
        var trail = new AuditTrailFixture(base, NEVER);
        var key = trail.createKey();
        var rotation = new AuditRotation(trail.head.index(), NEVER, trail.clock, USER);

        trail.underAuditLock(transaction -> assertFalse(rotation.rotateIfDue(transaction, trail.log, key,
                trail.head.closeCheck(key)), "an empty log is not rotated"));
        trail.append(2);
        trail.underAuditLock(transaction -> assertFalse(rotation.rotateIfDue(transaction, trail.log, key,
                trail.head.closeCheck(key)), "a log below the limits is not rotated"));

        assertAll(
                () -> assertEquals(NEVER, rotation.limits(), "the rotation keeps its limits"),
                () -> assertFalse(Files.exists(trail.indexFile()), "no index entry is written"),
                () -> assertEquals(2, lines(trail.logFile()).size(), "the log keeps its two records"));
    }

    @Test
    @DisplayName("a log whose first line does not parse is not rotated")
    void unparsableFirstLineNotRotated() {
        var trail = new AuditTrailFixture(base, NEVER);
        var key = trail.createKey();
        trail.append(2);
        var unparsable = words();
        AuditTrailFixture.editLine(trail.logFile(), 1, _ -> unparsable);
        var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);

        trail.underAuditLock(transaction -> assertFalse(rotation.rotateIfDue(transaction, trail.log, key,
                trail.head.closeCheck(key)), "an unparsable first line is not rotated"));

        assertFalse(Files.exists(trail.indexFile()), "no index entry is written");
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
                    () -> assertEquals(0, checkpoints(trail.logFile()), "no checkpoint is written to the log"),
                    () -> assertEquals(3, lines(trail.logFile()).size(), "the append stands on the cut log"),
                    () -> assertEquals(anchored, trail.readHead().orElseThrow(), "the anchored head is kept"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.format(AuditHead.ANCHORED_LINE_MISSING)),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED,
                            reason(trail.verification().verify()), "verify reports the cut as truncated"),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED,
                            reason(trail.verification().full()), "full reports the cut as truncated"));
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
                    () -> assertFalse(Files.exists(trail.indexFile()), "no checkpoint closed the cut log"),
                    () -> assertEquals(5, lines(trail.logFile()).size(), "every append stands on the cut log"),
                    () -> assertEquals(3, trail.readHead().orElseThrow().recordCount(),
                            "the anchored head is kept"),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().full()), "full reports a head mismatch"));
        }

        @Test
        @DisplayName("a damaged head over a cut log refuses the due rotation and stays in place")
        void damagedHeadNotClosed() throws Exception {
            var trail = new AuditTrailFixture(base, BY_AGE);
            trail.createKey();
            trail.append(4);
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            var damaged = words();
            Files.writeString(trail.headFile(), damaged);
            trail.clock.advance(Duration.ofDays(2));

            trail.append(1);

            assertAll(
                    () -> assertFalse(Files.exists(trail.indexFile()), "no checkpoint closed the cut log"),
                    () -> assertEquals(damaged, Files.readString(trail.headFile()), "the damaged head is kept"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.format(AuditHead.HEAD_UNVERIFIABLE)),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().verify()), "verify reports a head mismatch"),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().full()), "full reports a head mismatch"));
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
                    rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key)),
                    "a log without a head is rotated"));

            assertEquals(1, trail.head.index().snapshot().entries().size(), "the index enters the rotated log");
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
                    rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key)),
                    "a foreign index anchor refuses the rotation"));

            assertFalse(Files.exists(trail.indexFile()), "no index entry is written");
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
                    () -> assertEquals(2, trail.head.index().snapshot().entries().size(),
                            "the deferred rotation follows later"),
                    () -> assertEquals(1, trail.readHead().orElseThrow().recordCount(),
                            "the head counts the records of the new log"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the incremental verification is intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the full verification is intact"));
        }

        private static AuditChainVerification.BreakReason reason(AuditChainVerification.Outcome outcome) {
            return assertInstanceOf(AuditChainVerification.Outcome.Broken.class, outcome,
                    "the verification reports a break").reason();
        }
    }

    @Nested
    @DisplayName("a stopped rotation")
    class Stopped {

        @Test
        @DisplayName("stopped after the rename: verified intact, and the next append chains to the checkpoint line "
                + "and writes the head over the new log")
        void afterRename() {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var first = recordAt(trail.logFile(), 1);
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);
            trail.underAuditLock(transaction -> assertTrue(rotation.rotateIfDue(transaction, trail.log, key,
                    trail.head.closeCheck(key)), "the due rotation is carried out"));
            var logAfterStop = Files.exists(trail.logFile());
            var headAfterStop = trail.readHead().orElseThrow();
            var stoppedVerify = trail.verification().verify();
            var stoppedFull = trail.verification().full();
            var checkpointHash = hashOf(trail.rotatedFile(first.eventId()), 3);

            trail.append(1);

            var healed = trail.readHead().orElseThrow();
            assertAll(
                    () -> assertFalse(logAfterStop, "the log was renamed by the rotation"),
                    () -> assertEquals(2, headAfterStop.recordCount(), "the stop left the head of the old log"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), stoppedVerify,
                            "the stopped trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), stoppedFull,
                            "the stopped trail verifies intact in full"),
                    () -> assertEquals(checkpointHash, recordAt(trail.logFile(), 1).prevHash(),
                            "the new log chains to the checkpoint"),
                    () -> assertEquals(1, healed.recordCount(), "the healed head counts the new log"),
                    () -> assertEquals(hashOf(trail.logFile(), 1), healed.headHash(),
                            "the healed head anchors the new record"),
                    () -> assertEquals(trail.head.index().snapshot().headHash(), healed.indexHeadHash(),
                            "the healed head anchors the index"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.resolveIdentifierString()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the healed trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the healed trail verifies intact in full"));
        }

        @Test
        @DisplayName("stopped after a rename that the lock left an empty log behind: the next append heals it too")
        void afterRenameWithEmptyLog() throws Exception {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);
            trail.underAuditLock(transaction -> assertTrue(rotation.rotateIfDue(transaction, trail.log, key,
                    trail.head.closeCheck(key)), "the due rotation is carried out"));
            Files.createFile(trail.logFile());
            var stopped = trail.verification().verify();

            trail.append(1);

            assertAll(
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), stopped,
                            "the stopped trail verifies intact"),
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                    () -> assertEquals(1, trail.readHead().orElseThrow().recordCount(),
                            "the healed head counts the new log"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index enters the rotated log once"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the healed trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the healed trail verifies intact in full"));
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
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), pending,
                            "the pending rotation verifies intact"),
                    () -> assertEquals(3, lines(rotated).size(), "the rotated log holds records and checkpoint"),
                    () -> assertEquals(1, checkpoints(rotated) + checkpoints(trail.logFile()),
                            "exactly one checkpoint was written"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index enters the rotated log once"),
                    () -> assertEquals(hashOf(rotated, 3), recordAt(trail.logFile(), 1).prevHash(),
                            "the new log chains to the checkpoint"),
                    () -> assertEquals(1, trail.readHead().orElseThrow().recordCount(),
                            "the head counts the records of the new log"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the completed trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the completed trail verifies intact in full"));
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
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), pending,
                            "the pending rotation verifies intact"),
                    () -> assertEquals(3, lines(trail.rotatedFile(first.eventId())).size(),
                            "the rotated log holds records and checkpoint"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "no second index entry is written"),
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the completed trail verifies intact in full"));
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

            assertThrows(UncheckedIOException.class, () -> trail.append(1), "the blocked rename refuses the record");
            var verified = trail.verification().verify();
            unblock(blocker);

            assertAll(
                    () -> assertEquals(2, lines(trail.logFile()).size(), "the log holds record and checkpoint"),
                    () -> assertTrue(recordAt(trail.logFile(), 2).isCheckpoint(),
                            "the last line is the checkpoint"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index enters the pending rotation"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.resolveIdentifierString()),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), verified,
                            "the pending trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the pending trail verifies intact in full"));
        }

        @Test
        @DisplayName("a rename that fails once is completed by the next append with exactly one index entry")
        void failedRenameCompletedByTheNextAppend() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var first = recordAt(trail.logFile(), 1);
            var blocker = blockRename(trail, first);
            assertThrows(UncheckedIOException.class, () -> trail.append(1), "the blocked rename refuses the record");
            unblock(blocker);

            trail.append(1);

            var rotated = trail.rotatedFile(first.eventId());
            assertAll(
                    () -> assertEquals(2, lines(rotated).size(), "the rotated log holds record and checkpoint"),
                    () -> assertEquals(1, checkpoints(rotated), "the rotated log holds one checkpoint"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index enters the rotated log once"),
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                    () -> assertEquals(hashOf(rotated, 2), recordAt(trail.logFile(), 1).prevHash(),
                            "the new log chains to the checkpoint"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the completed trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the completed trail verifies intact in full"));
        }

        @Test
        @DisplayName("without the key a pending rotation is not completed and no record follows its checkpoint; "
                + "the next append with the key completes it")
        void pendingNotCompletedWithoutKey() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var first = recordAt(trail.logFile(), 1);
            var blocker = blockRename(trail, first);
            assertThrows(UncheckedIOException.class, () -> trail.append(1), "the blocked rename refuses the record");
            unblock(blocker);
            var withoutKey = new AuditTrailFixture(base, BY_SIZE);

            assertThrows(UncheckedIOException.class, () -> withoutKey.append(1),
                    "without the key the record is refused");
            var pendingLines = lines(trail.logFile()).size();
            var renamedWithoutKey = Files.exists(trail.rotatedFile(first.eventId()));
            trail.append(1);

            var rotated = trail.rotatedFile(first.eventId());
            assertAll(
                    () -> assertEquals(2, pendingLines, "no record follows the checkpoint"),
                    () -> assertFalse(renamedWithoutKey, "not completed without the key"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.format(AuditRotation.CHECKPOINT_UNVERIFIABLE)),
                    () -> assertEquals(2, lines(rotated).size(), "the rotated log holds record and checkpoint"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index enters the rotated log once"),
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                    () -> assertEquals(hashOf(rotated, 2), recordAt(trail.logFile(), 1).prevHash(),
                            "the new log chains to the checkpoint"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the completed trail verifies intact"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the completed trail verifies intact in full"));
        }

        @Test
        @DisplayName("with an unreadable key a pending rotation is not completed; once readable it is")
        void pendingNotCompletedWithUnreadableKey() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            trail.createKey();
            trail.append(1);
            var first = recordAt(trail.logFile(), 1);
            var blocker = blockRename(trail, first);
            assertThrows(UncheckedIOException.class, () -> trail.append(1), "the blocked rename refuses the record");
            unblock(blocker);
            var locked = new AuditTrailFixture(base, BY_SIZE);
            locked.keyring.entries.putAll(trail.keyring.entries);
            locked.keyring.failure = new SecretStoreException(SecretStoreException.Reason.LOCKED, words());

            assertThrows(UncheckedIOException.class, () -> locked.append(1),
                    "with a locked key the record is refused");
            var renamedWhileLocked = Files.exists(trail.rotatedFile(first.eventId()));
            locked.keyring.failure = null;
            locked.append(1);

            assertAll(
                    () -> assertFalse(renamedWhileLocked, "not completed while the key cannot be read"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.format(AuditRotation.CHECKPOINT_UNVERIFIABLE)),
                    () -> assertEquals(2, lines(trail.rotatedFile(first.eventId())).size(),
                            "the rotated log holds record and checkpoint"),
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the new log holds the appended record"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().full(),
                            "the completed trail verifies intact in full"));
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
                        .event(trail.event).details(Map.of()).build());
            });
            var rotation = new AuditRotation(trail.head.index(), BY_SIZE, trail.clock, USER);
            var due = new AuditTrailFixture(base, BY_SIZE);
            due.keyring.entries.putAll(trail.keyring.entries);
            var closeCheck = trail.head.closeCheck(key);

            trail.underAuditLock(transaction -> assertThrows(UncheckedIOException.class,
                    () -> rotation.rotateIfDue(transaction, trail.log, key, closeCheck),
                    "an entered log is not entered again"));
            due.append(1);

            assertAll(
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index keeps its single entry"),
                    () -> assertEquals(5, lines(trail.logFile()).size(), "the log is never renamed"),
                    () -> assertFalse(Files.exists(trail.rotatedFile(first.eventId())),
                            "no rotated log is written"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.resolveIdentifierString()));
        }

        /** Puts a non-empty directory where the log is renamed to, so the atomic move fails. */
        private Path blockRename(AuditTrailFixture trail, AuditRecord first) throws IOException {
            var blocker = trail.rotatedFile(first.eventId());
            Files.createDirectories(blocker);
            Files.writeString(blocker.resolve("blocker"), words());
            return blocker;
        }

        private void unblock(Path blocker) throws IOException {
            Files.delete(blocker.resolve("blocker"));
            Files.delete(blocker);
        }
    }

    @Nested
    @DisplayName("a forged checkpoint")
    class Forged {

        @Test
        @DisplayName("appended to a cut log is never completed, and verify and full report the cut")
        void forgedOnCutLogNotCompleted() {
            var trail = new AuditTrailFixture(base, NEVER);
            trail.createKey();
            trail.append(4);
            cutToTwoRecords(trail);
            var headHash = hashOf(trail.logFile(), 2);
            var otherKey = foreignKey();
            trail.underAuditLock(transaction -> trail.log.appendCheckpoint(transaction,
                    checkpoint(trail, otherKey, 2, headHash)));

            assertNeverCompleted(trail);
        }

        @Test
        @DisplayName("replaying a genuine signature over the log before the cut is never completed either")
        void replayedSignatureOnCutLogNotCompleted() {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(4);
            var signedBeforeCut = checkpoint(trail, key, 4, hashOf(trail.logFile(), 4));
            cutToTwoRecords(trail);
            trail.underAuditLock(transaction -> trail.log.appendCheckpoint(transaction, signedBeforeCut));

            assertNeverCompleted(trail);
        }

        @Test
        @DisplayName("entered in the index and renamed by hand is never re-signed by the next head")
        void forgedAndRenamedByHandNotResigned() throws Exception {
            var trail = new AuditTrailFixture(base, NEVER);
            trail.createKey();
            trail.append(4);
            var anchored = trail.readHead().orElseThrow();
            var first = recordAt(trail.logFile(), 1);
            cutToTwoRecords(trail);
            var headHash = hashOf(trail.logFile(), 2);
            var otherKey = foreignKey();
            trail.underAuditLock(transaction -> {
                var forged = checkpoint(trail, otherKey, 2, headHash);
                var forgedHash = trail.log.appendCheckpoint(transaction, forged);
                trail.head.index().append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        AuditIndexEntry.rotatedFileName(first.eventId()), first.eventId(), forged.eventId(), 3,
                        forgedHash), _ -> {
                });
            });
            Files.move(trail.logFile(), trail.rotatedFile(first.eventId()));

            trail.append(1);

            assertAll(
                    () -> assertEquals(anchored, trail.readHead().orElseThrow(), "the head is not re-signed"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.format(AuditHead.ANCHORED_LINE_MISSING)),
                    () -> assertInstanceOf(AuditChainVerification.Outcome.Broken.class,
                            trail.verification().verify(), "verify reports the forged trail broken"),
                    () -> assertInstanceOf(AuditChainVerification.Outcome.Broken.class,
                            trail.verification().full(), "full reports the forged trail broken"));
        }

        private static void cutToTwoRecords(AuditTrailFixture trail) {
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            AuditTrailFixture.removeLine(trail.logFile(), 3);
        }

        /** The append is refused, nothing is written, and both verifications report the checkpoint line. */
        private static void assertNeverCompleted(AuditTrailFixture trail) {
            var before = trail.files();

            assertThrows(UncheckedIOException.class, () -> trail.append(1),
                    "the append over the forged checkpoint fails");

            assertAll(
                    () -> assertEquals(before, trail.files(), "no index entry, no rename, no record, no head"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.format(AuditRotation.CHECKPOINT_UNVERIFIABLE)),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.INFO,
                            INFO.AUDIT_LOG_ROTATED.resolveIdentifierString()),
                    () -> assertLinkBroken(3, trail.verification().verify()),
                    () -> assertLinkBroken(3, trail.verification().full()));
        }

        private static void assertLinkBroken(long line, AuditChainVerification.Outcome outcome) {
            var broken = assertInstanceOf(AuditChainVerification.Outcome.Broken.class, outcome,
                    "the verification reports a break");
            assertAll(
                    () -> assertEquals(AuditChainVerification.BreakReason.LINK_BROKEN, broken.reason(),
                            "the verification reports link_broken"),
                    () -> assertEquals(OptionalLong.of(line), broken.firstBrokenLine(),
                            "the verification names the broken line"));
        }
    }

    @Nested
    @DisplayName("a torn tail behind a checkpoint")
    class TornTailBehindCheckpoint {

        @Test
        @DisplayName("behind a forged checkpoint over a cut log: the repair record follows the checkpoint, the append "
                + "is not refused, and verify and full still report the cut")
        void forgedCheckpointOverCutLog() throws Exception {
            var trail = new AuditTrailFixture(base, NEVER);
            trail.createKey();
            trail.append(4);
            var anchored = trail.readHead().orElseThrow();
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            AuditTrailFixture.removeLine(trail.logFile(), 3);
            var headHash = hashOf(trail.logFile(), 2);
            var otherKey = foreignKey();
            trail.underAuditLock(transaction -> trail.log.appendCheckpoint(transaction,
                    checkpoint(trail, otherKey, 2, headHash)));
            tearTail(trail);

            trail.append(1);

            assertAll(
                    () -> assertEquals(5, lines(trail.logFile()).size(), "the record was appended"),
                    () -> assertTrue(recordAt(trail.logFile(), 3).isCheckpoint(),
                            "the forged checkpoint stays in place"),
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, recordAt(trail.logFile(), 4).event(),
                            "the repair record follows the checkpoint"),
                    () -> assertEquals(hashOf(trail.logFile(), 3), recordAt(trail.logFile(), 4).prevHash(),
                            "the repair chains to the checkpoint"),
                    () -> assertFalse(Files.exists(trail.indexFile()), "the forged checkpoint got no index entry"),
                    () -> assertEquals(anchored, trail.readHead().orElseThrow(), "no head over the cut log"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.format(AuditHead.ANCHORED_LINE_MISSING)),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().verify()), "verify reports a head mismatch"),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH,
                            reason(trail.verification().full()), "full reports a head mismatch"));
        }

        @Test
        @DisplayName("behind a genuine checkpoint already entered in the index after a first rotation: the next start "
                + "reports reset")
        void enteredCheckpointFirstRotation() throws Exception {
            var trail = new AuditTrailFixture(base, NEVER);
            var key = trail.createKey();
            trail.append(2);
            enterCheckpoint(trail, key);
            tearTail(trail);

            trail.append(1);

            assertAll(
                    () -> assertEquals(5, lines(trail.logFile()).size(), "the record was appended"),
                    () -> assertEquals(AuditEvent.JSONL_TAIL_REPAIRED, recordAt(trail.logFile(), 4).event(),
                            "the repair record follows the checkpoint"),
                    () -> assertEquals(1, trail.head.index().snapshot().entries().size(),
                            "the index keeps its single entry"),
                    () -> assertEquals(5, trail.readHead().orElseThrow().recordCount(),
                            "the head counts every line of the log"),
                    () -> assertEquals(AuditChainVerification.BreakReason.RESET,
                            reason(trail.verification().verify()), "verify reports a reset"),
                    () -> assertEquals(AuditChainVerification.BreakReason.RESET,
                            reason(trail.verification().full()), "full reports a reset"));
        }

        @Test
        @DisplayName("behind a genuine checkpoint already entered in the index after a later rotation: the next start "
                + "reports link_broken at line 1, and every later due append logs a rotation failure and never "
                + "rotates")
        void enteredCheckpointLaterRotation() throws Exception {
            var trail = new AuditTrailFixture(base, BY_SIZE);
            var key = trail.createKey();
            trail.append(2);
            enterCheckpoint(trail, key);
            tearTail(trail);

            trail.append(1);
            trail.append(1);

            var broken = assertInstanceOf(AuditChainVerification.Outcome.Broken.class, trail.verification().verify(),
                    "the verification reports a break");
            assertAll(
                    () -> assertEquals(5, lines(trail.logFile()).size(), "the records were appended"),
                    () -> assertEquals(2, trail.head.index().snapshot().entries().size(), "the log is never rotated again"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.resolveIdentifierString()),
                    () -> assertEquals(AuditChainVerification.BreakReason.LINK_BROKEN, broken.reason(),
                            "verify reports link_broken"),
                    () -> assertEquals(OptionalLong.of(1), broken.firstBrokenLine(),
                            "verify names the first line as broken"),
                    () -> assertEquals(AuditChainVerification.BreakReason.LINK_BROKEN,
                            reason(trail.verification().full()), "full reports link_broken"));
        }

        /**
         * Closes the current log by a genuine checkpoint and enters it in the index, as a rotation stopped before its
         * rename leaves it.
         */
        private void enterCheckpoint(AuditTrailFixture trail, SecretKey key) {
            var count = lines(trail.logFile()).size();
            var first = recordAt(trail.logFile(), 1);
            var headHash = hashOf(trail.logFile(), count);
            trail.underAuditLock(transaction -> {
                var checkpoint = checkpoint(trail, key, count, headHash);
                var checkpointHash = trail.log.appendCheckpoint(transaction, checkpoint);
                trail.head.index().append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        AuditIndexEntry.rotatedFileName(first.eventId()), first.eventId(), checkpoint.eventId(),
                        count + 1L, checkpointHash), _ -> {
                });
            });
        }

        private static void tearTail(AuditTrailFixture trail) throws IOException {
            Files.writeString(trail.logFile(), TORN, StandardOpenOption.APPEND);
        }

        private static AuditChainVerification.BreakReason reason(AuditChainVerification.Outcome outcome) {
            return assertInstanceOf(AuditChainVerification.Outcome.Broken.class, outcome,
                    "the verification reports a break").reason();
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
