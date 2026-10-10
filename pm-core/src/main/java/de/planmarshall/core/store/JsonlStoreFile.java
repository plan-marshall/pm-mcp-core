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
import java.nio.file.attribute.PosixFilePermissions;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.OptionalLong;
import java.util.Set;
import java.util.function.Function;

import de.planmarshall.core.service.AuditEvent;
import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;

/**
 * The one append path of a JSONL store (PM-IMPL-7, PM-IMPL-2): a store file of records, one JSON object per line,
 * bound to the key of the lock that guards it.
 * <p>
 * A JSONL store is never rewritten. It grows by appends, and an append is a check-then-act on its tail: the decision
 * that the last line is torn and the cut that repairs it must see the same tail, and no second appender may come
 * between the repair and the write. Every append and every repair therefore runs in a transaction that holds the key
 * of the store, and is refused otherwise. A reader takes no part in it: it never repairs, and reports a torn tail
 * instead.
 * <p>
 * An append writes the block of records in one write on a channel opened with {@link StandardOpenOption#APPEND},
 * then forces the file to disk. Every record ends with a newline, so a last line without one is a torn tail that an
 * interrupted append left behind. The next append, still under the lock and before it writes, cuts the store back
 * to the end of its last complete line, forces it to disk, and hands a {@code jsonl_tail_repaired} record to the
 * audit sink. Nothing before the last newline is ever touched: a malformed complete line stays in place and is
 * reported as unreadable with its line number, while the other records of the store stay readable.
 * <p>
 * A leaf key names the store file itself, and on POSIX closing any channel on a file releases every lock of the
 * operating system that the process holds on that file. An append or a repair under a leaf key therefore releases
 * the lock of the operating system that the lock manager took on the store; only the in-process lock of the key
 * remains. Such a store is safe only while every writer of it runs in the one runtime that holds that in-process
 * lock.
 * <p>
 * The directory of the store exists before the first append; this type does not create it. The store file is
 * created by the first append, readable and writable by its owner only.
 *
 * @param store   the store file; kept absolute and normalized
 * @param lockKey the key of the lock that guards the store
 * @since 0.1
 */
public record JsonlStoreFile(Path store, LockKey lockKey) {

    private static final byte NEWLINE = '\n';
    private static final int SCAN_CHUNK = 8192;
    private static final String OWNER_ONLY = "rw-------";

    /**
     * Makes the store path absolute and normalized.
     *
     * @throws NullPointerException if a component is {@code null}
     */
    public JsonlStoreFile {
        store = Objects.requireNonNull(store, "store").toAbsolutePath().normalize();
        Objects.requireNonNull(lockKey, "lockKey");
    }

    /**
     * One complete line of the store, without its newline.
     *
     * @param number  the line number, counted from 1
     * @param content the bytes of the line, UTF-8 encoded; the accessor returns a copy
     * @since 0.1
     */
    public record Line(long number, byte[] content) {

        /**
         * @throws NullPointerException     if the content is {@code null}
         * @throws IllegalArgumentException if the number is less than 1
         */
        public Line {
            if (number < 1) {
                throw new IllegalArgumentException("Line numbers count from 1: " + number);
            }
            content = Objects.requireNonNull(content, "content").clone();
        }

        @Override
        public byte[] content() {
            return content.clone();
        }

        @Override
        public boolean equals(Object other) {
            return other instanceof Line line && number == line.number && Arrays.equals(content, line.content);
        }

        @Override
        public int hashCode() {
            return 31 * Long.hashCode(number) + Arrays.hashCode(content);
        }

        @Override
        public String toString() {
            return "Line[number=%d, %d bytes]".formatted(number, content.length);
        }
    }

    /**
     * What a read of the store found: its complete lines and, apart from them, a torn tail. A torn tail is never a
     * record.
     *
     * @param store          the store that was read
     * @param lines          the complete lines, in the order of the store
     * @param tornTailOffset the byte offset where a last line without a newline begins; empty if the store ends with
     *                       a newline, is empty or does not exist
     * @since 0.1
     */
    public record Content(Path store, List<Line> lines, OptionalLong tornTailOffset) {

        /** @throws NullPointerException if a component is {@code null} */
        public Content {
            Objects.requireNonNull(store, "store");
            lines = List.copyOf(lines);
            Objects.requireNonNull(tornTailOffset, "tornTailOffset");
        }

        /**
         * Decodes every complete line as a record. Each line is first checked for the current
         * {@code format_version}, so a record of another version is refused as outdated or newer and not as
         * unreadable; then the decoder reads it. A line that is refused stays an entry with its line number and the
         * refusal, and the lines after it are decoded as well.
         *
         * @param formatVersion the current format version of the records of the store
         * @param decoder       reads one record from the bytes of its line; it throws {@link StoreRefusal} naming
         *                      the store for a record that does not have the one shape of its kind
         * @param <T>           what a record holds
         * @return one entry per complete line, in the order of the store
         */
        public <T> List<Entry<T>> decode(int formatVersion, Function<byte[], T> decoder) {
            Objects.requireNonNull(decoder, "decoder");
            var entries = new ArrayList<Entry<T>>(lines.size());
            for (var line : lines) {
                entries.add(decodeLine(line, formatVersion, decoder));
            }
            return List.copyOf(entries);
        }

        private <T> Entry<T> decodeLine(Line line, int formatVersion, Function<byte[], T> decoder) {
            var bytes = line.content();
            try {
                StrictJson.requireCurrentVersion(store, bytes, formatVersion);
                return new Accepted<>(line.number(), decoder.apply(bytes));
            } catch (StoreRefusal refusal) {
                return new Refused<>(line.number(), refusal);
            }
        }
    }

    /**
     * The outcome of decoding one complete line of the store.
     *
     * @param <T> what a record holds
     * @since 0.1
     */
    public sealed interface Entry<T> permits Accepted, Refused {

        /** @return the line number of the record, counted from 1 */
        long line();
    }

    /**
     * A line that was decoded as a record of the current format version.
     *
     * @param line  the line number, counted from 1
     * @param value what the record holds
     * @param <T>   what a record holds
     * @since 0.1
     */
    public record Accepted<T>(long line, T value) implements Entry<T> {

        /** @throws NullPointerException if the value is {@code null} */
        public Accepted {
            Objects.requireNonNull(value, "value");
        }
    }

    /**
     * A line that was refused: it is unreadable or declares another format version. The line stays in the store.
     *
     * @param line    the line number, counted from 1
     * @param refusal why the line was refused, naming the store
     * @param <T>     what a record holds
     * @since 0.1
     */
    public record Refused<T>(long line, StoreRefusal refusal) implements Entry<T> {

        /** @throws NullPointerException if the refusal is {@code null} */
        public Refused {
            Objects.requireNonNull(refusal, "refusal");
        }
    }

    /**
     * Appends a block of records. A torn tail is repaired first, as {@link #repairTail(LockTransaction, AuditSink)}
     * does; then the block is written in one write on a channel opened for appending, and the file is forced to disk.
     *
     * @param transaction a transaction that holds the key of the store
     * @param records     the records to append, UTF-8 encoded, each ending with a newline
     * @param auditSink   where the record of a tail repair is handed over
     * @throws InternalFaultException   if the transaction does not hold the key; nothing was written then
     * @throws IllegalArgumentException if the block is empty or does not end with a newline; nothing was written then
     * @throws UncheckedIOException     if the store cannot be repaired or appended to
     */
    public void append(LockTransaction transaction, byte[] records, AuditSink auditSink) {
        requireHeld(transaction);
        Objects.requireNonNull(records, "records");
        Objects.requireNonNull(auditSink, "auditSink");
        if (records.length == 0 || records[records.length - 1] != NEWLINE) {
            throw new IllegalArgumentException(
                    "A block appended to a JSONL store is not empty and ends with a newline: " + store);
        }
        repairTail(transaction, auditSink);
        try {
            appendBlock(records);
        } catch (IOException e) {
            throw new UncheckedIOException("JSONL store cannot be appended to: " + store, e);
        }
    }

    /**
     * Repairs a torn tail: cuts the store back to the end of its last complete line, forces it to disk and hands a
     * {@code jsonl_tail_repaired} record with the number and the SHA-256 of the bytes cut off to the audit sink. A
     * store that ends with a newline, is empty or does not exist is left as it is, and nothing is recorded.
     *
     * @param transaction a transaction that holds the key of the store
     * @param auditSink   where the record of the repair is handed over
     * @throws InternalFaultException if the transaction does not hold the key; nothing was cut then
     * @throws UncheckedIOException   if the store cannot be read or cut
     */
    public void repairTail(LockTransaction transaction, AuditSink auditSink) {
        requireHeld(transaction);
        Objects.requireNonNull(auditSink, "auditSink");
        if (!Files.exists(store, LinkOption.NOFOLLOW_LINKS)) {
            return;
        }
        byte[] torn;
        try (var channel = FileChannel.open(store, StandardOpenOption.READ, StandardOpenOption.WRITE)) {
            var size = channel.size();
            var end = endOfLastCompleteLine(channel, size);
            if (end == size) {
                return;
            }
            torn = readFully(channel, end, Math.toIntExact(size - end));
            channel.truncate(end);
            channel.force(true);
        } catch (IOException e) {
            throw new UncheckedIOException("Torn tail of the JSONL store cannot be repaired: " + store, e);
        }
        auditSink.append(AuditEvent.jsonlTailRepaired(store, torn.length, StoreSnapshot.sha256(torn)));
    }

    /**
     * Reads the store under its lock. Nothing is repaired.
     *
     * @param transaction a transaction that holds the key of the store
     * @return the complete lines and a torn tail; no line if the store does not exist
     * @throws InternalFaultException if the transaction does not hold the key
     * @throws UncheckedIOException   if the store exists and cannot be read
     */
    public Content read(LockTransaction transaction) {
        requireHeld(transaction);
        return snapshot();
    }

    /**
     * Reads the store without a lock, as it is at the moment of the read. An append in progress shows as a torn
     * tail, never as a record. Nothing is repaired, and nothing is decided on the result that a write depends on.
     *
     * @return the complete lines and a torn tail; no line if the store does not exist
     * @throws UncheckedIOException if the store exists and cannot be read
     */
    public Content snapshot() {
        return StoreSnapshot.read(store).content().map(this::parse)
                .orElseGet(() -> new Content(store, List.of(), OptionalLong.empty()));
    }

    private Content parse(byte[] bytes) {
        var lines = new ArrayList<Line>();
        var start = 0;
        for (var i = 0; i < bytes.length; i++) {
            if (bytes[i] == NEWLINE) {
                lines.add(new Line(lines.size() + 1L, Arrays.copyOfRange(bytes, start, i)));
                start = i + 1;
            }
        }
        var tornTail = start < bytes.length ? OptionalLong.of(start) : OptionalLong.empty();
        return new Content(store, lines, tornTail);
    }

    private void appendBlock(byte[] records) throws IOException {
        var created = !Files.exists(store, LinkOption.NOFOLLOW_LINKS);
        var ownerOnly = PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString(OWNER_ONLY));
        try (var channel = FileChannel.open(store,
                Set.of(StandardOpenOption.CREATE, StandardOpenOption.WRITE, StandardOpenOption.APPEND), ownerOnly)) {
            var buffer = ByteBuffer.wrap(records);
            while (buffer.hasRemaining()) {
                channel.write(buffer);
            }
            channel.force(true);
        }
        if (created) {
            forceDirectory(store.getParent());
        }
    }

    /** A new file is an entry of the directory: it is on disk when the directory is. */
    private static void forceDirectory(Path directory) throws IOException {
        try (var channel = FileChannel.open(directory, StandardOpenOption.READ)) {
            channel.force(true);
        }
    }

    /** Scans back from the end in chunks; a store of complete lines is answered after its last byte. */
    private static long endOfLastCompleteLine(FileChannel channel, long size) throws IOException {
        var chunkEnd = size;
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
                throw new EOFException("The store ended before byte " + (position + length));
            }
        }
        return buffer.array();
    }

    private void requireHeld(LockTransaction transaction) {
        StoreLockGuard.requireHeld(transaction, store, lockKey);
    }
}
