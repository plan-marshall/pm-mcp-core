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

/**
 * Whether a commit of the project's own repository is present, so an item taken from it can be graded at its origin
 * commit (Model Work § Grading): an item whose commit is not in the repository runs in the checkout and records
 * {@link Checkout#CURRENT}.
 *
 * @since 0.1
 */
@FunctionalInterface
public interface ItemCheckout {

    /**
     * @param commit a commit of the project's repository
     * @return {@code true} if the repository holds it
     */
    boolean present(String commit);

    /** @return a checkout that never holds a commit, so every item runs in the checkout */
    static ItemCheckout none() {
        return commit -> false;
    }
}
