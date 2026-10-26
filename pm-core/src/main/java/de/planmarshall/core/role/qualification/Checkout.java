/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.role.qualification;

import java.util.Arrays;
import java.util.Optional;

/**
 * Where a graded item ran (Model Work § Grading; Store Schemas § Role Qualification Record, {@code checkout}).
 *
 * @since 0.1
 */
public enum Checkout {

    /** An item of the project's own repository, run in a job-private detached worktree at its origin commit. */
    ITEM_COMMIT("item_commit"),

    /** An item run in the project checkout, because it names no commit of the project or that commit is missing. */
    CURRENT("current"),

    /** An item with context files of the corpus, run in an empty job-private working directory with them in-band. */
    CORPUS_CONTEXT("corpus_context");

    private final String wireName;

    Checkout(String wireName) {
        this.wireName = wireName;
    }

    /** @return the name of the place in a record */
    public String wireName() {
        return wireName;
    }

    /**
     * @param wireName the name of a place in a document
     * @return the place; empty if the name is not one
     */
    public static Optional<Checkout> fromWireName(String wireName) {
        return Arrays.stream(values()).filter(checkout -> checkout.wireName.equals(wireName)).findFirst();
    }
}
