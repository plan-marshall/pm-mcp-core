/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.service;

import java.util.List;
import java.util.Objects;

/**
 * The live work of an enrolled project (PM-EXEC-2): its non-terminal plans, its non-terminal epics and its running
 * jobs. {@code unenrol} is refused with {@code scope_active} while a project has live work, and the refusal names
 * every item this interface reports. Implemented by the runtime from its lease and scope records.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface LiveWork {

    /**
     * @param projectId the enrolled project
     * @return the live work of the project, each item once; empty if the project has none
     */
    List<Item> of(String projectId);

    /**
     * What kind of live work an item is. The set is closed.
     *
     * @since 0.1
     */
    enum Kind {
        /** A plan that has not reached a terminal state. */
        PLAN("plan"),
        /** An epic that has not reached a terminal state. */
        EPIC("epic"),
        /** A job that is running. */
        JOB("job");

        private final String code;

        Kind(String code) {
            this.code = code;
        }

        /** @return the name of the kind in a representation */
        public String code() {
            return code;
        }
    }

    /**
     * One item of live work, named by its kind and its id, as a refusal names it.
     *
     * @param kind what kind of live work the item is
     * @param id   the id of the plan, epic or job
     * @since 0.1
     */
    record Item(Kind kind, String id) {

        /**
         * @throws NullPointerException     if a component is {@code null}
         * @throws IllegalArgumentException if the id is blank
         */
        public Item {
            Objects.requireNonNull(kind, "kind");
            Objects.requireNonNull(id, "id");
            if (id.isBlank()) {
                throw new IllegalArgumentException("Live work item id must not be blank");
            }
        }

        /** @return the kind and the id, as a refusal names the item, for example {@code plan add-login} */
        @Override
        public String toString() {
            return kind.code() + " " + id;
        }
    }
}
