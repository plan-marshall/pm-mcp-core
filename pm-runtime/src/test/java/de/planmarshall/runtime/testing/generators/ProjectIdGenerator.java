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

import de.cuioss.test.generator.TypedGenerator;

import de.planmarshall.core.testing.generators.CoreGenerators;

/**
 * Generates a project id through the generator artifact of {@code pm-core}, so a test of this module never spells the
 * project-id expression itself and never draws a value shorter than the identifier grammar admits.
 *
 * @since 0.1
 */
public final class ProjectIdGenerator implements TypedGenerator<String> {

    @Override
    public String next() {
        return CoreGenerators.projectEnrolments().next().projectId();
    }

    @Override
    public Class<String> getType() {
        return String.class;
    }
}
