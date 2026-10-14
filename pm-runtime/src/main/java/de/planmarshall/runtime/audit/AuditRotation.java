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
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import javax.crypto.SecretKey;

import de.cuioss.tools.logging.CuiLogger;
import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.AuditLog;
import de.planmarshall.core.store.AuditRecord;
import de.planmarshall.core.store.StoreRefusal;
import de.planmarshall.core.store.TransactionId;
import de.planmarshall.runtime.enrolment.EnrolmentHmac;
import de.planmarshall.runtime.log.RuntimeLogMessages.INFO;

/**
 * The rotation of the audit log (PM-SEC-6; Store Schemas, Security &amp; Transition Audit Log, Checkpoint records and
 * Audit Index): when the current log reaches its maximum size, or its first record its maximum age, the log is closed
 * and renamed, and the next record starts a new log that chains to it.
 * <p>
 * A rotation runs under the audit lock inside the writer's append, before the record is appended, in this order:
 * <ol>
 * <li>the {@value AuditRecord#EVENT_CHECKPOINT} record ({@code security}, scope {@code machine}, details
 * {@value #DETAIL_RECORD_COUNT}, {@value #DETAIL_HEAD_HASH} and {@value #DETAIL_HMAC}) is appended through
 * {@link AuditLog#appendCheckpoint(LockTransaction, AuditRecord)}, the one path by which this package writes into the
 * log;</li>
 * <li>an entry of the audit index names the rotated file, its first and its last record, its number of records and
 * the hash of the checkpoint line;</li>
 * <li>the log is renamed to {@code audit-<first_event_id>.jsonl} by an atomic move, and its directory is forced to
 * disk.</li>
 * </ol>
 * The new log is empty, so the writer chains its first record to the {@code checkpoint_hash} of the last index entry,
 * the checkpoint line; because the entry is written before the rename, that link survives a stop after the rename. A
 * rotation stopped after the checkpoint is completed by the next append without a second checkpoint: a log whose last
 * line is a checkpoint gets its index entry, unless the last entry already names that line, and is renamed.
 * <p>
 * The HMAC of a checkpoint is the HMAC-SHA-256 under the enrolment integrity key over the UTF-8 bytes of
 * {@code {"record_count":<record_count>,"head_hash":"<head_hash>"}} ({@link #checkpointHmac(SecretKey, long, String)}).
 * A tail repair of the index during a rotation stays owed beside the index until the log is renamed, and is then
 * handed into the new log.
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
     * Rotates the log when a limit is reached, or completes a rotation a stop interrupted after its checkpoint. A log
     * that does not exist, holds no complete line, or whose first line cannot be read is not rotated.
     *
     * @param transaction the writer's transaction, which holds the audit lock and has repaired the tail of the log
     * @param log         the audit log
     * @param key         the enrolment integrity key the checkpoint is signed with
     * @return whether a rotation was carried out or completed
     * @throws UncheckedIOException if the log or the index cannot be read or written, or the log cannot be renamed
     * @throws StoreRefusal         if the intent file of the log or of the index is unreadable
     */
    public boolean rotateIfDue(LockTransaction transaction, AuditLog log, SecretKey key) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(key, "key");
        var logFile = log.logFile();
        var edges = edges(logFile);
        if (edges.isEmpty()) {
            return false;
        }
        var first = decode(logFile, edges.get().first());
        if (first.isEmpty()) {
            return false;
        }
        var lastLine = edges.get().last();
        var last = decode(logFile, lastLine);
        if (last.isPresent() && last.get().isCheckpoint()) {
            complete(transaction, log, first.get().eventId(), last.get().eventId(), AuditCodec.sha256Hex(lastLine));
            return true;
        }
        if (!due(edges.get().size(), first.get())) {
            return false;
        }
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
     * @param key         the enrolment integrity key
     * @param recordCount the number of records of the rotated log before its checkpoint
     * @param headHash    the SHA-256 of the last line before the checkpoint
     * @return the HMAC of the checkpoint, in lower-case hex
     */
    public static String checkpointHmac(SecretKey key, long recordCount, String headHash) {
        Objects.requireNonNull(headHash, "headHash");
        var content = "{\"record_count\":%d,\"head_hash\":\"%s\"}".formatted(recordCount, headHash);
        return EnrolmentHmac.hmac(key, content.getBytes(StandardCharsets.UTF_8));
    }

    private boolean due(long size, AuditRecord first) {
        return size >= limits.maxBytes()
                || !first.timestamp().plus(limits.maxAge()).isAfter(clock.instant());
    }

    /** Completes a rotation whose checkpoint is the last line of the log. */
    private void complete(LockTransaction transaction, AuditLog log, String firstEventId, String checkpointEventId,
            String checkpointHash) {
        var entered = index.read(transaction).lastCheckpointHash().filter(checkpointHash::equals).isPresent();
        if (entered) {
            rename(transaction, log, firstEventId, log.recordCount(transaction));
        } else {
            close(transaction, log, firstEventId, checkpointEventId, log.recordCount(transaction), checkpointHash);
        }
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
        index.handOverRepairs(transaction, event -> log.append(transaction, toRecord(event)));
    }

    private AuditRecord toRecord(AuditEvent event) {
        return AuditRecord.builder()
                .eventId(TransactionId.generate(clock).value())
                .prevHash(AuditCodec.GENESIS_HASH)
                .timestamp(clock.instant())
                .category(AuditRecord.Category.fromWireName(event.category()).orElse(AuditRecord.Category.STORE))
                .event(event.event())
                .scopeType(AuditRecord.Scope.MACHINE)
                .actor(osPrincipal)
                .outcome(AuditRecord.Outcome.fromWireName(event.outcome()).orElse(AuditRecord.Outcome.SUCCEEDED))
                .details(event.details())
                .build();
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
