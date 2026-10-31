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

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import de.planmarshall.workflow.ast.SourceLocation;

/**
 * Generates a valid {@link SourceLocation}: a generated resource name and the line and column of the node.
 *
 * @since 0.1
 */
public final class SourceLocationGenerator implements TypedGenerator<SourceLocation> {

    @Override
    public SourceLocation next() {
        return new SourceLocation("unit-" + Generators.integers(1, 999).next() + ".md",
                Generators.integers(1, 500).next(), Generators.integers(1, 80).next());
    }

    @Override
    public Class<SourceLocation> getType() {
        return SourceLocation.class;
    }
}
