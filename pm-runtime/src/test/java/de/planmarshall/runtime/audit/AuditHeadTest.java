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

import static de.planmarshall.runtime.audit.AuditTrailFixture.hashOf;
import static de.planmarshall.runtime.audit.AuditTrailFixture.lines;
import static de.planmarshall.runtime.audit.AuditTrailFixture.recordAt;
import static de.planmarshall.runtime.audit.AuditTrailFixture.words;
import static de.planmarshall.runtime.testing.generators.RuntimeGenerators.hmacKeys;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeFalse;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.junit.EnableGeneratorController;
import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.AuditChainVerification;

/**
 * The head of the audit log: written after every append under the enrolment integrity key, which it only reads.
 *
 * <p>
 * The foreign key, the damaged head and blocker texts, the keyring's failure message and a record count beyond the log
 * are generated. The literals that stay are the contract: the rotation limits at their boundary (one byte, thirty
 * days), the record counts the appends produce, the file mode {@code rw-------}, the record line's count member
 * {@code "n"} the edit replaces, the head and rotation reason codes and the break reasons of the verification.
 */
@EnableGeneratorController
@EnableTestLogger
@DisplayName("The audit head")
class AuditHeadTest {

    /** Limits a log reaches with its first record, so every later append would rotate. */
    private static final AuditRotation.Limits ROTATE_ALWAYS = new AuditRotation.Limits(1, Duration.ofDays(30));

    @TempDir
    Path base;

    @Nested
    @DisplayName("with the integrity key")
    class WithKey {

        @Test
        @DisplayName("is written after every append over the whole log, with a valid HMAC and mode 0600")
        void writtenAfterEveryAppend() throws Exception {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();

            for (var count = 1; count <= 3; count++) {
                trail.append(1);

                var head = trail.readHead().orElseThrow();
                var expectedCount = count;
                assertAll(
                        () -> assertEquals(expectedCount, head.recordCount(), "the head counts every record"),
                        () -> assertEquals(recordAt(trail.logFile(), expectedCount).eventId(), head.lastEventId(),
                                "the head names the last event"),
                        () -> assertEquals(hashOf(trail.logFile(), expectedCount), head.headHash(),
                                "the head anchors the last record's hash"),
                        () -> assertEquals(AuditCodec.GENESIS_HASH, head.indexHeadHash(),
                                "without rotation the index head is genesis"),
                        () -> assertTrue(AuditHead.verifies(key, head), "the head's HMAC verifies under the key"));
            }
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(trail.headFile())),
                    "the head file has mode 0600");
        }

        @Test
        @DisplayName("a head under another key or with an edited field fails the HMAC check")
        void foreignOrEditedHeadFails() {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(2);
            var head = trail.readHead().orElseThrow();
            var otherKey = hmacKeys().next();

            assertAll(
                    () -> assertTrue(AuditHead.verifies(key, head), "the written head verifies"),
                    () -> assertFalse(AuditHead.verifies(key, AuditHead.sign(otherKey, head)),
                            "a head under another key fails"),
                    () -> assertFalse(AuditHead.verifies(key, head.withRecordCount(head.recordCount() + 1)),
                            "a head with an edited count fails"),
                    () -> assertFalse(AuditHead.verifies(key, head.withHeadHash(AuditCodec.GENESIS_HASH)),
                            "a head with an edited hash fails"));
        }

        @Test
        @DisplayName("a log cut below the records the head anchored is never re-signed and stays truncated")
        void truncatedLogNotResigned() {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            trail.append(4);
            var anchored = trail.readHead().orElseThrow();
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            AuditTrailFixture.removeLine(trail.logFile(), 3);

            trail.append(1);

            assertAll(
                    () -> assertEquals(anchored, trail.readHead().orElseThrow(), "the anchored head is kept"),
                    () -> assertEquals(3, lines(trail.logFile()).size(), "the append stands on the cut log"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            AuditHead.ANCHORED_LINE_MISSING),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED, breakReason(trail),
                            "the cut log verifies as truncated"));
        }

        @Test
        @DisplayName("a replaced last line is never re-signed, though the log keeps its length")
        void replacedLastLineNotResigned() {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            trail.append(3);
            var anchored = trail.readHead().orElseThrow();
            AuditTrailFixture.editLine(trail.logFile(), 3, line -> line.replace("\"n\":2", "\"n\":7"));

            trail.append(1);

            assertAll(
                    () -> assertEquals(anchored, trail.readHead().orElseThrow(), "the anchored head is kept"),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH, breakReason(trail),
                            "the replaced line is a head mismatch"));
        }

        @Test
        @DisplayName("a head that anchors a record count beyond the log is never replaced by a recount")
        void headBeyondTheLogNotReplaced() throws Exception {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(2);
            int beyond = Generators.integers(4, 1_000).next();
            var claimed = AuditHead.sign(key,
                    trail.readHead().orElseThrow().withRecordCount(beyond).withHeadHash(AuditCodec.GENESIS_HASH));
            Files.write(trail.headFile(), AuditCodec.writeHead(claimed));

            trail.append(1);

            assertAll(
                    () -> assertEquals(beyond, trail.readHead().orElseThrow().recordCount(),
                            "the head beyond the log is kept"),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED, breakReason(trail),
                            "the log verifies as truncated"));
        }

        @Test
        @DisplayName("a stop between an append and its head is closed by a recount over the whole log")
        void crashGapRecounted() throws Exception {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(2);
            var beforeGap = Files.readAllBytes(trail.headFile());
            trail.append(2);
            Files.write(trail.headFile(), beforeGap);

            trail.append(1);

            var head = trail.readHead().orElseThrow();
            assertAll(
                    () -> assertEquals(5, head.recordCount(), "the recount covers the whole log"),
                    () -> assertEquals(hashOf(trail.logFile(), 5), head.headHash(),
                            "the recount anchors the last record"),
                    () -> assertTrue(AuditHead.verifies(key, head), "the recounted head verifies"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the recounted log verifies intact"));
        }

        @Test
        @DisplayName("a damaged head over a cut log is never replaced by a recount, so the cut stays detectable")
        void damagedHeadOverCutLogNotReplaced() throws Exception {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            trail.append(4);
            AuditTrailFixture.removeLine(trail.logFile(), 4);
            AuditTrailFixture.removeLine(trail.logFile(), 3);
            var damaged = words();
            Files.writeString(trail.headFile(), damaged);

            trail.append(1);

            assertAll(
                    () -> assertEquals(damaged, Files.readString(trail.headFile()), "the damaged head is kept"),
                    () -> assertEquals(3, lines(trail.logFile()).size(), "the append stands on the cut log"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.format(AuditHead.HEAD_UNVERIFIABLE)),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH, breakReason(trail),
                            "the damaged head is a head mismatch"),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH, assertInstanceOf(
                            AuditChainVerification.Outcome.Broken.class, trail.verification().full(),
                            "the full verification is broken").reason(),
                            "the full verification names the mismatch"));
        }

        @Test
        @DisplayName("a head that parses but does not verify under the key is never replaced")
        void foreignHeadNotReplaced() throws Exception {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            trail.append(2);
            var foreign = AuditHead.sign(hmacKeys().next(), trail.readHead().orElseThrow());
            Files.write(trail.headFile(), AuditCodec.writeHead(foreign));

            trail.append(1);

            assertAll(
                    () -> assertEquals(foreign, trail.readHead().orElseThrow(), "the foreign head is kept"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.format(AuditHead.HEAD_UNVERIFIABLE)),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH, breakReason(trail),
                            "the foreign head is a head mismatch"));
        }

        @Test
        @DisplayName("an absent head lets the lines be counted, the first head over the whole log")
        void absentHeadCountsTheLines() throws Exception {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(2);
            Files.delete(trail.headFile());

            trail.append(1);

            var head = trail.readHead().orElseThrow();
            assertAll(
                    () -> assertEquals(3, head.recordCount(), "the first head counts every line"),
                    () -> assertTrue(AuditHead.verifies(key, head), "the first head verifies"),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify(),
                            "the counted log verifies intact"));
        }

        @Test
        @DisplayName("a head that cannot be read or written is logged as head_unwritable and the append stands")
        void unwritableHead() throws Exception {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            Files.createDirectories(trail.headFile());
            Files.writeString(trail.headFile().resolve("blocker"), words());

            trail.append(1);

            assertAll(
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the append stands without a head"),
                    () -> assertTrue(Files.isDirectory(trail.headFile()), "the blocking directory is kept"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.format(AuditHead.HEAD_UNWRITABLE)));
        }

        @Test
        @DisplayName("a head that cannot be read refuses a due rotation, and the record is appended to the current log")
        void unreadableHeadRefusesDueRotation() throws Exception {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(1);
            Files.delete(trail.headFile());
            Files.createDirectories(trail.headFile());
            Files.writeString(trail.headFile().resolve("blocker"), words());

            trail.append(1);

            assertAll(
                    () -> assertFalse(Files.exists(trail.indexFile()), "no checkpoint was signed"),
                    () -> assertEquals(2, lines(trail.logFile()).size(), "the record goes to the current log"),
                    () -> assertFalse(recordAt(trail.logFile(), 2).isCheckpoint(),
                            "the appended record is no checkpoint"),
                    () -> assertTrue(Files.isDirectory(trail.headFile()), "the blocking directory is kept"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_FAILED.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.format(AuditHead.HEAD_UNWRITABLE)));
        }

        @Test
        @DisplayName("a failed rotation over a log that cannot be read counts as pending and is thrown")
        void unreadableLogAfterFailedRotationIsPending() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(1);
            var thrown = new AtomicReference<RuntimeException>();

            trail.underAuditLock(transaction -> {
                permissions(trail.logFile(), "---------");
                try {
                    assumeFalse(Files.isReadable(trail.logFile()), "the log stays readable, as for a superuser");
                    thrown.set(assertThrows(UncheckedIOException.class,
                            () -> trail.head.beforeAppend(transaction, trail.log),
                            "an unreadable log after a failed rotation"));
                } finally {
                    permissions(trail.logFile(), "rw-------");
                }
            });

            assertAll(
                    () -> assertEquals(1, thrown.get().getSuppressed().length,
                            "the rotation failure is suppressed once"),
                    () -> assertInstanceOf(UncheckedIOException.class, thrown.get().getSuppressed()[0],
                            "the suppressed failure is an I/O failure"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_ROTATION_PENDING.resolveIdentifierString()));
        }

        private static void permissions(Path file, String permissions) {
            try {
                Files.setPosixFilePermissions(file, PosixFilePermissions.fromString(permissions));
            } catch (IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    @Nested
    @DisplayName("without the integrity key")
    class WithoutKey {

        @Test
        @DisplayName("the append stands, no head is written, no rotation happens and no key is created")
        void noKey() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);

            trail.append(3);

            assertAll(
                    () -> assertEquals(3, lines(trail.logFile()).size(), "every append stands without a key"),
                    () -> assertFalse(Files.exists(trail.headFile()), "no head is written without a key"),
                    () -> assertFalse(Files.exists(trail.indexFile()), "no rotation happens without a key"),
                    () -> assertTrue(trail.keyring.entries.isEmpty(), "no key is created by the append"),
                    () -> assertEquals(Optional.empty(), trail.integrityKey.read(),
                            "the integrity key is still absent"),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, AuditHead.class));
        }

        @Test
        @DisplayName("the first append after the key appears writes the head over the whole log")
        void firstAppendAfterKey() {
            var trail = new AuditTrailFixture(base);
            trail.append(2);
            var key = trail.createKey();

            trail.append(1);

            var head = trail.readHead().orElseThrow();
            assertAll(
                    () -> assertEquals(3, head.recordCount(), "the first head counts the whole log"),
                    () -> assertEquals(hashOf(trail.logFile(), 3), head.headHash(),
                            "the first head anchors the last record"),
                    () -> assertTrue(AuditHead.verifies(key, head), "the first head verifies under the key"));
        }

        @Test
        @DisplayName("with an unreadable key the append stands and the warning names the reason, never the key")
        void unreadableKey() {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            var secret = trail.keyring.entries.values().iterator().next();
            trail.keyring.failure = new SecretStoreException(SecretStoreException.Reason.LOCKED, words());

            trail.append(1);

            assertAll(
                    () -> assertEquals(1, lines(trail.logFile()).size(), "the append stands without the key"),
                    () -> assertFalse(Files.exists(trail.headFile()), "no head is written without the key"),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            SecretStoreException.Reason.LOCKED.code()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, secret));
        }
    }

    private static AuditChainVerification.BreakReason breakReason(AuditTrailFixture trail) {
        return assertInstanceOf(AuditChainVerification.Outcome.Broken.class, trail.verification().verify(),
                "the verification is broken").reason();
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("a missing head reads as empty")
        void missing() {
            assertEquals(Optional.empty(), AuditHead.read(AuditHead.headFile(base)), "a missing head reads as empty");
        }

        @Test
        @DisplayName("a head that does not parse is refused")
        void unparsable() throws Exception {
            var headFile = AuditHead.headFile(base);
            Files.createDirectories(headFile.getParent());
            // an empty JSON object is a head document without its members
            Files.writeString(headFile, "{}");

            assertThrows(StoreRefusal.class, () -> AuditHead.read(headFile), "an unparsable head is refused");
        }

        @Test
        @DisplayName("names the head file beside the log and offers the index of the rotation")
        void files() {
            var trail = new AuditTrailFixture(base);

            assertAll(
                    () -> assertEquals(trail.headFile(), trail.head.file(), "the head file lies beside the log"),
                    () -> assertEquals(trail.indexFile(), trail.head.index().file(),
                            "the head offers the rotation's index"),
                    () -> assertEquals(Optional.empty(), trail.head.emptyLogPredecessor(),
                            "a new log has no empty predecessor"));
        }
    }
}
