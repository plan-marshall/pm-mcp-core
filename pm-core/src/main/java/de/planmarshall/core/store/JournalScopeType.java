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

import java.util.Arrays;
import java.util.Optional;

/**
 * The type of a scope that keeps a transaction journal (PM-IMPL-7), as the {@code scope_type} of the journal names
 * it. The set is closed. A plan and an epic are named by their id; the workspace scope, the machine scope and the
 * lesson store of a project have none.
 * <p>
 * This is the journal's own set; the owning scope of a lease ({@link ScopeType}) is another one.
 *
 * @since 0.1
 */
public enum JournalScopeType {

    /** A plan scope: its scope id is the plan id. */
    PLAN("plan", true),
    /** An epic scope: its scope id is the epic id. */
    EPIC("epic", true),
    /** The workspace scope of a project: it has no scope id. */
    WORKSPACE("workspace", false),
    /** The one machine scope: it has no scope id and belongs to no project. */
    MACHINE("machine", false),
    /** The lesson store of a project: it has no scope id. */
    LESSONS("lessons", false);

    private final String wireName;
    private final boolean scopeIdRequired;

    JournalScopeType(String wireName, boolean scopeIdRequired) {
        this.wireName = wireName;
        this.scopeIdRequired = scopeIdRequired;
    }

    /** @return the name of the type in a journal and an audit record */
    public String wireName() {
        return wireName;
    }

    /** @return whether a scope of this type is named by a scope id; if not, its scope id is {@code null} */
    public boolean scopeIdRequired() {
        return scopeIdRequired;
    }

    /**
     * @param wireName the name of a type in a journal
     * @return the type of that name; empty if no type has it
     */
    public static Optional<JournalScopeType> ofWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst();
    }
}
