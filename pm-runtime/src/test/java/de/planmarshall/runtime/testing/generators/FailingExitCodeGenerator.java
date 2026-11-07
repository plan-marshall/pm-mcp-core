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
 * Generates the exit code of a failed process: a value from 1 to 255, the range a POSIX process reports a failure in.
 *
 * @since 0.1
 */
public final class FailingExitCodeGenerator implements TypedGenerator<Integer> {

    /** The least exit code of a failure. */
    static final int LEAST = 1;

    /** The greatest exit code a POSIX process reports. */
    static final int GREATEST = 255;

    @Override
    public Integer next() {
        return Generators.integers(LEAST, GREATEST).next();
    }

    @Override
    public Class<Integer> getType() {
        return Integer.class;
    }
}
