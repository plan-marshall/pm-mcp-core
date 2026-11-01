/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */
package de.planmarshall.core.testing.generators;

import de.cuioss.test.generator.Generators;
import de.cuioss.test.generator.TypedGenerator;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.planmarshall.core.store.ProjectEnrolment;

/**
 * Generates a minimal but valid {@link ProjectEnrolment}: the empty wrappers, host tools, provider overrides, scripts,
 * language servers, budgets, write paths and build environment, and the compiled build defaults. The project id, the
 * project root, the enrolment instant and the pilot flag vary; every other component is empty, because the enrolment
 * schema admits an empty value there.
 *
 * @since 0.1
 */
public final class ProjectEnrolmentGenerator implements TypedGenerator<ProjectEnrolment> {

    @Override
    public ProjectEnrolment next() {
        var suffix = Generators.longs(100, Long.MAX_VALUE).next();
        return new ProjectEnrolment("project-" + suffix, "/tmp/generated-project-" + suffix, instant(),
                Generators.booleans().next(), null, null, List.of(), List.of(), ProjectEnrolment.Build.defaults(),
                Map.of(), new TreeMap<>(), new TreeMap<>(), new TreeMap<>(), List.of(), new TreeMap<>());
    }

    @Override
    public Class<ProjectEnrolment> getType() {
        return ProjectEnrolment.class;
    }

    private static Instant instant() {
        return Instant.ofEpochMilli(Generators.integers(0, Integer.MAX_VALUE).next().longValue());
    }
}
