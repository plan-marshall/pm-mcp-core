/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.web;

import java.util.List;
import java.util.Objects;

import de.planmarshall.core.queue.ActivityEntry;
import de.planmarshall.core.queue.ActivityJournal;

/**
 * The live stream of one task's activity (Store Schemas § Activity Journal: a live stream a client opens only while it
 * shows the task).
 * <p>
 * The stream is a cursor the caller drives: every {@link #poll()} answers the entries written since the previous poll,
 * so the transport that owns the connection decides when to ask and never blocks a writer. A closed stream answers
 * nothing and refuses a further poll. The journal stays the source of truth: nothing is pushed into the stream, so a
 * dropped client leaves no state behind.
 *
 * @since 0.1
 */
public final class ActivityStream implements AutoCloseable {

    private final ActivityJournal journal;
    private final String queueTaskId;
    private long cursor;
    private boolean closed;

    /**
     * @param journal     the journal of the task's scope
     * @param queueTaskId the task
     * @param cursor      the last sequence the client saw; {@code 0} for the whole journal
     */
    public ActivityStream(ActivityJournal journal, String queueTaskId, long cursor) {
        this.journal = Objects.requireNonNull(journal, "journal");
        this.queueTaskId = Objects.requireNonNull(queueTaskId, "queueTaskId");
        if (cursor < 0) {
            throw new IllegalArgumentException("A cursor is not negative: " + cursor);
        }
        this.cursor = cursor;
    }

    /**
     * @return the entries written since the previous poll, in sequence order
     * @throws IllegalStateException if the stream is closed
     */
    public List<ActivityEntry> poll() {
        if (closed) {
            throw new IllegalStateException("The activity stream of " + queueTaskId + " is closed");
        }
        var entries = journal.entries(queueTaskId).stream()
                .filter(entry -> entry.seq() > cursor)
                .toList();
        if (!entries.isEmpty()) {
            cursor = entries.getLast().seq();
        }
        return entries;
    }

    /** @return the last sequence this stream has answered */
    public long cursor() {
        return cursor;
    }

    /** @return whether the stream is closed */
    public boolean isClosed() {
        return closed;
    }

    @Override
    public void close() {
        closed = true;
    }
}
