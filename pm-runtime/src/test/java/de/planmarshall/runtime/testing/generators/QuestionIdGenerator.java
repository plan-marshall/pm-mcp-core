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
 * Generates a question id in its written form: {@code Q-} and three to six decimal digits, never a leading zero, so it
 * matches the question-id grammar and can never collide with the fixed {@code Q-001}…{@code Q-003} ids a test keeps.
 *
 * @since 0.1
 */
public final class QuestionIdGenerator implements TypedGenerator<String> {

    @Override
    public String next() {
        return "Q-" + Generators.integers(100, 999_999).next();
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
