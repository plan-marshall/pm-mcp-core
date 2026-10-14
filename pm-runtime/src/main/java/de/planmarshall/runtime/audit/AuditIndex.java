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

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalLong;

import de.planmarshall.core.service.AuditSink;
import de.planmarshall.core.service.InternalFaultException;
import de.planmarshall.core.service.LockTransaction;
import de.planmarshall.core.store.AuditCodec;
import de.planmarshall.core.store.AuditIndexEntry;
import de.planmarshall.core.store.JsonlStoreFile;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The audit index {@code logs/audit-index.jsonl} (PM-SEC-6; Store Schemas, Audit Index): one entry per rotated log
 * file, append-only and hash-chained like the log, so a removed, reordered or edited entry breaks its chain.
 * <p>
 * An entry is appended only at a rotation, under the audit lock, through the one append path of a JSONL store; it
 * chains to the SHA-256 of the last complete line of the index, the first entry to
 * {@link AuditCodec#GENESIS_HASH}. The verification reads the index without the lock ({@link #snapshot()}); a torn
 * tail is never an entry. An entry is never removed, so a rotated file that was purged is still accounted for.
 *
 * @since 0.1
 */
public final class AuditIndex {

    /** The name of the index file beside the log. */
    public static final String FILE_NAME = "audit-index.jsonl";

    private static final byte NEWLINE = '\n';

    private final JsonlStoreFile store;

    /**
     * @param base the base directory of the runtime
     * @throws NullPointerException if the base is {@code null}
     */
    public AuditIndex(Path base) {
        var key = LockKey.auditLog(Objects.requireNonNull(base, "base"));
        this.store = new JsonlStoreFile(key.lockFile().resolveSibling(FILE_NAME), key);
    }

    /**
     * What a read of the index found.
     *
     * @param entries         the entries of the index up to the first one that breaks its chain, in their order
     * @param headHash        the SHA-256 of the last complete line of the index, intact or not;
     *                        {@link AuditCodec#GENESIS_HASH} when the index holds no complete line
     * @param firstBrokenLine the first line that does not parse or does not chain to the line before it; empty when
     *                        the chain of the index is intact
     * @since 0.1
     */
    public record Snapshot(List<AuditIndexEntry> entries, String headHash, OptionalLong firstBrokenLine) {

        /** @throws NullPointerException if a component is {@code null} */
        public Snapshot {
            entries = List.copyOf(entries);
            Objects.requireNonNull(headHash, "headHash");
            Objects.requireNonNull(firstBrokenLine, "firstBrokenLine");
        }

        /** @return the last entry of the intact chain; empty when the index holds none */
        public Optional<AuditIndexEntry> last() {
            return entries.isEmpty() ? Optional.empty() : Optional.of(entries.getLast());
        }

        /**
         * @return the hash the first line of the current log chains to: the {@code checkpoint_hash} of the last
         *         entry; empty when no rotation ever happened
         */
        public Optional<String> lastCheckpointHash() {
            return last().map(AuditIndexEntry::checkpointHash);
        }
    }

    /** @return the index file */
    public Path file() {
        return store.store();
    }

    /**
     * Reads the index without a lock, as it is at the moment of the read. Nothing is repaired.
     *
     * @return the entries and the state of the chain; no entry if the index does not exist
     * @throws UncheckedIOException if the index exists and cannot be read
     */
    public Snapshot snapshot() {
        return evaluate(store.snapshot());
    }

    /**
     * Reads the index under the audit lock. Nothing is repaired.
     *
     * @param transaction a transaction that holds the audit lock
     * @return the entries and the state of the chain; no entry if the index does not exist
     * @throws InternalFaultException if the transaction does not hold the audit lock
     * @throws UncheckedIOException   if the index exists and cannot be read
     */
    public Snapshot read(LockTransaction transaction) {
        return evaluate(store.read(transaction));
    }

    /**
     * Appends one entry, chained to the last complete line of the index. A torn tail is cut first, and the record
     * of the cut is handed to the given sink, owed beside the index until the sink accepted it.
     *
     * @param transaction a transaction that holds the audit lock
     * @param entry       the entry; its {@code prev_hash} is replaced by the hash it chains to
     * @param repairSink  where the record of a tail repair of the index is handed over
     * @return the SHA-256 of the appended line, the new {@code index_head_hash}
     * @throws InternalFaultException if the transaction does not hold the audit lock; nothing was written then
     * @throws UncheckedIOException   if the index cannot be repaired or appended to
     */
    public String append(LockTransaction transaction, AuditIndexEntry entry, AuditSink repairSink) {
        Objects.requireNonNull(entry, "entry");
        var lines = store.read(transaction).lines();
        var prevHash = lines.isEmpty() ? AuditCodec.GENESIS_HASH : AuditCodec.sha256Hex(lines.getLast().content());
        var line = AuditCodec.writeIndexEntry(entry.withPrevHash(prevHash));
        var block = Arrays.copyOf(line, line.length + 1);
        block[line.length] = NEWLINE;
        store.append(transaction, block, repairSink);
        return AuditCodec.sha256Hex(line);
    }

    /**
     * Hands over the records of tail repairs of the index that are still owed, as an append does first.
     *
     * @param transaction a transaction that holds the audit lock
     * @param sink        where the owed records are handed over
     * @throws InternalFaultException if the transaction does not hold the audit lock
     * @throws UncheckedIOException   if the index or its intent file cannot be read or written
     */
    public void handOverRepairs(LockTransaction transaction, AuditSink sink) {
        store.repairTail(transaction, sink);
    }

    private Snapshot evaluate(JsonlStoreFile.Content content) {
        var lines = content.lines();
        var headHash = lines.isEmpty() ? AuditCodec.GENESIS_HASH : AuditCodec.sha256Hex(lines.getLast().content());
        var entries = new ArrayList<AuditIndexEntry>();
        var expected = AuditCodec.GENESIS_HASH;
        for (var line : lines) {
            var bytes = line.content();
            var entry = decode(bytes);
            if (entry.isEmpty() || !entry.get().prevHash().equals(expected)) {
                return new Snapshot(entries, headHash, OptionalLong.of(line.number()));
            }
            entries.add(entry.get());
            expected = AuditCodec.sha256Hex(bytes);
        }
        return new Snapshot(entries, headHash, OptionalLong.empty());
    }

    private Optional<AuditIndexEntry> decode(byte[] line) {
        try {
            return Optional.of(AuditCodec.readIndexEntry(store.store(), line));
        } catch (StoreRefusal _) {
            return Optional.empty();
        }
    }
}
