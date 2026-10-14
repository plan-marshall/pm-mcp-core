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
import static de.planmarshall.runtime.audit.AuditTrailFixture.recordAt;
import static de.planmarshall.runtime.audit.AuditTrailFixture.removeLine;
import static de.planmarshall.runtime.audit.AuditTrailFixture.writeLines;
import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.spec.SecretKeySpec;

import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.start.AuditChainVerification.BreakReason;
import de.planmarshall.runtime.start.AuditChainVerification.Outcome;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * The verification of the audit trail, the acceptance of the package: every tamper case of the work package is
 * detected with its reason, a rotation in the middle of a verification is not, and a verification changes nothing.
 */
@DisplayName("The audit verification")
class AuditVerificationTest {

    /** Limits a log reaches with its first record, so every later append rotates it. */
    private static final AuditRotation.Limits ROTATE_ALWAYS = new AuditRotation.Limits(1, Duration.ofDays(30));

    private static final Outcome INTACT = new Outcome.Intact();

    @TempDir
    Path base;

    /** @return a trail with the key and five records */
    private AuditTrailFixture fiveRecords() {
        var trail = new AuditTrailFixture(base);
        trail.createKey();
        trail.append(5);
        return trail;
    }

    private static Outcome.Broken assertBroken(BreakReason reason, Outcome outcome) {
        var broken = assertInstanceOf(Outcome.Broken.class, outcome);
        assertEquals(reason, broken.reason(), broken::diagnostic);
        return broken;
    }

    private static void assertLinkBroken(long line, Outcome outcome) {
        var broken = assertBroken(BreakReason.LINK_BROKEN, outcome);
        assertEquals(OptionalLong.of(line), broken.firstBrokenLine(), broken::diagnostic);
    }

    @Test
    @DisplayName("an intact chain is intact, incrementally and in full")
    void intact() {
        var trail = fiveRecords();

        assertAll(
                () -> assertEquals(INTACT, trail.verification().verify()),
                () -> assertEquals(INTACT, trail.verification().full()));
    }

    @Nested
    @DisplayName("not verified")
    class NotVerified {

        @Test
        @DisplayName("without the integrity key nothing is anchored yet")
        void noKey() {
            var trail = new AuditTrailFixture(base);
            trail.append(3);

            assertInstanceOf(Outcome.NotVerified.class, trail.verification().verify());
        }

        @Test
        @DisplayName("with the key and no audit trail at all")
        void noChain() {
            var trail = new AuditTrailFixture(base);
            trail.createKey();

            assertEquals(new Outcome.NotVerified("no audit chain"), trail.verification().verify());
        }

        @Test
        @DisplayName("with an unreadable key, naming its reason")
        void unreadableKey() {
            var trail = fiveRecords();
            trail.keyring.failure = new SecretStoreException(SecretStoreException.Reason.LOCKED, "keyring locked");

            var outcome = assertInstanceOf(Outcome.NotVerified.class, trail.verification().verify());

            assertTrue(outcome.reason().contains(SecretStoreException.Reason.LOCKED.code()), outcome.reason());
        }
    }

    @Nested
    @DisplayName("link_broken with the first broken line")
    class LinkBroken {

        @Test
        @DisplayName("a removed record")
        void removedRecord() {
            var trail = fiveRecords();
            removeLine(trail.logFile(), 3);

            assertLinkBroken(3, trail.verification().verify());
        }

        @Test
        @DisplayName("a reordered record")
        void reorderedRecord() {
            var trail = fiveRecords();
            var lines = new ArrayList<>(lines(trail.logFile()));
            lines.add(1, lines.remove(2));
            writeLines(trail.logFile(), lines);

            assertLinkBroken(2, trail.verification().verify());
        }

        @Test
        @DisplayName("an edited record")
        void editedRecord() {
            var trail = fiveRecords();
            editLine(trail.logFile(), 2, line -> line.replace("\"n\":1", "\"n\":9"));

            assertLinkBroken(3, trail.verification().verify());
        }

        @Test
        @DisplayName("a line that does not parse")
        void unparsableRecord() {
            var trail = fiveRecords();
            editLine(trail.logFile(), 4, _ -> "not a record");

            assertLinkBroken(4, trail.verification().verify());
        }

        @Test
        @DisplayName("an index entry that does not chain")
        void brokenIndex() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(3);
            editLine(trail.indexFile(), 1, line -> line.replace("\"record_count\":2", "\"record_count\":7"));

            var broken = assertBroken(BreakReason.LINK_BROKEN, trail.verification().verify());

            assertTrue(broken.diagnostic().contains(AuditIndex.FILE_NAME), broken.diagnostic());
        }
    }

    @Nested
    @DisplayName("head_mismatch")
    class HeadMismatch {

        @Test
        @DisplayName("a head replaced by one under another key")
        void replacedHead() throws Exception {
            var trail = fiveRecords();
            var head = trail.readHead().orElseThrow();
            var otherKey = new SecretKeySpec(new byte[32], EnrolmentKey.ALGORITHM);
            Files.write(trail.headFile(), AuditCodec.writeHead(AuditHead.sign(otherKey, head)));

            assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify());
        }

        @Test
        @DisplayName("a head with an edited field")
        void editedHead() throws Exception {
            var trail = fiveRecords();
            var head = trail.readHead().orElseThrow();
            Files.write(trail.headFile(), AuditCodec.writeHead(head.withRecordCount(4)));

            assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify());
        }

        @Test
        @DisplayName("a deleted head of a non-empty log")
        void deletedHead() throws Exception {
            var trail = fiveRecords();
            Files.delete(trail.headFile());

            assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify());
        }

        @Test
        @DisplayName("an edited last record, which no line after it chains to")
        void editedLastRecord() {
            var trail = fiveRecords();
            editLine(trail.logFile(), 5, line -> line.replace("\"n\":4", "\"n\":8"));

            assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify());
        }

        @Test
        @DisplayName("a chained record appended without a head")
        void unanchoredRecord() {
            var trail = fiveRecords();
            var head = trail.headFile();
            var anchored = trail.files().get(AuditHead.FILE_NAME);
            trail.append(1);
            writeString(head, anchored);

            assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify());
        }

        @Test
        @DisplayName("a head that does not anchor the last index line")
        void indexNotAnchored() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(2);
            var head = trail.files().get(AuditHead.FILE_NAME);
            trail.append(1);
            writeString(trail.headFile(), head);

            assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify());
        }
    }

    @Test
    @DisplayName("missing: the log is gone while its head anchors records")
    void missing() throws Exception {
        var trail = fiveRecords();
        Files.delete(trail.logFile());

        assertBroken(BreakReason.MISSING, trail.verification().verify());
    }

    @Nested
    @DisplayName("reset")
    class Reset {

        @Test
        @DisplayName("the log is emptied while its head anchors records")
        void emptied() {
            var trail = fiveRecords();
            writeLines(trail.logFile(), List.of());

            assertBroken(BreakReason.RESET, trail.verification().verify());
        }

        @Test
        @DisplayName("the log after a rotation starts a new chain at genesis")
        void newChainAfterRotation() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(2);
            var restarted = recordAt(trail.logFile(), 1).withPrevHash(AuditCodec.GENESIS_HASH);
            writeLines(trail.logFile(),
                    List.of(new String(AuditCodec.writeRecord(restarted), StandardCharsets.UTF_8)));

            assertBroken(BreakReason.RESET, trail.verification().verify());
        }
    }

    @Test
    @DisplayName("truncated: the log holds fewer records than its head anchors")
    void truncated() {
        var trail = fiveRecords();
        removeLine(trail.logFile(), 5);

        assertBroken(BreakReason.TRUNCATED, trail.verification().verify());
    }

    @Test
    @DisplayName("a rotation in the middle of a verification is reported intact")
    void rotationDuringVerification() {
        var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
        trail.createKey();
        trail.append(3);
        var rotations = new AtomicInteger();
        var verification = new AuditVerification(base, trail.integrityKey, () -> {
            if (rotations.getAndIncrement() == 0) {
                trail.append(1);
            }
        });

        var outcome = verification.verify();

        assertAll(
                () -> assertEquals(INTACT, outcome),
                () -> assertEquals(3, trail.head.index().snapshot().entries().size()),
                () -> assertTrue(rotations.get() >= 2, "the reads were repeated after the rotation"));
    }

    @Nested
    @DisplayName("full")
    class Full {

        @Test
        @DisplayName("verifies every rotated file the index lists")
        void acrossRotatedFiles() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(4);

            assertAll(
                    () -> assertEquals(3, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(INTACT, trail.verification().full()));
        }

        @Test
        @DisplayName("finds an edited rotated file, which the incremental verification does not read")
        void editedRotatedFile() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(4);
            var rotated = trail.logFile().resolveSibling(trail.head.index().snapshot().entries().get(1).file());
            editLine(rotated, 1, line -> line.replace("\"n\":1", "\"n\":9"));

            assertAll(
                    () -> assertEquals(INTACT, trail.verification().verify()),
                    () -> assertLinkBroken(2, trail.verification().full()));
        }

        @Test
        @DisplayName("finds a truncated rotated file")
        void truncatedRotatedFile() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(3);
            var rotated = trail.logFile().resolveSibling(trail.head.index().snapshot().entries().getFirst().file());
            removeLine(rotated, 2);

            assertBroken(BreakReason.TRUNCATED, trail.verification().full());
        }

        @Test
        @DisplayName("finds a line added after the checkpoint of a rotated file")
        void lineAfterCheckpoint() {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(3);
            var rotated = trail.logFile().resolveSibling(trail.head.index().snapshot().entries().getFirst().file());
            var lines = new ArrayList<>(lines(rotated));
            lines.add("not a record");
            writeLines(rotated, lines);

            assertLinkBroken(3, trail.verification().full());
        }

        @Test
        @DisplayName("accounts for a purged rotated file by its index entry")
        void purgedRotatedFile() throws Exception {
            var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
            trail.createKey();
            trail.append(4);
            Files.delete(trail.logFile().resolveSibling(trail.head.index().snapshot().entries().getFirst().file()));

            assertEquals(INTACT, trail.verification().full());
        }
    }

    @Test
    @DisplayName("a verification changes nothing, also on a broken chain")
    void changesNothing() {
        var trail = new AuditTrailFixture(base, ROTATE_ALWAYS);
        trail.createKey();
        trail.append(4);
        removeLine(trail.logFile(), 1);
        var before = trail.files();

        trail.verification().verify();
        trail.verification().full();

        assertEquals(before, trail.files());
    }

    private static void writeString(Path file, String content) {
        try {
            Files.writeString(file, content, StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
