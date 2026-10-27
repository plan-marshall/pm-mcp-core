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

import java.util.Objects;

import de.planmarshall.core.queue.ActivityJournal;

/**
 * Serves a task's activity to a client: a page from a cursor and a live stream (Store Schemas § Activity Journal).
 * <p>
 * The service owns no state of its own beyond the journal it is built on, so the assembly can serve both the resource
 * of one task and the stream of it without a registry to leak. The activity is the journal's, already masked and
 * bounded at the write.
 *
 * @since 0.1
 */
public final class ActivityService {

    /** The largest page a client may ask for. */
    public static final int MAX_PAGE = 200;

    private final ActivityJournal journal;

    /**
     * @param journal the journal of the task's scope
     */
    public ActivityService(ActivityJournal journal) {
        this.journal = Objects.requireNonNull(journal, "journal");
    }

    /**
     * @param queueTaskId the task
     * @param cursor      the last sequence the client saw; {@code 0} for the whole journal
     * @param limit       the page size, 1 to {@value #MAX_PAGE}
     * @return the page after the cursor
     * @throws IllegalArgumentException if the cursor is negative or the limit is outside its bound
     */
    public ActivityPage page(String queueTaskId, long cursor, int limit) {
        if (cursor < 0) {
            throw new IllegalArgumentException("A cursor is not negative: " + cursor);
        }
        if (limit < 1 || limit > MAX_PAGE) {
            throw new IllegalArgumentException("A page holds 1 to " + MAX_PAGE + " entries, not " + limit);
        }
        var after = journal.entries(queueTaskId).stream()
                .filter(entry -> entry.seq() > cursor)
                .toList();
        var page = after.stream().limit(limit).toList();
        var next = page.isEmpty() ? cursor : page.getLast().seq();
        return new ActivityPage(page, next, after.size() > page.size());
    }

    /**
     * @param queueTaskId the task
     * @param cursor      the last sequence the client saw; {@code 0} for the whole journal
     * @return the live stream of the task, from the cursor
     */
    public ActivityStream open(String queueTaskId, long cursor) {
        return new ActivityStream(journal, queueTaskId, cursor);
    }
}
