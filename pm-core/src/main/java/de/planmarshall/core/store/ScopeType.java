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
 * The kind of scope that owns a lease (PM-IMPL-7). A plan and an epic are named by their id; a project has one
 * workspace scope, and the project itself owns the model slots of its workers, so neither of the two has a scope id.
 *
 * @since 0.1
 */
public enum ScopeType {
    /** A plan, named by its plan id. */
    PLAN("plan", true),
    /** An epic, named by its epic id. */
    EPIC("epic", true),
    /** The one workspace scope of a project. */
    WORKSPACE("workspace", false),
    /** The project itself: the owner of a model slot, a worker of the project's task queue. */
    PROJECT("project", false);

    private final String wireName;
    private final boolean identified;

    ScopeType(String wireName, boolean identified) {
        this.wireName = wireName;
        this.identified = identified;
    }

    /** @return the name of the value in a store */
    public String wireName() {
        return wireName;
    }

    /** @return whether a scope of this type is named by a scope id */
    public boolean hasScopeId() {
        return identified;
    }

    /**
     * @param wireName the name of a value in a store
     * @return the value of that name; empty if no value has it
     */
    public static Optional<ScopeType> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(type -> type.wireName.equals(wireName)).findFirst();
    }
}
