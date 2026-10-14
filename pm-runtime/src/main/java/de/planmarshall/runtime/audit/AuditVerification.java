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

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.NoSuchFileException;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;

import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditHeadRecord;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.runtime.credentials.SecretStoreException;
import de.planmarshall.runtime.enrolment.EnrolmentKey;
import de.planmarshall.runtime.start.AuditChainVerification;

/**
 * The verification of the audit trail (PM-SEC-6; Store Schemas, Security &amp; Transition Audit Log, Hash chain): it
 * fills the seam {@link AuditChainVerification} of the start with the incremental verification ({@link #verify()})
 * and offers the full verification that {@code pm-operator audit verify} requests ({@link #full()}).
 * <p>
 * The incremental verification checks, in this order: the HMAC of the head under the enrolment integrity key, the
 * chain of the audit index and the head's anchor of its last line, and the records of the current log, which starts
 * after the last checkpoint: its first record chains to the {@code checkpoint_hash} of the last index entry (to
 * {@link AuditCodec#GENESIS_HASH} when no rotation ever happened), every other record to the line before it; the
 * number of records and the last line are the ones the head anchors. The full verification checks every rotated file
 * the index lists before: its records, its count, its first and last record and its checkpoint; a purged file is
 * accounted for by its entry.
 * <p>
 * A current log whose last line is a checkpoint is a pending rotation, interrupted after its checkpoint, before or
 * after its index entry: it is verified as the rotated file it is about to become, against that entry once written,
 * and the head anchors the line before the checkpoint and the index line before that entry. Its checkpoint is verified
 * as the writer verifies it before completing the rotation ({@code AuditRotation.checkpointVerifies}): it counts the
 * records before it, names and chains to the line before it, and its HMAC verifies under the key. A checkpoint
 * appended to a cut log does not, and is reported as {@code link_broken} at its line by both verifications.
 * <p>
 * A rotation stopped after its rename, before the first record of the new log and its head, leaves a current log
 * without a complete line (absent, empty or torn), an index whose last entry names the rotated log, and a head that
 * still anchors the index line before that entry and the line before the checkpoint of the rotated log. That state is
 * intact exactly when every piece of it verifies: the head under the key, the entry chaining to the index line the head
 * anchors, the rotated log against that entry (its chain from the checkpoint before it, its record count, its first
 * record, its checkpoint line and the checkpoint's HMAC under the key), and the head anchoring the line before that
 * checkpoint. Every partial match is a break, and without the key the trail is {@code NotVerified}, never intact.
 * <p>
 * A break is reported by its reason: {@code link_broken} with the first broken line for a line that does not parse or
 * does not chain, {@code missing} for a missing log (also the rotated log a head anchors after a rename),
 * {@code reset} for a chain that starts over, {@code truncated}
 * for fewer records than anchored, {@code head_mismatch} for a head that does not verify or does not match. Without
 * the key nothing is anchored yet, and the trail is {@code NotVerified}; so is a base without any audit trail.
 * <p>
 * The verification reads without the lock and never writes. A rotation or an append between its reads of head,
 * index and log is detected by reading head and index again after the log; the reads are then repeated, and a break
 * is reported only when a second, consistent read shows it as well. It never throws: a trail it cannot read is
 * {@code NotVerified}.
 *
 * @since 0.1
 */
public final class AuditVerification implements AuditChainVerification {

    /** How often the reads are repeated when a rotation or an append came between them. */
    static final int READ_ATTEMPTS = 3;

    private static final String LOGS = "logs/";

    private final Path logFile;
    private final Path headFile;
    private final LockKey lockKey;
    private final AuditIndex index;
    private final EnrolmentKey integrityKey;
    private final Runnable afterLogRead;

    /**
     * @param base         the base directory of the runtime
     * @param integrityKey the enrolment integrity key, only read
     * @throws NullPointerException if an argument is {@code null}
     */
    public AuditVerification(Path base, EnrolmentKey integrityKey) {
        this(base, integrityKey, () -> {
            // nothing between the reads
        });
    }

    /**
     * @param base         the base directory of the runtime
     * @param integrityKey the enrolment integrity key, only read
     * @param afterLogRead run after the log was read and before head and index are read again, where a test lets
     *                     a rotation come between the reads
     */
    AuditVerification(Path base, EnrolmentKey integrityKey, Runnable afterLogRead) {
        this.lockKey = LockKey.auditLog(Objects.requireNonNull(base, "base"));
        this.logFile = lockKey.lockFile();
        this.headFile = AuditHead.headFile(base);
        this.index = new AuditIndex(base);
        this.integrityKey = Objects.requireNonNull(integrityKey, "integrityKey");
        this.afterLogRead = Objects.requireNonNull(afterLogRead, "afterLogRead");
    }

    /**
     * Verifies the head, the index and the current log from the last checkpoint.
     *
     * @return the outcome; never thrown
     */
    @Override
    public Outcome verify() {
        return verifyWith(false);
    }

    /**
     * Verifies, besides what {@link #verify()} verifies, every rotated file the audit index lists.
     *
     * @return the outcome; never thrown
     */
    public Outcome full() {
        return verifyWith(true);
    }

    private Outcome verifyWith(boolean full) {
        Optional<SecretKey> key;
        try {
            key = integrityKey.read();
        } catch (SecretStoreException e) {
            return new Outcome.NotVerified("integrity key unreadable (" + e.reason().code() + ")");
        }
        if (key.isEmpty()) {
            return new Outcome.NotVerified("no integrity key, nothing is anchored yet");
        }
        try {
            var outcome = evaluate(key.get(), stableState(), full);
            if (outcome instanceof Outcome.Broken) {
                outcome = evaluate(key.get(), stableState(), full);
            }
            return outcome;
        } catch (UncheckedIOException e) {
            return new Outcome.NotVerified("audit trail cannot be read: " + e.getMessage());
        }
    }

    /**
     * What one read of the trail found.
     *
     * @param head      the bytes of the head; empty if it does not exist
     * @param index     the audit index
     * @param log       the current log
     * @param logExists whether the current log exists
     */
    private record State(Optional<byte[]> head, AuditIndex.Snapshot index, JsonlStoreFile.Content log,
    boolean logExists) {
    }

    /** Reads head, index and log, and again head and index, until no rotation or append came between the reads. */
    private State stableState() {
        State state = null;
        for (var attempt = 0; attempt < READ_ATTEMPTS; attempt++) {
            var head = headBytes();
            var indexSnapshot = index.snapshot();
            var log = new JsonlStoreFile(logFile, lockKey).snapshot();
            var logExists = Files.exists(logFile, LinkOption.NOFOLLOW_LINKS);
            afterLogRead.run();
            state = new State(head, indexSnapshot, log, logExists);
            if (sameBytes(head, headBytes()) && indexSnapshot.headHash().equals(index.snapshot().headHash())) {
                return state;
            }
        }
        return state;
    }

    private Outcome evaluate(SecretKey key, State state, boolean full) {
        var indexSnapshot = state.index();
        if (state.head().isEmpty() && state.log().lines().isEmpty() && indexSnapshot.entries().isEmpty()
                && indexSnapshot.firstBrokenLine().isEmpty()) {
            return new Outcome.NotVerified("no audit chain");
        }
        if (indexSnapshot.firstBrokenLine().isPresent()) {
            var line = indexSnapshot.firstBrokenLine().getAsLong();
            return Outcome.Broken.linkBroken(line,
                    LOGS + AuditIndex.FILE_NAME + " line " + line + " does not parse or does not chain");
        }
        var head = parseHead(state.head());
        if (head.isEmpty()) {
            return Outcome.Broken.withoutLine(BreakReason.HEAD_MISMATCH,
                    LOGS + AuditHead.FILE_NAME + " is missing or does not parse");
        }
        if (!AuditHead.verifies(key, head.get())) {
            return Outcome.Broken.withoutLine(BreakReason.HEAD_MISMATCH,
                    LOGS + AuditHead.FILE_NAME + " does not verify under the integrity key");
        }
        var lines = state.log().lines();
        var pending = pendingRotation(lines, indexSnapshot);
        var entered = pending.flatMap(PendingRotation::entry);
        var renamed = pending.isEmpty() && lines.isEmpty() ? renamedRotation(head.get(), indexSnapshot)
                : Optional.<AuditIndexEntry>empty();
        var closing = entered.or(() -> renamed);
        var anchoredIndexHead = closing.map(AuditIndexEntry::prevHash).orElse(indexSnapshot.headHash());
        if (!head.get().indexHeadHash().equals(anchoredIndexHead)) {
            return Outcome.Broken.withoutLine(BreakReason.HEAD_MISMATCH, LOGS + AuditHead.FILE_NAME
                    + " does not anchor the last line of " + LOGS + AuditIndex.FILE_NAME);
        }
        var entries = indexSnapshot.entries();
        if (full) {
            var rotated = verifyRotated(key, entries);
            if (rotated.isPresent()) {
                return rotated.get();
            }
        }
        var firstPredecessor = checkpointBefore(entries, closing.isPresent() ? entries.size() - 1 : entries.size());
        if (renamed.isPresent()) {
            return verifyRenamed(key, renamed.get(), head.get(), firstPredecessor);
        }
        if (pending.isPresent()) {
            var closed = verifyClosed(key, lines, pending.get(), firstPredecessor);
            if (closed.isPresent()) {
                return closed.get();
            }
            return verifyCurrent(true, lines.subList(0, lines.size() - 1), head.get(), firstPredecessor);
        }
        return verifyCurrent(state.logExists(), lines, head.get(), firstPredecessor);
    }

    /**
     * A rotation interrupted after its checkpoint: the last line of the current log is a checkpoint, and the head
     * still anchors the line before it.
     *
     * @param checkpoint the checkpoint record, the last line of the current log
     * @param entry      the last index entry when it names that checkpoint line; empty when the entry was not written
     */
    private record PendingRotation(AuditRecord checkpoint, Optional<AuditIndexEntry> entry) {
    }

    /**
     * @return the hash the first record of a log chains to when the given number of index entries precede it: the
     *         {@code checkpoint_hash} of the last of them, {@link AuditCodec#GENESIS_HASH} when none does
     */
    private static String checkpointBefore(List<AuditIndexEntry> entries, int preceding) {
        return preceding == 0 ? AuditCodec.GENESIS_HASH : entries.get(preceding - 1).checkpointHash();
    }

    /** @return the pending rotation of the current log; empty when its last line is not a checkpoint */
    private Optional<PendingRotation> pendingRotation(List<JsonlStoreFile.Line> lines, AuditIndex.Snapshot snapshot) {
        if (lines.isEmpty()) {
            return Optional.empty();
        }
        var lastLine = lines.getLast().content();
        AuditRecord last;
        try {
            last = AuditCodec.readRecord(logFile, lastLine);
        } catch (StoreRefusal _) {
            return Optional.empty();
        }
        if (!last.isCheckpoint()) {
            return Optional.empty();
        }
        var lastHash = AuditCodec.sha256Hex(lastLine);
        return Optional.of(new PendingRotation(last,
                snapshot.last().filter(entry -> entry.checkpointHash().equals(lastHash))));
    }

    /**
     * A rotation stopped after its rename, before the first record of the new log: the current log holds no complete
     * line, and the last index entry chains to the index line the head anchors, so the head still anchors the log
     * that entry names. Whether that log is the one the head anchors is left to {@link #verifyRenamed}.
     *
     * @return the last index entry when it chains to the index line the head anchors; empty otherwise
     */
    private static Optional<AuditIndexEntry> renamedRotation(AuditHeadRecord head, AuditIndex.Snapshot snapshot) {
        return snapshot.last().filter(entry -> entry.prevHash().equals(head.indexHeadHash()));
    }

    /**
     * Verifies a trail stopped between a rotation's rename and the first record of the new log. It is intact exactly
     * when the rotated file the last index entry names verifies against that entry, its checkpoint under the key
     * included, and the head anchors the line before that checkpoint: its record count, its hash and its event id.
     * Anything else is a break: a rotated file that is gone is {@code missing}, one that does not match its entry or
     * whose checkpoint does not verify is reported as {@code full()} reports it, and a head that anchors another line
     * is {@code head_mismatch}.
     */
    private Outcome verifyRenamed(SecretKey key, AuditIndexEntry entry, AuditHeadRecord head,
            String firstPredecessor) {
        var name = LOGS + entry.file();
        var file = logFile.resolveSibling(entry.file());
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Outcome.Broken.withoutLine(BreakReason.MISSING,
                    name + " is missing, the rotated log its head anchors");
        }
        var lines = new JsonlStoreFile(file, lockKey).snapshot().lines();
        var rotated = verifyRotatedFile(key, entry, name, lines, firstPredecessor);
        if (rotated.isPresent()) {
            return rotated.get();
        }
        var count = lines.size() - 1;
        var anchored = lines.get(count - 1).content();
        if (head.recordCount() != count || !head.headHash().equals(AuditCodec.sha256Hex(anchored))
                || !AuditCodec.readRecord(file, anchored).eventId().equals(head.lastEventId())) {
            return Outcome.Broken.withoutLine(BreakReason.HEAD_MISMATCH, LOGS + AuditHead.FILE_NAME
                    + " does not anchor line " + count + " of " + name + ", the line before its checkpoint");
        }
        return new Outcome.Intact();
    }

    /**
     * Verifies a current log closed by its checkpoint as a rotated file: against the index entry that names it, or,
     * before the entry was written, its chain and its checkpoint.
     */
    private Optional<Outcome> verifyClosed(SecretKey key, List<JsonlStoreFile.Line> lines, PendingRotation pending,
            String firstPredecessor) {
        var name = LOGS + AuditHeadRecord.FILE;
        if (pending.entry().isPresent()) {
            return verifyRotatedFile(key, pending.entry().get(), name, lines, firstPredecessor);
        }
        if (walk(name, lines, firstPredecessor) instanceof Walk.Broken(var broken)) {
            return Optional.of(broken);
        }
        if (!AuditRotation.checkpointVerifies(key, pending.checkpoint(), lines)) {
            var count = lines.size();
            return Optional.of(Outcome.Broken.linkBroken(count,
                    name + " line " + count + " is a checkpoint that does not verify"));
        }
        return Optional.empty();
    }

    private Outcome verifyCurrent(boolean logExists, List<JsonlStoreFile.Line> lines, AuditHeadRecord head,
            String firstPredecessor) {
        var name = LOGS + AuditHeadRecord.FILE;
        if (!logExists) {
            return head.recordCount() == 0 ? new Outcome.Intact()
                    : Outcome.Broken.withoutLine(BreakReason.MISSING, name + " is missing");
        }
        var chain = walk(name, lines, firstPredecessor);
        if (chain instanceof Walk.Broken(var broken)) {
            return broken;
        }
        var count = lines.size();
        if (count == 0 && head.recordCount() > 0) {
            return Outcome.Broken.withoutLine(BreakReason.RESET,
                    name + " is empty, its head anchors " + head.recordCount() + " records");
        }
        if (count < head.recordCount()) {
            return Outcome.Broken.withoutLine(BreakReason.TRUNCATED,
                    name + " holds " + count + " records, its head anchors " + head.recordCount());
        }
        if (count > head.recordCount()) {
            return Outcome.Broken.withoutLine(BreakReason.HEAD_MISMATCH,
                    name + " holds " + count + " records, more than the " + head.recordCount() + " its head anchors");
        }
        if (count == 0) {
            return new Outcome.Intact();
        }
        var last = (Walk.Intact) chain;
        if (!head.headHash().equals(last.lastHash()) || !last.lastRecord().eventId().equals(head.lastEventId())) {
            return Outcome.Broken.withoutLine(BreakReason.HEAD_MISMATCH,
                    name + " line " + count + " is not the line its head anchors");
        }
        return new Outcome.Intact();
    }

    /** Verifies the rotated files in the order of the index; a purged file is accounted for by its entry. */
    private Optional<Outcome> verifyRotated(SecretKey key, List<AuditIndexEntry> entries) {
        var predecessor = AuditCodec.GENESIS_HASH;
        for (var entry : entries) {
            var file = logFile.resolveSibling(entry.file());
            if (Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
                var outcome = verifyRotatedFile(key, entry, LOGS + entry.file(),
                        new JsonlStoreFile(file, lockKey).snapshot().lines(), predecessor);
                if (outcome.isPresent()) {
                    return outcome;
                }
            }
            predecessor = entry.checkpointHash();
        }
        return Optional.empty();
    }

    private Optional<Outcome> verifyRotatedFile(SecretKey key, AuditIndexEntry entry, String name,
            List<JsonlStoreFile.Line> lines, String predecessor) {
        var chain = walk(name, lines, predecessor);
        if (chain instanceof Walk.Broken(var broken)) {
            return Optional.of(broken);
        }
        var count = lines.size();
        if (count < entry.recordCount()) {
            return Optional.of(Outcome.Broken.withoutLine(BreakReason.TRUNCATED,
                    name + " holds " + count + " records, its index entry names " + entry.recordCount()));
        }
        if (count > entry.recordCount()) {
            var line = entry.recordCount() + 1;
            return Optional.of(Outcome.Broken.linkBroken(line, name + " line " + line + " follows its checkpoint"));
        }
        var last = (Walk.Intact) chain;
        var checkpoint = last.lastRecord();
        var firstRecord = AuditCodec.readRecord(logFile, lines.getFirst().content());
        if (!last.lastHash().equals(entry.checkpointHash()) || !checkpoint.isCheckpoint()
                || !checkpoint.eventId().equals(entry.lastEventId())
                || !firstRecord.eventId().equals(entry.firstEventId())
                || !AuditRotation.checkpointVerifies(key, checkpoint, lines)) {
            return Optional.of(Outcome.Broken.linkBroken(count,
                    name + " line " + count + " is not the checkpoint its index entry names"));
        }
        return Optional.empty();
    }

    /** The outcome of walking the chain of one log file. */
    private sealed interface Walk {

        /**
         * @param lastHash   the SHA-256 of the last line; the predecessor when the file holds no line
         * @param lastRecord the last record; {@code null} when the file holds no line
         */
        record Intact(String lastHash, AuditRecord lastRecord) implements Walk {
        }

        /** @param broken the break */
        record Broken(Outcome.Broken broken) implements Walk {
        }
    }

    /**
     * Walks the lines of one log file: the first chains to the predecessor, every other one to the line before it.
     * A first line that starts at {@link AuditCodec#GENESIS_HASH} where a predecessor is due starts the chain over.
     */
    private Walk walk(String name, List<JsonlStoreFile.Line> lines, String predecessor) {
        var expected = predecessor;
        AuditRecord last = null;
        for (var line : lines) {
            var bytes = line.content();
            AuditRecord auditRecord;
            try {
                auditRecord = AuditCodec.readRecord(logFile, bytes);
            } catch (StoreRefusal _) {
                return new Walk.Broken(Outcome.Broken.linkBroken(line.number(),
                        name + " line " + line.number() + " does not parse"));
            }
            if (!auditRecord.prevHash().equals(expected)) {
                if (line.number() == 1 && AuditCodec.GENESIS_HASH.equals(auditRecord.prevHash())) {
                    return new Walk.Broken(Outcome.Broken.withoutLine(BreakReason.RESET,
                            name + " starts a new chain where the audit index continues it"));
                }
                return new Walk.Broken(Outcome.Broken.linkBroken(line.number(),
                        name + " line " + line.number() + " does not chain to the line before it"));
            }
            expected = AuditCodec.sha256Hex(bytes);
            last = auditRecord;
        }
        return new Walk.Intact(expected, last);
    }

    private Optional<AuditHeadRecord> parseHead(Optional<byte[]> bytes) {
        try {
            return bytes.map(document -> AuditCodec.readHead(headFile, document));
        } catch (StoreRefusal _) {
            return Optional.empty();
        }
    }

    private Optional<byte[]> headBytes() {
        try {
            return Optional.of(Files.readAllBytes(headFile));
        } catch (NoSuchFileException _) {
            return Optional.empty();
        } catch (IOException e) {
            throw new UncheckedIOException("The audit head cannot be read: " + headFile, e);
        }
    }

    private static boolean sameBytes(Optional<byte[]> first, Optional<byte[]> second) {
        return first.isPresent() == second.isPresent()
                && (first.isEmpty() || Arrays.equals(first.get(), second.get()));
    }
}
