/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

import java.util.Objects;

import de.planmarshall.core.store.EntryId;

/**
 * The engine's read view of one pending entry record {@code pending/<entry_id>.json} of the project's workspace scope
 * (PM-ARCH-4, bullets 2 and 5: a pending plan or epic bootstrap is addressed in the workspace scope by its
 * {@code entry_id} as entity, and resolved from its persisted record, never from the memory of a running engine). The
 * engine reads the record an {@code entry_id} names to learn whether the bootstrap still exists and of which kind it
 * is.
 * <p>
 * The request carries the typed {@link EntryId} of {@code core.store}, which owns the grammar of the {@code entry_id}:
 * the port never receives an unchecked string.
 * <p>
 * The read is tri-state: a record that is gone (the bootstrap ended, or the sweep removed it) is
 * {@link EntryRead.Deleted}, and a record that exists but cannot be read is the explicit result
 * {@link EntryRead.Unreadable}, never taken for a live one, so a broken store is never mistaken for a bootstrap that
 * can be addressed.
 * <p>
 * The records are kept by {@link de.planmarshall.core.store.PendingEntryStore} of {@code core.store}; the port is
 * implemented over that store, outside the engine packages:
 * {@link de.planmarshall.core.store.PendingEntryStore#read(EntryId)} returning a record is {@link EntryRead.Live} with
 * the {@linkplain de.planmarshall.core.store.PendingEntryRecord.Kind#wireName() wire name} of the record's kind,
 * returning empty is {@link EntryRead.Deleted}, and throwing {@link de.planmarshall.core.store.StoreRefusal} (another
 * format version, an unreadable record, or a record that states another {@code entry_id} than its file name) or
 * {@link java.io.UncheckedIOException} (a record that exists and cannot be read) is {@link EntryRead.Unreadable} with
 * the reason of the failure. As every engine port, it is implemented in {@code pm-runtime}, which holds the store of
 * a project's workspace scope; no class of {@code pm-core} implements it (PM-IMPL-5), and no implementation exists
 * yet: until the runtime wires one, only the test doubles do.
 *
 * @since 0.1
 */
public interface PendingEntryReader {

    /**
     * Reads a pending entry record.
     *
     * @param request the entry to read
     * @return the record live with its kind, deleted, or unreadable
     */
    EntryRead read(ReadRequest request);

    /**
     * What the engine asks the port to read: the pending entry record of one bootstrap.
     *
     * @param entryId the entry, whose grammar its type has checked
     * @since 0.1
     */
    record ReadRequest(EntryId entryId) {

        /**
         * @throws NullPointerException if the entry is {@code null}
         */
        public ReadRequest {
            Objects.requireNonNull(entryId, "entryId");
        }
    }

    /**
     * The three results of one read.
     *
     * @since 0.1
     */
    sealed interface EntryRead permits EntryRead.Live, EntryRead.Deleted, EntryRead.Unreadable {

        /**
         * The record was read; the bootstrap it holds can be addressed.
         *
         * @param kind the kind of scope the bootstrap creates, {@code plan} or {@code epic}
         * @since 0.1
         */
        record Live(String kind) implements EntryRead {

            /**
             * @throws NullPointerException if the kind is {@code null}
             * @throws IllegalArgumentException if the kind is blank
             */
            public Live {
                requireNonBlank(kind, "kind");
            }
        }

        /**
         * No record exists under the identifier: the bootstrap ended or the sweep removed it. The caller answers
         * {@code scope_not_found}.
         *
         * @since 0.1
         */
        record Deleted() implements EntryRead {
        }

        /**
         * The record exists and could not be read; whether its bootstrap can be addressed is unknown.
         *
         * @param reason why the record could not be read
         * @since 0.1
         */
        record Unreadable(String reason) implements EntryRead {

            /**
             * @throws NullPointerException if the reason is {@code null}
             * @throws IllegalArgumentException if the reason is blank
             */
            public Unreadable {
                requireNonBlank(reason, "reason");
            }
        }

        private static void requireNonBlank(String value, String name) {
            Objects.requireNonNull(value, name);
            if (value.isBlank()) {
                throw new IllegalArgumentException(name + " must not be blank");
            }
        }
    }
}
