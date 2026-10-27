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

/**
 * One page of a task's activity from a cursor (Store Schemas § Activity Journal: the entries from a cursor).
 * <p>
 * The client sends the cursor it last saw and receives the entries after it; {@code nextCursor} is the entry it must
 * send next. A journal bounded by its seq never reuses one, so a cursor stays valid and a gap shows what gave way.
 *
 * @param entries    the entries after the cursor, in sequence order
 * @param nextCursor the last sequence this page carries, or the cursor it was asked for when the page is empty
 * @param hasMore    whether the journal holds an entry beyond {@code nextCursor}
 * @since 0.1
 */
public record ActivityPage(List<ActivityEntry> entries, long nextCursor, boolean hasMore) {

    /** Copies the entries. */
    public ActivityPage {
        entries = List.copyOf(Objects.requireNonNull(entries, "entries"));
        if (nextCursor < 0) {
            throw new IllegalArgumentException("A cursor is not negative: " + nextCursor);
        }
    }
}
