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

/**
 * Generates a plan id in the form a launch and its prompt carry it: a number from 1 to 999, a hyphen and a lower-case
 * word of two to ten letters. The id names no real plan; a test uses it where the code under test passes the id on.
 *
 * @since 0.1
 */
public final class PlanIdGenerator implements TypedGenerator<String> {

    private final TypedGenerator<String> words = new WordGenerator(2, 10);

    @Override
    public String next() {
        return Generators.integers(1, 999).next() + "-" + words.next();
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
