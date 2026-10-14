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
import java.util.Map;
import java.util.OptionalLong;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.TransactionId;
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

    @Nested
    @DisplayName("a pending rotation")
    class Pending {

        /** A key other than the integrity key, as anyone without the key could sign with. */
        private static final SecretKey OTHER_KEY = new SecretKeySpec(new byte[32], EnrolmentKey.ALGORITHM);

        @Test
        @DisplayName("closed by a genuine checkpoint is intact, incrementally and in full")
        void genuineCheckpointIntact() {
            var trail = fiveRecords();
            var key = trail.createKey();
            var headHash = AuditTrailFixture.hashOf(trail.logFile(), 5);
            trail.underAuditLock(transaction -> trail.log.appendCheckpoint(transaction,
                    checkpoint(trail, key, 5, headHash)));

            assertAll(
                    () -> assertEquals(INTACT, trail.verification().verify()),
                    () -> assertEquals(INTACT, trail.verification().full()));
        }

        @Test
        @DisplayName("closed by a forged checkpoint after a cut is link_broken at the checkpoint, in both")
        void forgedCheckpointOverCutLog() {
            var trail = fiveRecords();
            removeLine(trail.logFile(), 5);
            removeLine(trail.logFile(), 4);
            var headHash = AuditTrailFixture.hashOf(trail.logFile(), 3);
            trail.underAuditLock(transaction -> trail.log.appendCheckpoint(transaction,
                    checkpoint(trail, OTHER_KEY, 3, headHash)));

            assertAll(
                    () -> assertLinkBroken(4, trail.verification().verify()),
                    () -> assertLinkBroken(4, trail.verification().full()));
        }

        @Test
        @DisplayName("closed by a forged checkpoint the index already names is link_broken at the checkpoint, in both")
        void forgedCheckpointEntered() {
            var trail = fiveRecords();
            var first = recordAt(trail.logFile(), 1);
            removeLine(trail.logFile(), 5);
            var headHash = AuditTrailFixture.hashOf(trail.logFile(), 4);
            trail.underAuditLock(transaction -> {
                var forged = checkpoint(trail, OTHER_KEY, 4, headHash);
                var forgedHash = trail.log.appendCheckpoint(transaction, forged);
                trail.head.index().append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        AuditIndexEntry.rotatedFileName(first.eventId()), first.eventId(), forged.eventId(), 5,
                        forgedHash), _ -> {
                });
            });

            assertAll(
                    () -> assertLinkBroken(5, trail.verification().verify()),
                    () -> assertLinkBroken(5, trail.verification().full()));
        }

        private static AuditRecord checkpoint(AuditTrailFixture trail, SecretKey key, long recordCount,
                String headHash) {
            return AuditRecord.builder()
                    .eventId(TransactionId.generate(trail.clock).value())
                    .prevHash(AuditCodec.GENESIS_HASH)
                    .timestamp(trail.clock.instant())
                    .category(AuditRecord.Category.SECURITY)
                    .event(AuditRecord.EVENT_CHECKPOINT)
                    .scopeType(AuditRecord.Scope.MACHINE)
                    .actor(AuditTrailFixture.USER)
                    .outcome(AuditRecord.Outcome.SUCCEEDED)
                    .details(Map.of(AuditRotation.DETAIL_RECORD_COUNT, recordCount, AuditRotation.DETAIL_HEAD_HASH,
                            headHash, AuditRotation.DETAIL_HMAC,
                            AuditRotation.checkpointHmac(key, recordCount, headHash)))
                    .build();
        }
    }

    @Nested
    @DisplayName("a rotation stopped after its rename, before the next record and its head")
    class StoppedAfterRename {

        /** A key other than the integrity key, as anyone without the key could sign with. */
        private static final SecretKey OTHER_KEY = new SecretKeySpec(new byte[32], EnrolmentKey.ALGORITHM);

        /** A torn first line of the new log: a stop while the record was written. */
        private static final String TORN = "{\"format_version\":1,\"prev_hash\":\"00";

        /**
         * Appends the records under the limits, then rotates the log and stops after the rename: no current log, no
         * record of the new log, and the head of the last append before the rotation.
         */
        private AuditTrailFixture stopped(AuditRotation.Limits limits, int records) {
            var trail = new AuditTrailFixture(base, limits);
            var key = trail.createKey();
            trail.append(records);
            var rotation = new AuditRotation(trail.head.index(), ROTATE_ALWAYS, trail.clock, AuditTrailFixture.USER);
            trail.underAuditLock(transaction -> assertTrue(
                    rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));
            return trail;
        }

        private AuditTrailFixture stopped() {
            return stopped(AuditRotation.Limits.DEFAULTS, 2);
        }

        private static Path rotatedFile(AuditTrailFixture trail) {
            return trail.logFile().resolveSibling(trail.head.index().snapshot().entries().getLast().file());
        }

        private static void assertBrokenInBoth(BreakReason reason, AuditTrailFixture trail) {
            assertAll(
                    () -> assertBroken(reason, trail.verification().verify()),
                    () -> assertBroken(reason, trail.verification().full()));
        }

        private static void assertLinkBrokenInBoth(long line, AuditTrailFixture trail) {
            assertAll(
                    () -> assertLinkBroken(line, trail.verification().verify()),
                    () -> assertLinkBroken(line, trail.verification().full()));
        }

        @Test
        @DisplayName("is intact with the new log absent, empty or torn, incrementally and in full")
        void intact() throws Exception {
            var trail = stopped();
            var logAbsent = !Files.exists(trail.logFile());
            var absent = List.of(trail.verification().verify(), trail.verification().full());
            Files.createFile(trail.logFile());
            var empty = List.of(trail.verification().verify(), trail.verification().full());
            Files.writeString(trail.logFile(), TORN, StandardCharsets.UTF_8);
            var torn = List.of(trail.verification().verify(), trail.verification().full());

            assertAll(
                    () -> assertTrue(logAbsent, "the log was renamed"),
                    () -> assertEquals(List.of(INTACT, INTACT), absent),
                    () -> assertEquals(List.of(INTACT, INTACT), empty),
                    () -> assertEquals(List.of(INTACT, INTACT), torn));
        }

        @Test
        @DisplayName("is intact after earlier rotations, the rotated log chaining to the checkpoint before it")
        void intactAfterEarlierRotations() {
            var trail = stopped(ROTATE_ALWAYS, 3);

            assertAll(
                    () -> assertEquals(3, trail.head.index().snapshot().entries().size()),
                    () -> assertEquals(INTACT, trail.verification().verify()),
                    () -> assertEquals(INTACT, trail.verification().full()));
        }

        @Test
        @DisplayName("without the key, or with an unreadable key, is not verified, never intact")
        void notVerifiedWithoutKey() {
            var trail = stopped();
            var withoutKey = new AuditTrailFixture(base).verification().verify();
            trail.keyring.failure = new SecretStoreException(SecretStoreException.Reason.LOCKED, "keyring locked");

            assertAll(
                    () -> assertInstanceOf(Outcome.NotVerified.class, withoutKey),
                    () -> assertInstanceOf(Outcome.NotVerified.class, trail.verification().verify()),
                    () -> assertInstanceOf(Outcome.NotVerified.class, trail.verification().full()));
        }

        @Test
        @DisplayName("with a head under another key is head_mismatch")
        void headUnderOtherKey() throws Exception {
            var trail = stopped();
            Files.write(trail.headFile(), AuditCodec.writeHead(AuditHead.sign(OTHER_KEY,
                    trail.readHead().orElseThrow())));

            assertBrokenInBoth(BreakReason.HEAD_MISMATCH, trail);
        }

        @Test
        @DisplayName("with a genuine earlier head, anchoring another line of the rotated log, is head_mismatch")
        void earlierHead() throws Exception {
            var trail = new AuditTrailFixture(base);
            var key = trail.createKey();
            trail.append(1);
            var earlier = Files.readAllBytes(trail.headFile());
            trail.append(1);
            var rotation = new AuditRotation(trail.head.index(), ROTATE_ALWAYS, trail.clock, AuditTrailFixture.USER);
            trail.underAuditLock(transaction -> assertTrue(
                    rotation.rotateIfDue(transaction, trail.log, key, trail.head.closeCheck(key))));
            Files.write(trail.headFile(), earlier);

            assertBrokenInBoth(BreakReason.HEAD_MISMATCH, trail);
        }

        @Test
        @DisplayName("with the rotated log gone is missing")
        void rotatedLogGone() throws Exception {
            var trail = stopped();
            Files.delete(rotatedFile(trail));

            assertBrokenInBoth(BreakReason.MISSING, trail);
        }

        @Test
        @DisplayName("with a record cut from the rotated log is link_broken at the line after the cut")
        void rotatedLogCut() {
            var trail = stopped();
            removeLine(rotatedFile(trail), 2);

            assertLinkBrokenInBoth(2, trail);
        }

        @Test
        @DisplayName("with the checkpoint cut from the rotated log is truncated")
        void checkpointCut() {
            var trail = stopped();
            removeLine(rotatedFile(trail), 3);

            assertBrokenInBoth(BreakReason.TRUNCATED, trail);
        }

        @Test
        @DisplayName("with an index entry that does not match the rotated log is link_broken at the checkpoint")
        void mismatchingIndexEntry() throws Exception {
            var trail = stopped();
            var entry = trail.head.index().snapshot().entries().getLast();
            Files.delete(trail.indexFile());
            trail.underAuditLock(transaction -> trail.head.index().append(transaction, entry.withRecordCount(2),
                    _ -> {
                    }));

            assertLinkBrokenInBoth(3, trail);
        }

        @Test
        @DisplayName("with a cut log closed by a forged checkpoint, entered and renamed by hand, is link_broken")
        void forgedCheckpointRenamedByHand() throws Exception {
            var trail = fiveRecords();
            var first = recordAt(trail.logFile(), 1);
            removeLine(trail.logFile(), 5);
            removeLine(trail.logFile(), 4);
            removeLine(trail.logFile(), 3);
            var headHash = AuditTrailFixture.hashOf(trail.logFile(), 2);
            trail.underAuditLock(transaction -> {
                var forged = Pending.checkpoint(trail, OTHER_KEY, 2, headHash);
                var forgedHash = trail.log.appendCheckpoint(transaction, forged);
                trail.head.index().append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                        AuditIndexEntry.rotatedFileName(first.eventId()), first.eventId(), forged.eventId(), 3,
                        forgedHash), _ -> {
                });
            });
            Files.move(trail.logFile(), trail.rotatedFile(first.eventId()));

            assertLinkBrokenInBoth(3, trail);
        }

        @Test
        @DisplayName("with a record of the new log written without its head is head_mismatch")
        void recordWithoutHead() {
            var trail = stopped();
            new AuditTrailFixture(base).append(1);

            assertAll(
                    () -> assertEquals(1, lines(trail.logFile()).size()),
                    () -> assertBroken(BreakReason.HEAD_MISMATCH, trail.verification().verify()));
        }

        @Test
        @DisplayName("is not changed by a verification")
        void changesNothing() {
            var trail = stopped();
            var before = trail.files();

            trail.verification().verify();
            trail.verification().full();

            assertEquals(before, trail.files());
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
