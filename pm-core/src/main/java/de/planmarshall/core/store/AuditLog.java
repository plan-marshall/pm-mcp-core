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

import java.io.EOFException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;

/**
 * The audit log {@code <base>/logs/audit.jsonl} as a hash chain (PM-SEC-6): every line names the SHA-256 of the line
 * before it, so no complete record can be removed, reordered or edited without breaking the chain.
 * <p>
 * <em>The predecessor rule</em>, the one rule for every line written into the log: a new line chains to the SHA-256
 * of the last complete line of the log. When the log holds no complete line, it chains to
 * {@link AuditAnchor#emptyLogPredecessor()} when that is present (the checkpoint line that closed the rotated log), and
 * to {@link AuditCodec#GENESIS_HASH} only when no rotation ever happened. So the first record after a rotation links
 * to the checkpoint line.
 * <p>
 * <em>Appends</em> run under the audit lock ({@link LockKey#auditLog(Path)}, the leaf of the lock order) through the
 * one append path of a JSONL store ({@link JsonlStoreFile}), which closes its channel: only the in-process lock of the
 * key remains, and the runtime lock excludes a second runtime. The read of the last line, the append and the calls of
 * the {@link AuditAnchor} run in the one transaction that holds the key, so no second appender comes between the hash
 * and the line it chains.
 * <p>
 * <em>A torn tail of the log itself</em> is cut by the next append through the unchanged repair of the JSONL store,
 * which owes the {@code jsonl_tail_repaired} record in its intent file and hands it to the private in-log sink of this
 * type. That sink writes the record into the log directly, chained by the predecessor rule and forced to disk, before
 * it returns, so the intent is deleted only once the record is in the log: a stop before the write re-hands the
 * record, a stop after it at most writes it again with the same {@code transaction_id}, the deduplication key. When the
 * write fails, the intent stays and the append is not carried out.
 * <p>
 * The directory {@code logs/} exists before the first append; the lock manager creates it with the lock of the log.
 *
 * @since 0.1
 */
public final class AuditLog {

    private static final byte NEWLINE = '\n';
    private static final int SCAN_CHUNK = 8192;

    private final JsonlStoreFile store;
    private final AuditAnchor anchor;
    private final Clock clock;
    private final String osPrincipal;
    private final AuditSink inLogSink = this::writeOwedRecord;

    /**
     * @param base        the base directory of the runtime
     * @param anchor      the head, rotation and index of the log; {@link AuditAnchor#NONE} for the chain alone
     * @param clock       the clock of the instants and ids of the records the log writes itself
     * @param osPrincipal the OS principal of the runtime, the actor of the records the log writes itself
     * @throws NullPointerException     if an argument is {@code null}
     * @throws IllegalArgumentException if the OS principal is blank
     */
    public AuditLog(Path base, AuditAnchor anchor, Clock clock, String osPrincipal) {
        var key = LockKey.auditLog(base);
        this.store = new JsonlStoreFile(key.lockFile(), key);
        this.anchor = Objects.requireNonNull(anchor, "anchor");
        this.clock = Objects.requireNonNull(clock, "clock");
        this.osPrincipal = Objects.requireNonNull(osPrincipal, "osPrincipal");
        if (osPrincipal.isBlank()) {
            throw new IllegalArgumentException("The OS principal of the audit log must not be blank");
        }
    }

    /** @return the current log file {@code <base>/logs/audit.jsonl}, which a rotation renames */
    public Path logFile() {
        return store.store();
    }

    /**
     * Appends one record: repairs a torn tail, lets the anchor rotate the log, chains the record by the predecessor
     * rule, appends it, and lets the anchor write the head.
     *
     * @param transaction a transaction that holds the audit lock
     * @param template    the record to append; its {@code prev_hash} is replaced by the hash it chains to
     * @return the record as it was appended
     * @throws InternalFaultException if the transaction does not hold the audit lock; nothing was written then
     * @throws StoreRefusal           if the intent file of the log is unreadable; nothing was written then
     * @throws UncheckedIOException   if the log cannot be repaired or appended to, or the record of a repair could not
     *                                be written into the log; the record was not appended then
     */
    public AuditRecord append(LockTransaction transaction, AuditRecord template) {
        StoreLockGuard.requireHeld(transaction, logFile(), lockKey());
        Objects.requireNonNull(template, "template");
        repairTail(transaction);
        anchor.beforeAppend(transaction, this);
        var appended = template.withPrevHash(predecessor());
        var line = AuditCodec.writeRecord(appended);
        store.append(transaction, block(line), inLogSink);
        anchor.afterAppend(transaction, this, appended, AuditCodec.sha256Hex(line));
        return appended;
    }

    /**
     * Appends the checkpoint record that closes the log before a rotation. It repairs a torn tail, chains the record
     * by the predecessor rule and appends it; it never calls the anchor, so a rotation cannot recurse.
     *
     * @param transaction a transaction that holds the audit lock
     * @param checkpoint  the checkpoint record; its {@code prev_hash} is replaced by the hash it chains to
     * @return the SHA-256 of the checkpoint line as it was written, in lower-case hex
     * @throws IllegalStateException    if the transaction does not hold the audit lock; nothing was written then
     * @throws IllegalArgumentException if the record is not a {@value AuditRecord#EVENT_CHECKPOINT} record; nothing
     *                                  was written then
     * @throws UncheckedIOException     if the log cannot be repaired or appended to
     */
    public String appendCheckpoint(LockTransaction transaction, AuditRecord checkpoint) {
        Objects.requireNonNull(transaction, "transaction");
        Objects.requireNonNull(checkpoint, "checkpoint");
        if (!transaction.holds(lockKey())) {
            throw new IllegalStateException("A checkpoint is appended only under the audit lock: " + logFile());
        }
        if (!checkpoint.isCheckpoint()) {
            throw new IllegalArgumentException("Not a checkpoint record: " + checkpoint.event());
        }
        repairTail(transaction);
        var line = AuditCodec.writeRecord(checkpoint.withPrevHash(predecessor()));
        store.append(transaction, block(line), inLogSink);
        return AuditCodec.sha256Hex(line);
    }

    /**
     * Counts the complete lines of the current log, under the audit lock. A torn tail is not a record.
     *
     * @param transaction a transaction that holds the audit lock
     * @return the number of complete lines; 0 if the log does not exist
     * @throws InternalFaultException if the transaction does not hold the audit lock
     * @throws UncheckedIOException   if the log exists and cannot be read
     */
    public long recordCount(LockTransaction transaction) {
        StoreLockGuard.requireHeld(transaction, logFile(), lockKey());
        if (!Files.exists(logFile(), LinkOption.NOFOLLOW_LINKS)) {
            return 0;
        }
        try (var channel = FileChannel.open(logFile(), StandardOpenOption.READ)) {
            var count = 0L;
            var buffer = ByteBuffer.allocate(SCAN_CHUNK);
            while (channel.read(buffer) >= 0) {
                buffer.flip();
                while (buffer.hasRemaining()) {
                    if (buffer.get() == NEWLINE) {
                        count++;
                    }
                }
                buffer.clear();
            }
            return count;
        } catch (IOException e) {
            throw new UncheckedIOException("Audit log cannot be read: " + logFile(), e);
        }
    }

    /**
     * Reads the current log under the audit lock. Nothing is repaired; a torn tail is reported, never a record.
     *
     * @param transaction a transaction that holds the audit lock
     * @return the complete lines and a torn tail; no line if the log does not exist
     * @throws InternalFaultException if the transaction does not hold the audit lock
     * @throws UncheckedIOException   if the log exists and cannot be read
     */
    public JsonlStoreFile.Content read(LockTransaction transaction) {
        return store.read(transaction);
    }

    /**
     * Reads the current log without a lock, as it is at the moment of the read. An append in progress shows as a torn
     * tail, never as a record.
     *
     * @return the complete lines and a torn tail; no line if the log does not exist
     * @throws UncheckedIOException if the log exists and cannot be read
     */
    public JsonlStoreFile.Content snapshot() {
        return store.snapshot();
    }

    /** @return the key of the audit lock */
    LockKey lockKey() {
        return store.lockKey();
    }

    /** @return the clock of the instants and ids of the records */
    Clock clock() {
        return clock;
    }

    /** @return the OS principal of the runtime */
    String osPrincipal() {
        return osPrincipal;
    }

    /**
     * Cuts a torn tail and has the in-log sink write its record. An intent left behind means the record is not in the
     * log, and the append is not carried out.
     */
    private void repairTail(LockTransaction transaction) {
        store.repairTail(transaction, inLogSink);
        var intent = store.auditIntentFile();
        if (Files.exists(intent, LinkOption.NOFOLLOW_LINKS)) {
            throw new UncheckedIOException(new IOException(
                    "The record of a tail repair of the audit log is not in the log and stays owed in " + intent));
        }
    }

    /**
     * The in-log sink: writes an owed record of the log's own tail repair into the log directly, chained and forced
     * to disk, never through the JSONL append, which would repair the tail again while the intent still exists.
     */
    private void writeOwedRecord(AuditEvent event) {
        var owed = AuditLogSink.toRecord(event, clock, osPrincipal).withPrevHash(predecessor());
        try {
            SyncedFiles.append(logFile(), block(AuditCodec.writeRecord(owed)));
        } catch (IOException e) {
            throw new UncheckedIOException("The record of a tail repair cannot be written into the audit log: "
                    + logFile(), e);
        }
    }

    private String predecessor() {
        return lastCompleteLine().map(AuditCodec::sha256Hex).or(anchor::emptyLogPredecessor)
                .orElse(AuditCodec.GENESIS_HASH);
    }

    /** Reads the last complete line from the end of the log, in chunks; a torn tail after it is skipped. */
    private Optional<byte[]> lastCompleteLine() {
        var file = logFile();
        if (!Files.exists(file, LinkOption.NOFOLLOW_LINKS)) {
            return Optional.empty();
        }
        try (var channel = FileChannel.open(file, StandardOpenOption.READ)) {
            var end = afterLastNewline(channel, channel.size());
            if (end == 0) {
                return Optional.empty();
            }
            var start = afterLastNewline(channel, end - 1);
            return Optional.of(readFully(channel, start, Math.toIntExact(end - 1 - start)));
        } catch (IOException e) {
            throw new UncheckedIOException("Audit log cannot be read: " + file, e);
        }
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

    private static byte[] block(byte[] line) {
        var block = Arrays.copyOf(line, line.length + 1);
        block[line.length] = NEWLINE;
        return block;
    }
}
