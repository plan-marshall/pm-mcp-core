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

/**
 * The engine port that reads one pending entry record of the project (PM-ARCH-4, bullets 2 and 5: a pending plan or
 * epic bootstrap is addressed in the workspace scope by its {@code entry_id} as entity, and resolved from its persisted
 * record, never from the memory of a running engine). The engine reads the record an {@code entry_id} names to learn
 * whether the bootstrap still exists and of which kind it is.
 * <p>
 * The read is tri-state: a record that is gone (the bootstrap ended, or the sweep removed it) is
 * {@link EntryRead.Deleted}, and a record that exists but cannot be read is the explicit result
 * {@link EntryRead.Unreadable}, never taken for a live one, so a broken store is never mistaken for a bootstrap that
 * can be addressed.
 * <p>
 * The {@code entry_id} the port receives has already been checked against its grammar by the addressing of
 * {@code pm-workflow}, the only place of that check; the request therefore refuses only an absent or blank identifier.
 * <p>
 * The pending entry store of {@code pm-runtime} implements the port; no class of {@code pm-core} does (PM-IMPL-5).
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
     * @param entryId the identifier of the entry, already checked against {@code ^ent-[0-9a-f]{8,32}$} by its caller
     * @since 0.1
     */
    record ReadRequest(String entryId) {

        /**
         * @throws NullPointerException if the entry identifier is {@code null}
         * @throws IllegalArgumentException if the entry identifier is blank
         */
        public ReadRequest {
            requireNonBlank(entryId, "entryId");
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
    }

    private static void requireNonBlank(String value, String name) {
        Objects.requireNonNull(value, name);
        if (value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
    }
}
