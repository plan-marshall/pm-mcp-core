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

/**
 * One line range of a file, both ends inclusive and one-based.
 *
 * @param first the first line
 * @param last  the last line
 * @since 0.1
 */
public record LineRange(int first, int last) {

    /**
     * @throws IllegalArgumentException if the range is empty or inverted
     */
    public LineRange {
        if (first < 1 || last < first) {
            throw new IllegalArgumentException("not a line range: %d to %d".formatted(first, last));
        }
    }
}
