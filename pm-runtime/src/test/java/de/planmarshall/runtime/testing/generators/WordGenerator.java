/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.runtime.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.util.Locale;

/**
 * Generates a lower-case word of ASCII letters within a length range: a name segment, a path element, a module or
 * argument name. The words carry no meaning, so a test uses them wherever the code under test only passes a name on,
 * compares it or places it into a path.
 *
 * @since 0.1
 */
public final class WordGenerator implements TypedGenerator<String> {

    private final int minimumLength;
    private final int maximumLength;

    /**
     * @param minimumLength the least number of letters, at least 1
     * @param maximumLength the greatest number of letters, at least {@code minimumLength}
     */
    public WordGenerator(int minimumLength, int maximumLength) {
        if (minimumLength < 1 || maximumLength < minimumLength) {
            throw new IllegalArgumentException(
                    "invalid word length range " + minimumLength + ".." + maximumLength);
        }
        this.minimumLength = minimumLength;
        this.maximumLength = maximumLength;
    }

    @Override
    public String next() {
        return Generators.letterStrings(minimumLength, maximumLength).next().toLowerCase(Locale.ROOT);
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
