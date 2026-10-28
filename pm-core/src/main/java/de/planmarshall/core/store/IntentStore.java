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

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Objects;

/**
 * A plan's {@code intent.json} (Store Schemas § Intent Record): the accepted record as {@code current}, and every
 * earlier revision in {@code history}. The accepting transaction moves the record it replaces into the history and
 * keeps it unchanged (Phase Workflows § The Intent Record, Revisions).
 * <p>
 * {@link #accept(Revision)} is that transaction's data change: it refuses a revision that does not follow the current
 * one and an identifier an earlier revision removed, which the Identifier Registry forbids reusing for another item.
 * The record's own rules are those of {@link IntentRecord}; the checks that need the plan are those of
 * {@link IntentAcceptance}.
 *
 * @param current the accepted revision
 * @param history every earlier revision, oldest first
 * @since 0.1
 */
public record IntentStore(Revision current, List<Revision> history) {

    /** The format version of an intent store document that is read and written. */
    public static final int FORMAT_VERSION = 1;

    /**
     * Copies the history into an immutable list and checks that no revision follows the current one.
     *
     * @throws NullPointerException     if a value is {@code null}
     * @throws IllegalArgumentException if a history revision is not older than the current one
     */
    public IntentStore {
        Objects.requireNonNull(current, "current");
        Objects.requireNonNull(history, "history");
        history = List.copyOf(history);
        var previous = 0;
        for (var revision : history) {
            if (revision.revision() <= previous || revision.revision() >= current.revision()) {
                throw new IllegalArgumentException(
                        "the history revisions are older than the current revision %d and strictly increasing: %d"
                                .formatted(current.revision(), revision.revision()));
            }
            previous = revision.revision();
        }
        if (!history.isEmpty() && current.revision() != history.getLast().revision() + 1) {
            throw new IllegalArgumentException(
                    "the current revision follows the last history revision: %d after %d".formatted(
                            current.revision(), history.getLast().revision()));
        }
    }

    /**
     * @param revision the first revision of a plan's intent record
     * @return the store that holds it
     */
    public static IntentStore first(Revision revision) {
        Objects.requireNonNull(revision, "revision");
        if (revision.revision() != 1) {
            throw new IllegalArgumentException("the first revision of a record is revision 1: " + revision.revision());
        }
        return new IntentStore(revision, List.of());
    }

    /**
     * Accepts the next revision and moves the current one into the history.
     *
     * @param next the revision a task submitted
     * @return the store after the acceptance
     * @throws IllegalArgumentException if the revision does not follow the current one, or reuses an identifier an
     *                                  earlier revision removed
     */
    public IntentStore accept(Revision next) {
        Objects.requireNonNull(next, "next");
        if (next.revision() != current.revision() + 1) {
            throw new IllegalArgumentException(
                    "the submitted revision is %d, the next one is %d".formatted(next.revision(),
                            current.revision() + 1));
        }
        var removed = removedIdentifiers();
        var reused = next.intentRecord().identifiers().stream().filter(removed::contains).toList();
        if (!reused.isEmpty()) {
            throw new IllegalArgumentException(
                    "the revision reuses the identifiers %s, which an earlier revision removed".formatted(reused));
        }
        var nextHistory = new ArrayList<>(history);
        nextHistory.add(current);
        return new IntentStore(next, nextHistory);
    }

    /**
     * @return the identifiers an earlier revision held and the current one no longer holds
     */
    public List<String> removedIdentifiers() {
        var currentIds = new HashSet<>(current.intentRecord().identifiers());
        var removed = new ArrayList<String>();
        for (var revision : history) {
            for (var id : revision.intentRecord().identifiers()) {
                if (!currentIds.contains(id) && !removed.contains(id)) {
                    removed.add(id);
                }
            }
        }
        return List.copyOf(removed);
    }

    /**
     * One accepted revision.
     *
     * @param revision     the revision number, from 1
     * @param submittedAt  the instant the transaction accepted it
     * @param taskId       the task whose submission carried the record
     * @param intentRecord the submitted record
     * @since 0.1
     */
    public record Revision(int revision, Instant submittedAt, String taskId, IntentRecord intentRecord) {

        /**
         * @throws IllegalArgumentException if the revision or the task id is out of its bounds
         * @throws NullPointerException     if a value is {@code null}
         */
        public Revision {
            if (revision < 1) {
                throw new IllegalArgumentException("a revision number is at least 1: " + revision);
            }
            Objects.requireNonNull(submittedAt, "submittedAt");
            Objects.requireNonNull(taskId, "taskId");
            if (taskId.isEmpty() || taskId.length() > 128) {
                throw new IllegalArgumentException("a task id is 1 to 128 characters: " + taskId.length());
            }
            Objects.requireNonNull(intentRecord, "intentRecord");
        }
    }
}
