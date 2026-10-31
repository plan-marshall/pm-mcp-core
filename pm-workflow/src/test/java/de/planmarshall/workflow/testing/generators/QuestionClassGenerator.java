/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.workflow.testing.generators;

import java.util.Set;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.planmarshall.workflow.verify.QuestionClass;

/**
 * Generates a valid {@link QuestionClass}: a generated question-class name and a generated, non-empty set of
 * resolution outcomes.
 *
 * @since 0.1
 */
public final class QuestionClassGenerator implements TypedGenerator<QuestionClass> {

    @Override
    public QuestionClass next() {
        var suffix = Generators.longs(1, Long.MAX_VALUE).next();
        return new QuestionClass("question-class-" + suffix,
                Set.of("answered-" + suffix, "declined-" + suffix));
    }

    @Override
    public Class<QuestionClass> getType() {
        return QuestionClass.class;
    }
}
