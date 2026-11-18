/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.worker;

import java.util.Collection;
import lombok.experimental.UtilityClass;
import org.jspecify.annotations.Nullable;

import de.planmarshall.core.role.corpus.ImportedItem;

/**
 * The contamination rule of a warm grading run (Model Work § Grading; ADR {@code core-role/004}): one supervised worker
 * takes the items of a form in order, and two items whose {@code contamination} notes name each other never share a
 * worker.
 * <p>
 * The note is literal text copied from the corpus row, and it names an item by its id. An item may join the group of the
 * running worker only when none of the items already served carries a note naming it and its own note names none of them;
 * otherwise the worker's group is closed and the item opens a new one. A fresh form needs no rule: every fresh item runs
 * in its own worker.
 *
 * @since 0.1
 */
@UtilityClass
public class WarmServing {

    /**
     * @param contamination the contamination note of the candidate item
     * @param servedIds     the ids of the items already served by the running worker
     * @return {@code true} when the candidate may share the running worker
     */
    public static boolean mayShare(@Nullable String contamination, Collection<String> servedIds) {
        if (contamination == null) {
            return true;
        }
        for (var id : servedIds) {
            if (contamination.contains(id)) {
                return false;
            }
        }
        return true;
    }

    /**
     * @param served the items already served by the running worker
     * @param item   the candidate item
     * @return {@code true} when one of the served items' notes names the candidate, so the two never share a worker
     */
    public static boolean names(Collection<ImportedItem> served, ImportedItem item) {
        for (var other : served) {
            var note = other.contamination();
            if (note != null && note.contains(item.id())) {
                return true;
            }
        }
        return false;
    }
}
