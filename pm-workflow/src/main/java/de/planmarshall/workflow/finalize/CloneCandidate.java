/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.finalize;

import java.util.Objects;

/**
 * One run of the plan's added lines that repeats a run of the tree the plan started from (Phase Workflows § Finalize
 * Step Ordering Bands, the clone candidates of {@code finalize.simplify}).
 * <p>
 * A candidate is evidence for the restraint ladder's "is it already in the codebase?", never a finding by itself: the
 * simplify task proposes to use the existing facility, or states in its submission why the repetition stays.
 *
 * @param addedPath     the file the added lines belong to
 * @param addedRange    the added lines
 * @param existingPath  the file of the tree the run repeats
 * @param existingRange the lines of the tree the run repeats
 * @param tokens        the length of the run in normalized tokens, at least 50
 * @since 0.1
 */
public record CloneCandidate(String addedPath, LineRange addedRange, String existingPath, LineRange existingRange,
int tokens) {

    /** The shortest run that is a candidate. */
    public static final int MIN_TOKENS = 50;

    /** The most candidates one run names. */
    public static final int MAX_CANDIDATES = 20;

    /**
     * @throws IllegalArgumentException if the run is shorter than {@value #MIN_TOKENS} tokens
     * @throws NullPointerException     if a value is {@code null}
     */
    public CloneCandidate {
        Objects.requireNonNull(addedPath, "addedPath");
        Objects.requireNonNull(addedRange, "addedRange");
        Objects.requireNonNull(existingPath, "existingPath");
        Objects.requireNonNull(existingRange, "existingRange");
        if (tokens < MIN_TOKENS) {
            throw new IllegalArgumentException(
                    "a clone candidate runs at least %d tokens: %d".formatted(MIN_TOKENS, tokens));
        }
    }
}
