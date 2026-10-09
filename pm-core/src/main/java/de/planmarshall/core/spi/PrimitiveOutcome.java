/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.spi;

/**
 * One outcome of a primitive (PM-IMPL-5). The outcomes of a primitive are the constants of one enum that implements
 * this interface, so the set is closed: a workflow routes every constant, and nothing but a constant can be returned.
 *
 * @since 0.1
 */
public interface PrimitiveOutcome {

    /**
     * @return the name a workflow routes this outcome by, for example {@code nothing_to_commit}; unique within the
     *         outcome enum of its primitive
     */
    String wireName();

    /**
     * @return the class of this outcome. The class, never the wire name, decides which step record is written for
     *         the outcome.
     */
    OutcomeClass outcomeClass();

    /**
     * @return {@code true} for an outcome with which a primitive reports the job it started. Only a primitive whose
     *         {@link Primitive#enqueuesJob()} is {@code true} declares such an outcome, and it declares at least one.
     */
    default boolean startsJob() {
        return false;
    }
}
