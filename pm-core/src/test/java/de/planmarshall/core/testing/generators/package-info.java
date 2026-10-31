/*
 * Copyright © 2026-present Oliver Wolff
 *
 * SPDX-License-Identifier: LicenseRef-Proprietary
 *
 * All rights reserved. This file is part of plan-marshall-mcp, which is proprietary software.
 * No right to use, copy, modify or distribute this file is granted; see the LICENSE.md file at
 * the root of this repository.
 */

/**
 * The typed generators of the {@code pm-core} test artifact (PLAN-V2-107): one
 * {@link de.cuioss.test.generator.TypedGenerator TypedGenerator} per domain type that the modules depending on the
 * foundation build by hand, and the {@link de.planmarshall.core.testing.generators.CoreGenerators} factory that
 * registers them in one place.
 * <p>
 * The package lives in the test source tree, so it is published with the {@code tests} classifier only: production
 * code never sees it, and a consumer adds the artifact as a test-scope {@code test-jar} dependency. A module that
 * needs a generator for a type of the foundation takes it from here instead of writing its own, and a generator for a
 * type of this module goes here rather than into a test class.
 *
 * @since 0.1
 */
@NullMarked
package de.planmarshall.core.testing.generators;

import org.jspecify.annotations.NullMarked;
