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

import java.io.EOFException;
import java.io.IOException;
import java.io.Serial;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;
import de.planmarshall.runtime.log.RuntimeLogMessages.WARN;

/**
 * The rotation of the audit log (PM-SEC-6; Store Schemas, Security &amp; Transition Audit Log, Checkpoint records and
 * Audit Index): when the current log reaches its maximum size, or its first record its maximum age, the log is closed
 * and renamed, and the next record starts a new log that chains to it.
 * <p>
 * A rotation runs under the audit lock inside the writer's append, before the record is appended, and only when the
 * {@link CloseCheck} of the caller lets the log be closed (the audit head refuses a log that no longer holds the line
 * it anchors, and a head that does not verify), in this order:
 * <ol>
 * <li>the {@value AuditRecord#EVENT_CHECKPOINT} record ({@code security}, scope {@code machine}, details
 * {@value #DETAIL_RECORD_COUNT}, {@value #DETAIL_HEAD_HASH} and {@value #DETAIL_HMAC}) is appended through
 * {@link AuditLog#appendCheckpoint(LockTransaction, AuditRecord)};</li>
 * <li>an entry of the audit index names the rotated file, its first and its last record, its number of records and
 * the hash of the checkpoint line;</li>
 * <li>the log is renamed to {@code audit-<first_event_id>.jsonl} by an atomic move, and its directory is forced to
 * disk.</li>
 * </ol>
 * The new log is empty, so the writer chains its first record to the {@code checkpoint_hash} of the last index entry,
 * the checkpoint line; because the entry is written before the rename, that link survives a stop after the rename.
 * <p>
 * A log whose last line is a checkpoint is a <em>pending rotation</em>: one a stop or a failure interrupted after its
 * checkpoint. No record may follow that checkpoint, so the anchor does not let the record be appended while the
 * rotation stays pending ({@link #pending(AuditLog)}). The next append with the key completes it without a second
 * checkpoint, but only over a checkpoint that verifies ({@code checkpointVerifies}):
 * it counts the records before it, names the hash of the line before it, chains to that line, and its HMAC verifies
 * under the key. The log then gets its index entry, unless the last entry already names that line, and is renamed. A
 * last line that merely has the event {@value AuditRecord#EVENT_CHECKPOINT} proves nothing, since anyone who can write
 * the log can append one after cutting its tail; so a checkpoint that does not verify, and every pending checkpoint
 * while the key is absent or unreadable ({@link #refusePending(AuditLog)}), is never completed: the append is refused
 * with the reason {@value #CHECKPOINT_UNVERIFIABLE}, the log stays as it is, and the verification reports it. A due log
 * that the last index entry already names as its rotated file is never entered a second time.
 * <p>
 * The refusal holds while the checkpoint is the last complete line. {@link AuditLog#append} cuts a torn tail of the
 * log before it calls the anchor, so a torn tail behind a checkpoint, which no writer of the trail leaves since none
 * writes after a checkpoint, is cut first and its {@code jsonl_tail_repaired} record follows the checkpoint: the log is
 * then no longer pending, and the append is not refused. The integrity of the trail is not affected: that record is
 * chained, a checkpoint that does not verify still gets no index entry and no rename, no head is written over a log
 * that no longer holds the anchored line, and the verification still reports a cut. A checkpoint whose index entry is
 * already written is the one case with a lasting consequence: the log the entry names as rotated stays the current
 * log, and the head of the append, counted over it, anchors the index with that entry. The next start reports the
 * current log as {@code reset} after a first rotation, its first record chaining to genesis, and as {@code link_broken}
 * at line 1 after a later one, its first record chaining to the checkpoint before; and every later due append logs a
 * rotation failure, since the log is already entered in the index, and never rotates it.
 * <p>
 * A stop after the rename, before the first record of the new log, leaves no current log and a head that anchors the
 * rotated log; the verification reports it intact when the rotated log and its checkpoint verify against the entry,
 * and the next append writes the head over the new log.
 * <p>
 * The HMAC of a checkpoint is the HMAC-SHA-256 under the enrolment integrity key over the UTF-8 bytes of
 * {@code {"record_count":<record_count>,"head_hash":"<head_hash>"}} ({@link #checkpointHmac(SecretKey, long, String)}).
 * A tail repair of the index during a rotation stays owed beside the index until the log is renamed, and is then
 * handed into the new log through {@link AuditLog#appendOwedRepair}. These two members of {@link AuditLog}, neither
 * of which calls the anchor, are the only paths by which this package writes into the log.
 *
 * @since 0.1
 */
public final class AuditRotation {

    /** The detail of a checkpoint that counts the records of the rotated log before the checkpoint. */
    public static final String DETAIL_RECORD_COUNT = "record_count";

    /** The detail of a checkpoint that holds the SHA-256 of the last line before it. */
    public static final String DETAIL_HEAD_HASH = "head_hash";

    /** The detail of a checkpoint that holds the HMAC over its record count and head hash. */
    public static final String DETAIL_HMAC = "hmac";

    /**
     * The reason a pending rotation is not completed: its checkpoint does not verify under the integrity key, or the
     * key is absent or unreadable, so it cannot be verified.
     */
    static final String CHECKPOINT_UNVERIFIABLE = "checkpoint_unverifiable";

    private static final CuiLogger LOGGER = new CuiLogger(AuditRotation.class);

    private static final byte NEWLINE = '\n';
    private static final int SCAN_CHUNK = 8192;

    /** Owes a tail repair of the index during a rotation until the log is renamed. */
    private static final AuditSink DEFERRED = _ -> {
        throw new UncheckedIOException(
                new IOException("handed over into the new audit log once the rotation is complete"));
    };

    private final AuditIndex index;
    private final Limits limits;
    private final Clock clock;
    private final String osPrincipal;

    /**
     * The limits whose first reached rotates the log.
     *
     * @param maxBytes the size of the log in bytes at which it is rotated
     * @param maxAge   the age of the first record of the log at which it is rotated
     * @since 0.1
     */
    public record Limits(long maxBytes, Duration maxAge) {

        /** The default maximum size, 64 MiB. */
        public static final long DEFAULT_MAX_BYTES = 64L * 1024 * 1024;

        /** The default maximum age, 30 days. */
        public static final Duration DEFAULT_MAX_AGE = Duration.ofDays(30);

        /** The default limits: 64 MiB and 30 days. */
        public static final Limits DEFAULTS = new Limits(DEFAULT_MAX_BYTES, DEFAULT_MAX_AGE);

        /**
         * @throws NullPointerException     if the maximum age is {@code null}
         * @throws IllegalArgumentException if a limit is not positive
         */
        public Limits {
            Objects.requireNonNull(maxAge, "maxAge");
            if (maxBytes < 1 || maxAge.isNegative() || maxAge.isZero()) {
                throw new IllegalArgumentException(
                        "The limits of the audit rotation are positive: %d bytes, %s".formatted(maxBytes, maxAge));
            }
        }
    }

    /**
     * @param index       the audit index the rotated files are entered in
     * @param limits      the limits whose first reached rotates the log
     * @param clock       the clock of the age and of the checkpoint record
     * @param osPrincipal the OS principal of the runtime, the actor of the checkpoint record
     * @throws NullPointerException if an argument is {@code null}
     */
    public AuditRotation(AuditIndex index, Limits limits, Clock clock, String osPrincipal) {
        this.index = Objects.requireNonNull(index, "index");
        this.limits = Objects.requireNonNull(limits, "limits");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.osPrincipal = Objects.requireNonNull(osPrincipal, "osPrincipal");
    }

    /** @return the limits of the rotation */
    public Limits limits() {
        return limits;
    }

    /**
     * The check a rotation runs before it signs a checkpoint: whether the current log may be closed. A checkpoint
     * signs the record count and the last line of the log, so it must never be written over a log that no longer
     * holds what its head anchors.
     *
     * @since 0.1
     */
    @FunctionalInterface
    public interface CloseCheck {

        /**
         * @param transaction   the writer's transaction, which holds the audit lock
         * @param log           the audit log, due for rotation and not pending
         * @param indexSnapshot the audit index as read under the audit lock
         * @return the reason code why the log may not be closed; empty when it may
         * @throws UncheckedIOException if what the check reads cannot be read
         */
        Optional<String> refusal(LockTransaction transaction, AuditLog log, AuditIndex.Snapshot indexSnapshot);
    }

    /**
     * A pending rotation that is not completed because its checkpoint does not verify, or cannot be verified without
     * the key. It stays pending, so the record of the append is refused: no record may follow a checkpoint.
     *
     * @since 0.1
     */
    static final class PendingRefusal extends UncheckedIOException {

        @Serial
        private static final long serialVersionUID = 1L;

        /** The reason code. */
        private final String reason;

        /**
         * @param reason the reason code
         * @param detail what was refused
         */
        PendingRefusal(String reason, String detail) {
            super(detail, new IOException(detail));
            this.reason = reason;
        }

        /** @return the reason code, logged in place of the detail */
        String reason() {
            return reason;
        }
    }

    /**
     * Rotates the log when a limit is reached and the check lets it be closed, or completes a pending rotation whose
     * checkpoint verifies. A log that does not exist, holds no complete line, or whose first line cannot be read is not
     * rotated. A due log the check refuses is not rotated either: the refusal is logged with its reason code, and the
     * next append runs the check again.
     *
     * @param transaction the writer's transaction, which holds the audit lock and has repaired the tail of the log
     * @param log         the audit log
     * @param key         the enrolment integrity key the checkpoint is signed with, and a pending checkpoint verified
     *                    under
     * @param check       the check run before a checkpoint is signed; a pending rotation is completed without it,
     *                    since its checkpoint verifies under the key
     * @return whether a rotation was carried out or completed
     * @throws PendingRefusal       if the last line of the log is a checkpoint that does not verify; the log stays
     *                              pending
     * @throws UncheckedIOException if the log or the index cannot be read or written, the log cannot be renamed, the
     *                              check cannot read what it checks, or the due log is already named by the last
     *                              index entry while records follow its checkpoint
     * @throws StoreRefusal         if the intent file of the log or of the index is unreadable
     */
    public boolean rotateIfDue(LockTransaction transaction, AuditLog log, SecretKey key, CloseCheck check) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(key, "key");
        Objects.requireNonNull(check, "check");
        var logFile = log.logFile();
        var edges = edges(logFile);
        if (edges.isEmpty()) {
            return false;
        }
        var first = decode(logFile, edges.get().first());
        if (first.isEmpty()) {
            return false;
        }
        if (completePending(transaction, log, edges.get(), first.get(), key)) {
            return true;
        }
        if (!due(edges.get().size(), first.get())) {
            return false;
        }
        var rotatedFile = AuditIndexEntry.rotatedFileName(first.get().eventId());
        var indexSnapshot = index.read(transaction);
        if (indexSnapshot.last().filter(entry -> entry.file().equals(rotatedFile)).isPresent()) {
            throw new UncheckedIOException(new IOException("The audit log is already entered in the audit index as "
                    + rotatedFile + " while records follow its checkpoint; it is not entered a second time"));
        }
        var refusal = check.refusal(transaction, log, indexSnapshot);
        if (refusal.isPresent()) {
            LOGGER.warn(WARN.AUDIT_ROTATION_FAILED, refusal.get());
            return false;
        }
        var lastLine = edges.get().last();
        var recordCount = log.recordCount(transaction);
        var headHash = AuditCodec.sha256Hex(lastLine);
        var checkpoint = AuditRecord.builder()
                .eventId(TransactionId.generate(clock).value())
                .prevHash(AuditCodec.GENESIS_HASH)
                .timestamp(clock.instant())
                .category(AuditRecord.Category.SECURITY)
                .event(AuditRecord.EVENT_CHECKPOINT)
                .scopeType(AuditRecord.Scope.MACHINE)
                .actor(osPrincipal)
                .outcome(AuditRecord.Outcome.SUCCEEDED)
                .details(Map.of(DETAIL_RECORD_COUNT, recordCount, DETAIL_HEAD_HASH, headHash, DETAIL_HMAC,
                        checkpointHmac(key, recordCount, headHash)))
                .build();
        var checkpointHash = log.appendCheckpoint(transaction, checkpoint);
        close(transaction, log, first.get().eventId(), checkpoint.eventId(), recordCount + 1, checkpointHash);
        return true;
    }

    /**
     * Refuses the append while a rotation is pending and the key is absent or unreadable: without the key its
     * checkpoint cannot be verified, so it is not completed, and no record may follow it. A log that is not pending is
     * left to the append.
     *
     * @param log the audit log
     * @throws PendingRefusal       if the last complete line of the log is a checkpoint; the log stays pending
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public static void refusePending(AuditLog log) {
        if (pending(log)) {
            throw new PendingRefusal(CHECKPOINT_UNVERIFIABLE, "The audit log is closed by a checkpoint that cannot be "
                    + "verified without the integrity key; the rotation is not completed");
        }
    }

    /**
     * Whether a rotation is pending: the last complete line of the log is a checkpoint. While it is, no record may be
     * appended to the log.
     *
     * @param log the audit log
     * @return whether the log is closed by its checkpoint and not yet renamed
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public static boolean pending(AuditLog log) {
        var logFile = log.logFile();
        return edges(logFile).flatMap(edges -> decode(logFile, edges.last())).filter(AuditRecord::isCheckpoint)
                .isPresent();
    }

    /**
     * @param key         the enrolment integrity key
     * @param recordCount the number of records of the rotated log before its checkpoint
     * @param headHash    the SHA-256 of the last line before the checkpoint
     * @return the HMAC of the checkpoint, in lower-case hex
     */
    public static String checkpointHmac(SecretKey key, long recordCount, String headHash) {
        Objects.requireNonNull(headHash, "headHash");
        // Concatenation, not String.formatted: %d formats with the default locale and could write non-ASCII digits.
        var content = "{\"record_count\":" + Long.toString(recordCount) + ",\"head_hash\":\"" + headHash + "\"}";
        return EnrolmentHmac.hmac(key, content.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * Whether a checkpoint closes the lines it ends: it counts the records before it, names the hash of the line
     * before it and chains to that line, and its HMAC verifies under the key. A checkpoint with no record before it
     * never verifies, since a rotation closes only a log that holds one.
     *
     * @param key        the enrolment integrity key
     * @param checkpoint the record of the last of the lines
     * @param lines      the lines of the log the checkpoint closes, the checkpoint the last of them
     * @return whether the checkpoint verifies
     */
    static boolean checkpointVerifies(SecretKey key, AuditRecord checkpoint, List<JsonlStoreFile.Line> lines) {
        var details = checkpoint.details();
        var count = lines.size() - 1L;
        if (!checkpoint.isCheckpoint() || count < 1 || !Long.valueOf(count).equals(details.get(DETAIL_RECORD_COUNT))
                || !(details.get(DETAIL_HEAD_HASH) instanceof String headHash)
                || !(details.get(DETAIL_HMAC) instanceof String hmac)) {
            return false;
        }
        var lineBefore = AuditCodec.sha256Hex(lines.get(lines.size() - 2).content());
        return headHash.equals(lineBefore) && lineBefore.equals(checkpoint.prevHash()) && MessageDigest.isEqual(
                checkpointHmac(key, count, headHash).getBytes(StandardCharsets.US_ASCII),
                hmac.getBytes(StandardCharsets.US_ASCII));
    }

    private boolean due(long size, AuditRecord first) {
        return size >= limits.maxBytes()
                || !first.timestamp().plus(limits.maxAge()).isAfter(clock.instant());
    }

    /**
     * Completes a rotation whose checkpoint is the last line of the log and verifies under the key: enters it in the
     * index unless the last entry already names that line, and renames it. A checkpoint that does not verify is
     * refused before the index or the log is touched, also when the index already names it.
     *
     * @return whether the log was closed by a checkpoint
     * @throws PendingRefusal if the checkpoint does not verify
     */
    private boolean completePending(LockTransaction transaction, AuditLog log, Edges edges, AuditRecord first,
            SecretKey key) {
        var logFile = log.logFile();
        var last = decode(logFile, edges.last());
        if (last.isEmpty() || !last.get().isCheckpoint()) {
            return false;
        }
        var lines = log.read(transaction).lines();
        var checkpoint = lines.isEmpty() ? Optional.<AuditRecord>empty()
                : decode(logFile, lines.getLast().content());
        if (checkpoint.isEmpty() || !checkpointVerifies(key, checkpoint.get(), lines)) {
            throw new PendingRefusal(CHECKPOINT_UNVERIFIABLE, "The audit log is closed by a checkpoint that does not "
                    + "verify under the integrity key; the rotation is not completed");
        }
        var checkpointHash = AuditCodec.sha256Hex(lines.getLast().content());
        var recordCount = lines.size();
        var entered = index.read(transaction).lastCheckpointHash().filter(checkpointHash::equals).isPresent();
        if (entered) {
            rename(transaction, log, first.eventId(), recordCount);
        } else {
            close(transaction, log, first.eventId(), checkpoint.get().eventId(), recordCount, checkpointHash);
        }
        return true;
    }

    /** Enters the closed log in the index and renames it. */
    private void close(LockTransaction transaction, AuditLog log, String firstEventId, String checkpointEventId,
            long recordCount, String checkpointHash) {
        index.append(transaction, new AuditIndexEntry(AuditCodec.GENESIS_HASH,
                AuditIndexEntry.rotatedFileName(firstEventId), firstEventId, checkpointEventId, recordCount,
                checkpointHash), DEFERRED);
        rename(transaction, log, firstEventId, recordCount);
    }

    /** Renames the closed log, then hands the owed tail repairs of the index into the new log. */
    private void rename(LockTransaction transaction, AuditLog log, String firstEventId, long recordCount) {
        var logFile = log.logFile();
        var rotated = logFile.resolveSibling(AuditIndexEntry.rotatedFileName(firstEventId));
        try {
            Files.move(logFile, rotated, StandardCopyOption.ATOMIC_MOVE);
            try (var directory = FileChannel.open(logFile.getParent(), StandardOpenOption.READ)) {
                directory.force(true);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("The audit log cannot be renamed to " + rotated, e);
        }
        LOGGER.info(INFO.AUDIT_LOG_ROTATED, rotated.getFileName(), recordCount);
        index.handOverRepairs(transaction, owed -> log.appendOwedRepair(transaction, owed));
    }

    private static Optional<AuditRecord> decode(Path logFile, byte[] line) {
        try {
            return Optional.of(AuditCodec.readRecord(logFile, line));
        } catch (StoreRefusal _) {
            return Optional.empty();
        }
    }

    /**
     * The first and the last complete line of the log and its size.
     *
     * @param first the first complete line, without its newline
     * @param last  the last complete line, without its newline
     * @param size  the size of the log in bytes
     */
    private record Edges(byte[] first, byte[] last, long size) {

        @Override
        public boolean equals(Object other) {
            return other instanceof Edges(byte[] otherFirst, byte[] otherLast, long otherSize) && size == otherSize
                    && Arrays.equals(first, otherFirst) && Arrays.equals(last, otherLast);
        }

        @Override
        public int hashCode() {
            return Objects.hash(Arrays.hashCode(first), Arrays.hashCode(last), size);
        }

        @Override
        public String toString() {
            return "Edges[first=" + first.length + " bytes, last=" + last.length + " bytes, size=" + size + "]";
        }
    }

    /** Reads the first and the last complete line; empty if the log does not exist or holds no complete line. */
    private static Optional<Edges> edges(Path logFile) {
        if (!Files.exists(logFile, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try (var channel = FileChannel.open(logFile, StandardOpenOption.READ)) {
            var size = channel.size();
            var end = afterLastNewline(channel, size);
            if (end == 0) {
                return Optional.empty();
            }
            var start = afterLastNewline(channel, end - 1);
            var last = readFully(channel, start, Math.toIntExact(end - 1 - start));
            var firstEnd = firstNewline(channel, end);
            var first = readFully(channel, 0, Math.toIntExact(firstEnd));
            return Optional.of(new Edges(first, last, size));
        } catch (IOException e) {
            throw new UncheckedIOException("The audit log cannot be read: " + logFile, e);
        }
    }

    /** @return the position of the first newline before the limit, which holds one */
    private static long firstNewline(FileChannel channel, long limit) throws IOException {
        var chunkStart = 0L;
        while (chunkStart < limit) {
            var chunk = readFully(channel, chunkStart, (int) Math.min(SCAN_CHUNK, limit - chunkStart));
            for (var i = 0; i < chunk.length; i++) {
                if (chunk[i] == NEWLINE) {
                    return chunkStart + i;
                }
            }
            chunkStart += chunk.length;
        }
        return limit - 1;
    }

    /** @return the position after the last newline before the limit; 0 if there is none */
    private static long afterLastNewline(FileChannel channel, long limit) throws IOException {
        var chunkEnd = limit;
        while (chunkEnd > 0) {
            var chunkStart = Math.max(0, chunkEnd - SCAN_CHUNK);
            var chunk = readFully(channel, chunkStart, (int) (chunkEnd - chunkStart));
            for (var i = chunk.length - 1; i >= 0; i--) {
                if (chunk[i] == NEWLINE) {
                    return chunkStart + i + 1;
                }
            }
            chunkEnd = chunkStart;
        }
        return 0;
    }

    private static byte[] readFully(FileChannel channel, long position, int length) throws IOException {
        var buffer = ByteBuffer.allocate(length);
        while (buffer.hasRemaining()) {
            if (channel.read(buffer, position + buffer.position()) < 0) {
                throw new EOFException("The audit log ended before byte " + (position + length));
            }
        }
        return buffer.array();
    }
}
