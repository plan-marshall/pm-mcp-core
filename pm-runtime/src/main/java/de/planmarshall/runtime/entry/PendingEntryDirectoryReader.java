/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.entry;

import java.io.UncheckedIOException;
import java.nio.file.Path;
import java.util.Objects;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.spi.PendingEntryReader;
import de.planmarshall.core.store.JournalScope;
import de.planmarshall.core.store.LockKey;
import de.planmarshall.core.store.PendingEntryStore;
import de.planmarshall.core.store.StoreRefusal;

/**
 * The adapter of the engine port {@link PendingEntryReader} over the pending entry directory
 * {@code pending/<entry_id>/} of {@link PendingEntryStore} in {@code core.store} (PM-ARCH-4, PM-IMPL-5): it reads the
 * record {@code entry.json} an {@code entry_id} names and returns the port's tri-state read of the entry.
 * <p>
 * <b>Trust assumptions.</b> The read is a read: it takes no lock an append or a transaction could wait on, writes
 * nothing and repairs nothing.
 * <ol>
 * <li>The record is replaced by an atomic rename under the workspace lock, so a lock-free read sees one complete
 * version of it, never a torn record. A torn <em>record</em> is therefore evidence of an edit outside a transaction,
 * and is reported {@link EntryRead.Unreadable} with the refusal of the codec.</li>
 * <li>The read judges the record alone, never the entry's source directory, question or decision store, and never the
 * chat transcript. A torn line in one of those is not this reader's evidence.</li>
 * <li>The read does not verify the digest sidecar. A lock-free read cannot decide an edit outside a transaction: it
 * would see a transaction in flight as a difference ({@code ScopeIntegrity.snapshot} reports
 * {@code digest_unverified} while a journal exists and never decides). Integrity is decided by
 * {@link PendingEntryStore#verify} under the workspace lock, whose {@code Tampered} answer names the entry and its
 * store; the caller raises the entry's own hold from that answer.</li>
 * <li>An absent record is {@link EntryRead.Deleted}: the entry's scope was created, the entry was abandoned, or the
 * record was already removed by a removal a stop cut off. The directory that a cut-off removal left behind is the
 * sweep's residue, never a live entry, and the reader does not remove it.</li>
 * <li>The path is {@code pending/<entry_id>/entry.json} for the {@code entry_id} of the request, which its type has
 * checked, below the project's store root; it cannot name a file outside the pending directory.</li>
 * </ol>
 *
 * @since 0.1
 */
public final class PendingEntryDirectoryReader implements PendingEntryReader {

    private final PendingEntryStore store;

    /**
     * @param base      the base directory of the runtime, below which the project stores lie
     * @param projectId the project whose pending entries are read
     * @throws NullPointerException     if a component is {@code null}
     * @throws IllegalArgumentException if the project id is not an identifier
     */
    public PendingEntryDirectoryReader(Path base, String projectId) {
        this(base, projectId, null);
    }

    /**
     * The seam of the refusal tests: a store the caller names, which must be the store of the project's workspace
     * scope in production.
     *
     * @param base      the base directory of the runtime
     * @param projectId the project whose pending entries are read
     * @param store     the store to read, or {@code null} for the project's own workspace store
     * @throws NullPointerException     if the project id is {@code null}
     * @throws IllegalArgumentException if the project id is not an identifier
     */
    PendingEntryDirectoryReader(Path base, String projectId, @Nullable PendingEntryStore store) {
        Objects.requireNonNull(base, "base");
        var project = Objects.requireNonNull(projectId, "projectId");
        LockKey.workspace(base, project);
        this.store = store == null ? new PendingEntryStore(JournalScope.workspace(base, project)) : store;
    }

    /**
     * {@inheritDoc}
     *
     * @throws NullPointerException if the request is {@code null}
     */
    @Override
    public EntryRead read(ReadRequest request) {
        Objects.requireNonNull(request, "request");
        try {
            return store.read(request.entryId())
                    .<EntryRead>map(record -> new EntryRead.Live(record.kind().wireName()))
                    .orElseGet(EntryRead.Deleted::new);
        } catch (StoreRefusal e) {
            return new EntryRead.Unreadable(e.getMessage());
        } catch (UncheckedIOException e) {
            return new EntryRead.Unreadable("the entry record cannot be read: " + e.getMessage());
        }
    }
}
