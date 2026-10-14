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
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.time.Duration;
import java.util.Optional;
import javax.crypto.spec.SecretKeySpec;

import de.cuioss.test.juli.LogAsserts;
import de.cuioss.test.juli.TestLogLevel;
import de.cuioss.test.juli.junit5.EnableTestLogger;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;
import de.planmarshall.runtime.start.AuditChainVerification;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The head of the audit log: written after every append under the enrolment integrity key, which it only reads.
 */
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
                        () -> assertEquals(expectedCount, head.recordCount()),
                        () -> assertEquals(recordAt(trail.logFile(), expectedCount).eventId(), head.lastEventId()),
                        () -> assertEquals(hashOf(trail.logFile(), expectedCount), head.headHash()),
                        () -> assertEquals(AuditCodec.GENESIS_HASH, head.indexHeadHash()),
                        () -> assertTrue(AuditHead.verifies(key, head)));
            }
            assertEquals("rw-------", PosixFilePermissions.toString(Files.getPosixFilePermissions(trail.headFile())));
        }

        @Test
        @DisplayName("a head under another key or with an edited field fails the HMAC check")
        void foreignOrEditedHeadFails() {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(2);
            var head = trail.readHead().orElseThrow();
            var otherKey = new SecretKeySpec(new byte[32], EnrolmentKey.ALGORITHM);

            assertAll(
                    () -> assertTrue(AuditHead.verifies(key, head)),
                    () -> assertFalse(AuditHead.verifies(key, AuditHead.sign(otherKey, head))),
                    () -> assertFalse(AuditHead.verifies(key, head.withRecordCount(head.recordCount() + 1))),
                    () -> assertFalse(AuditHead.verifies(key, head.withHeadHash(AuditCodec.GENESIS_HASH))));
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
                    () -> assertEquals(anchored, trail.readHead().orElseThrow()),
                    () -> assertEquals(3, lines(trail.logFile()).size()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            AuditHead.ANCHORED_LINE_MISSING),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED, breakReason(trail)));
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
                    () -> assertEquals(anchored, trail.readHead().orElseThrow()),
                    () -> assertEquals(AuditChainVerification.BreakReason.HEAD_MISMATCH, breakReason(trail)));
        }

        @Test
        @DisplayName("a head that anchors a record count beyond the log is never replaced by a recount")
        void headBeyondTheLogNotReplaced() throws Exception {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(2);
            var claimed = AuditHead.sign(key,
                    trail.readHead().orElseThrow().withRecordCount(9).withHeadHash(AuditCodec.GENESIS_HASH));
            Files.write(trail.headFile(), AuditCodec.writeHead(claimed));

            trail.append(1);

            assertAll(
                    () -> assertEquals(9, trail.readHead().orElseThrow().recordCount()),
                    () -> assertEquals(AuditChainVerification.BreakReason.TRUNCATED, breakReason(trail)));
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
                    () -> assertEquals(5, head.recordCount()),
                    () -> assertEquals(hashOf(trail.logFile(), 5), head.headHash()),
                    () -> assertTrue(AuditHead.verifies(key, head)),
                    () -> assertEquals(new AuditChainVerification.Outcome.Intact(), trail.verification().verify()));
        }

        @Test
        @DisplayName("a head that does not parse makes the lines be counted")
        void unreadableHeadCountsTheLines() throws Exception {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            trail.append(2);
            Files.writeString(trail.headFile(), "not a head");

            trail.append(1);

            assertEquals(3, trail.readHead().orElseThrow().recordCount());
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
                    () -> assertEquals(3, lines(trail.logFile()).size()),
                    () -> assertFalse(Files.exists(trail.headFile())),
                    () -> assertFalse(Files.exists(trail.indexFile())),
                    () -> assertTrue(trail.keyring.entries.isEmpty()),
                    () -> assertEquals(Optional.empty(), trail.integrityKey.read()),
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
                    () -> assertEquals(3, head.recordCount()),
                    () -> assertEquals(hashOf(trail.logFile(), 3), head.headHash()),
                    () -> assertTrue(AuditHead.verifies(key, head)));
        }

        @Test
        @DisplayName("with an unreadable key the append stands and the warning names the reason, never the key")
        void unreadableKey() {
            var trail = new AuditTrailFixture(base);
            trail.createKey();
            var secret = trail.keyring.entries.values().iterator().next();
            trail.keyring.failure = new SecretStoreException(SecretStoreException.Reason.LOCKED, "keyring locked");

            trail.append(1);

            assertAll(
                    () -> assertEquals(1, lines(trail.logFile()).size()),
                    () -> assertFalse(Files.exists(trail.headFile())),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            WARN.AUDIT_HEAD_NOT_WRITTEN.resolveIdentifierString()),
                    () -> LogAsserts.assertLogMessagePresentContaining(TestLogLevel.WARN,
                            SecretStoreException.Reason.LOCKED.code()),
                    () -> LogAsserts.assertNoLogMessagePresent(TestLogLevel.WARN, secret));
        }
    }

    private static AuditChainVerification.BreakReason breakReason(AuditTrailFixture trail) {
        return assertInstanceOf(AuditChainVerification.Outcome.Broken.class, trail.verification().verify()).reason();
    }

    @Nested
    @DisplayName("read")
    class Read {

        @Test
        @DisplayName("a missing head reads as empty")
        void missing() {
            assertEquals(Optional.empty(), AuditHead.read(AuditHead.headFile(base)));
        }

        @Test
        @DisplayName("a head that does not parse is refused")
        void unparsable() throws Exception {
            var headFile = AuditHead.headFile(base);
            Files.createDirectories(headFile.getParent());
            Files.writeString(headFile, "{}");

            assertThrows(StoreRefusal.class, () -> AuditHead.read(headFile));
        }

        @Test
        @DisplayName("names the head file beside the log and offers the index of the rotation")
        void files() {
            var trail = new AuditTrailFixture(base);

            assertAll(
                    () -> assertEquals(trail.headFile(), trail.head.file()),
                    () -> assertEquals(trail.indexFile(), trail.head.index().file()),
                    () -> assertEquals(Optional.empty(), trail.head.emptyLogPredecessor()));
        }
    }
}
